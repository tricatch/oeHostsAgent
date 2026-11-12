package tricatch.oehosts.agent;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Host name to address mapping parsed from hosts-file text: {@code IP name [name...]} per line,
 * {@code #} starts a comment. The first line that names a host wins. A name may be a wildcard
 * {@code *.example.com}, which matches every subdomain (not {@code example.com} itself), as in
 * Chrome's {@code --host-resolver-rules}; an exact name beats a wildcard, a longer wildcard beats a
 * shorter one. Immutable. Java 8.
 */
final class HostsTable {

    static final HostsTable EMPTY = new HostsTable(Collections.<String, InetAddress>emptyMap());

    private static final Pattern WS = Pattern.compile("[\\s\\uFEFF]+");
    private static final Pattern TRIM = Pattern.compile("^[\\s\\uFEFF]+|[\\s\\uFEFF]+$");
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");
    private static final Pattern NAME = Pattern.compile("(\\*\\.)?[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");

    /** Lower-case name (wildcards keep their {@code *.}) to address, in file order. */
    private final Map<String, InetAddress> map;

    private HostsTable(Map<String, InetAddress> map) {
        this.map = map;
    }

    /** Parses several hosts texts in order; earlier texts win, as earlier lines do. */
    static HostsTable parse(List<String> texts) {
        Map<String, InetAddress> m = new LinkedHashMap<>();
        for (String text : texts) {
            for (String line : text.split("\r?\n")) {
                int hash = line.indexOf('#');
                // trim() leaves a byte order mark (U+FEFF) in place; it must not hide the first IP.
                String l = TRIM.matcher(hash >= 0 ? line.substring(0, hash) : line).replaceAll("");
                if (l.isEmpty()) continue;
                String[] w = WS.split(l);
                if (w.length < 2) continue;
                byte[] addr = literal(w[0]);
                if (addr == null) continue;
                for (int k = 1; k < w.length; k++) {
                    String name = normalize(w[k]);
                    if (!NAME.matcher(name).matches() || m.containsKey(name)) continue;
                    try {
                        // Carries the host name, so getHostName() needs no reverse lookup.
                        m.put(name, InetAddress.getByAddress(name.startsWith("*.") ? null : name, addr));
                    } catch (UnknownHostException e) {
                        // not reached: addr is 4 or 16 bytes
                    }
                }
            }
        }
        return new HostsTable(Collections.unmodifiableMap(m));
    }

    static HostsTable parse(String text) {
        return parse(Collections.singletonList(text));
    }

    int size() {
        return map.size();
    }

    /**
     * The address mapped to {@code host}, or null when no line names it. A wildcard match is
     * returned with {@code host} as its name.
     */
    InetAddress lookup(String host) {
        if (host == null || map.isEmpty()) return null;
        String h = normalize(host);
        InetAddress a = map.get(h);
        if (a != null) return a;
        // *.a.b.c, then *.b.c, then *.c: the longest wildcard first.
        for (int dot = h.indexOf('.'); dot >= 0 && dot < h.length() - 1; dot = h.indexOf('.', dot + 1)) {
            InetAddress w = map.get("*" + h.substring(dot));
            if (w != null) {
                try {
                    return InetAddress.getByAddress(h, w.getAddress());
                } catch (UnknownHostException e) {
                    return w;
                }
            }
        }
        return null;
    }

    /** The mapped addresses of the families {@code ipv4}/{@code ipv6} allow (empty when none); null when unmapped. */
    List<InetAddress> lookup(String host, boolean ipv4, boolean ipv6) {
        InetAddress a = lookup(host);
        if (a == null) return null;
        List<InetAddress> out = new ArrayList<>(1);
        if ((a instanceof Inet4Address && ipv4) || (a instanceof Inet6Address && ipv6)) out.add(a);
        return out;
    }

    private static String normalize(String name) {
        String n = TRIM.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
        return n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
    }

    /** The bytes of an IPv4 or IPv6 literal, or null when {@code s} is not one. Never does a lookup. */
    static byte[] literal(String s) {
        String t = s.startsWith("[") && s.endsWith("]") ? s.substring(1, s.length() - 1) : s;
        if (IPV4.matcher(t).matches()) {
            String[] p = t.split("\\.");
            byte[] b = new byte[4];
            for (int k = 0; k < 4; k++) {
                int v = Integer.parseInt(p[k]);
                if (v > 255) return null;
                b[k] = (byte) v;
            }
            return b;
        }
        if (!IPV6.matcher(t).matches()) return null;
        try {
            // A string with ':' is parsed as an IPv6 literal (an invalid one throws), never looked up.
            return InetAddress.getByName(t).getAddress();
        } catch (UnknownHostException | RuntimeException e) {
            return null;
        }
    }
}
