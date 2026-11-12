package tricatch.oehosts.agent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.List;
import java.util.stream.Stream;

/**
 * The JDK 18 resolver SPI (JEP 418) entry, registered in
 * {@code META-INF/services/java.net.spi.InetAddressResolverProvider}. The JDK loads it with the
 * system class loader on the first name lookup; {@code -javaagent} puts the agent jar on that class
 * path, so no code has to install it. A host in {@link HostsAgent#table()} gets its mapped address;
 * every other lookup, and every reverse lookup, goes to the JDK's built-in resolver.
 */
public final class OeHostsResolverProvider extends InetAddressResolverProvider {

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                int c = policy.characteristics();
                boolean v4 = (c & LookupPolicy.IPV4) != 0;
                boolean v6 = (c & LookupPolicy.IPV6) != 0;
                List<InetAddress> mapped = HostsAgent.table().lookup(host, v4, v6);
                if (mapped == null) return builtin.lookupByName(host, policy);
                HostsAgent.debug("%s -> %s", host, mapped.isEmpty() ? "(no address of the requested family)" : mapped.get(0).getHostAddress());
                // Mapped, but to the other address family: the host is not reachable this way. Not
                // falling back to DNS keeps the mapping authoritative, as a hosts file is.
                if (mapped.isEmpty()) throw new UnknownHostException(host);
                return mapped.stream();
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                return builtin.lookupByAddress(addr);
            }
        };
    }

    @Override
    public String name() {
        return "oeHosts-Agent";
    }
}
