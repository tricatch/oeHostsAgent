package tricatch.oehosts.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Name lookup hooks for JDKs without the resolver SPI (before 18). Both rely on JDK internals, which
 * no longer change for these released versions; the agent is tested on 8, 11 and 17.
 * <ul>
 *   <li>JDK 8: {@code InetAddress.nameServices} is a list of {@code sun.net.spi.nameservice.NameService}
 *       tried in order (the next one when a service throws {@link UnknownHostException}); a proxy is
 *       put first that answers the mapped hosts and throws for the rest.</li>
 *   <li>JDK 9-17: {@code InetAddress.nameService} holds one {@code InetAddress.NameService} (a
 *       package-private interface); a proxy wraps it, answering the mapped hosts and passing every
 *       other call to the original. {@code java.net} is opened to the agent with
 *       {@link Instrumentation#redefineModule}, so no {@code --add-opens} is needed.</li>
 * </ul>
 * Compiled for Java 8, so the module API is called by reflection.
 */
final class LegacyResolver {

    private LegacyResolver() {}

    static void installJdk8() throws Exception {
        Class<?> ns = Class.forName("sun.net.spi.nameservice.NameService");
        Field f = InetAddress.class.getDeclaredField("nameServices");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> services = (List<Object>) f.get(null);
        Object proxy = Proxy.newProxyInstance(LegacyResolver.class.getClassLoader(), new Class<?>[]{ns}, handler(null));
        synchronized (services) {
            services.add(0, proxy);
        }
    }

    static void installJdk9To17(Instrumentation inst) throws Exception {
        Method getModule = Class.class.getMethod("getModule");
        Object javaBase = getModule.invoke(InetAddress.class);
        Object agentModule = getModule.invoke(LegacyResolver.class);
        Class<?> moduleClass = Class.forName("java.lang.Module");
        Instrumentation.class.getMethod("redefineModule", moduleClass, Set.class, Map.class, Map.class, Set.class, Map.class)
                .invoke(inst, javaBase, Collections.emptySet(), Collections.emptyMap(),
                        Collections.singletonMap("java.net", Collections.singleton(agentModule)),
                        Collections.emptySet(), Collections.emptyMap());

        Class<?> ns = Class.forName("java.net.InetAddress$NameService");
        Field f = InetAddress.class.getDeclaredField("nameService");
        f.setAccessible(true);
        Object original = f.get(null);
        if (original == null) throw new IllegalStateException("InetAddress.nameService is not set");
        // A proxy of a non-public interface is defined in that interface's package and loader (here
        // java.net, the boot loader), hence the null loader.
        Object proxy = Proxy.newProxyInstance(null, new Class<?>[]{ns}, handler(original));
        setStatic(f, proxy);
    }

    /**
     * The proxy's behavior: a mapped host gets its address; anything else goes to {@code fallback}, or,
     * with no fallback (JDK 8, where the next name service in the list takes over), fails with
     * {@link UnknownHostException}.
     */
    private static InvocationHandler handler(Object fallback) {
        return (proxy, m, a) -> {
            switch (m.getName()) {
                case "lookupAllHostAddr": {
                    String host = (String) a[0];
                    InetAddress mapped = HostsAgent.table().lookup(host);
                    if (mapped != null) {
                        HostsAgent.debug("%s -> %s", host, mapped.getHostAddress());
                        return new InetAddress[]{mapped};
                    }
                    if (fallback == null) throw new UnknownHostException(host);
                    return call(m, fallback, a);
                }
                case "getHostByAddr":
                    if (fallback == null) throw new UnknownHostException();
                    return call(m, fallback, a);
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == a[0];
                case "toString":
                    return "oeHosts-Agent name service";
                default:
                    if (fallback == null) throw new UnsupportedOperationException(m.getName());
                    return call(m, fallback, a);
            }
        };
    }

    private static Object call(Method m, Object target, Object[] a) throws Throwable {
        try {
            m.setAccessible(true);
            return m.invoke(target, a);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Sets a static field, also a {@code static final} one (through Unsafe, which reflection cannot). */
    private static void setStatic(Field f, Object value) throws Exception {
        if (!Modifier.isFinal(f.getModifiers())) {
            f.set(null, value);
            return;
        }
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Object base = unsafeClass.getMethod("staticFieldBase", Field.class).invoke(unsafe, f);
        long offset = (Long) unsafeClass.getMethod("staticFieldOffset", Field.class).invoke(unsafe, f);
        unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class).invoke(unsafe, base, offset, value);
    }
}
