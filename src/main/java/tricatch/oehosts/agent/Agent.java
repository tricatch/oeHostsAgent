package tricatch.oehosts.agent;

import java.lang.instrument.Instrumentation;

/**
 * The Premain-Class. Hooks the mapping into the JDK's name lookup the way the running version allows,
 * then starts the loader ({@link HostsAgent}):
 * <ul>
 *   <li>JDK 18+: nothing to install; the JDK finds {@code OeHostsResolverProvider} through
 *       {@code META-INF/services} (JEP 418).</li>
 *   <li>JDK 9-17: {@link LegacyResolver#installJdk9To17} wraps {@code InetAddress.nameService}.</li>
 *   <li>JDK 8: {@link LegacyResolver#installJdk8} puts a name service first in {@code InetAddress.nameServices}.</li>
 * </ul>
 * Everything here runs on Java 8. It never throws: a javaagent that throws from premain stops the JVM.
 */
public final class Agent {

    private Agent() {}

    public static void premain(String args, Instrumentation inst) {
        try {
            int v = feature(System.getProperty("java.specification.version"));
            try {
                if (v == 8) LegacyResolver.installJdk8();
                else if (v >= 9 && v < 18) LegacyResolver.installJdk9To17(inst);
            } catch (Throwable t) {
                HostsAgent.log("could not hook into name lookup on Java " + v + "; hosts mapping is off: " + rootCause(t));
                return;
            }
            HostsAgent.start(args);
        } catch (Throwable t) {
            HostsAgent.log("failed to start; hosts mapping is off: " + rootCause(t));
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c;
    }

    /** {@code "1.8"} -> 8, {@code "17"} -> 17; 0 when it cannot be read. */
    static int feature(String spec) {
        if (spec == null) return 0;
        String s = spec.startsWith("1.") ? spec.substring(2) : spec;
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        try {
            return end == 0 ? 0 : Integer.parseInt(s.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
