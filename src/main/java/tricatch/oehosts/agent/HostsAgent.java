package tricatch.oehosts.agent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads the hosts mapping from oeHosts share URLs and keeps it fresh. Started from
 * {@link Agent#premain} once the lookup hook is in place:
 * <pre>
 * java -javaagent:oehosts-agent-&lt;version&gt;.jar=http://127.0.0.1:36981/hosts/&lt;id&gt;-&lt;sig&gt;/&lt;name&gt; -jar app.jar
 * </pre>
 * Several URLs are separated by {@code ,}; earlier ones win for a host listed in more than one.
 * Options are system properties:
 * <ul>
 *   <li>{@code -Doehosts.agent.url=...} - the URLs, when the agent argument is empty</li>
 *   <li>{@code -Doehosts.agent.refresh=60} - seconds between reloads (0: load once)</li>
 *   <li>{@code -Doehosts.agent.debug=true} - log every mapped lookup</li>
 * </ul>
 * The first load happens before {@code main} so the application's first lookups are mapped. When a
 * load fails the previous mapping stays in use. Java 8.
 */
public final class HostsAgent {

    static final String PREFIX = "[oehosts-agent] ";
    private static final int TIMEOUT_MS = 3000;
    private static final int MAX_BYTES = 4 << 20;

    private static volatile HostsTable table = HostsTable.EMPTY;
    private static volatile boolean debug;
    private static List<URI> urls = Collections.emptyList();
    private static boolean failing;

    private HostsAgent() {}

    /** The mapping in use (empty until the first successful load). */
    static HostsTable table() {
        return table;
    }

    /** The jar's Implementation-Version, or "dev" when run from classes. */
    static String version() {
        String v = HostsAgent.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    static void start(String args) {
        debug = Boolean.getBoolean("oehosts.agent.debug");
        log("oeHosts-Agent " + version() + " (Java " + Agent.feature(System.getProperty("java.specification.version")) + ")");
        String spec = args != null && !args.trim().isEmpty() ? args : System.getProperty("oehosts.agent.url", "");
        urls = parseUrls(spec);
        if (urls.isEmpty()) {
            log("no share URL given (-javaagent:oehosts-agent-<version>.jar=<url>); hosts mapping is off.");
            return;
        }
        load();
        final long refresh = refreshSeconds(System.getProperty("oehosts.agent.refresh"));
        if (refresh <= 0) return;
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(refresh * 1000);
                } catch (InterruptedException e) {
                    return;
                }
                load();
            }
        }, "oehosts-agent-refresh");
        t.setDaemon(true);
        t.start();
    }

    static List<URI> parseUrls(String spec) {
        List<URI> out = new ArrayList<>();
        for (String s : spec.split(",")) {
            String u = s.trim();
            if (u.isEmpty()) continue;
            try {
                URI uri = new URI(u);
                if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                    out.add(uri);
                } else {
                    log("ignored (not http/https): " + label(u));
                }
            } catch (Exception e) {
                log("ignored (not a URL): " + label(u));
            }
        }
        return out;
    }

    static long refreshSeconds(String v) {
        if (v == null || v.trim().isEmpty()) return 60;
        try {
            return Math.max(0, Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    /** Fetches every URL; installs the new mapping only when all of them answered. */
    static synchronized void load() {
        List<String> texts = new ArrayList<>();
        for (URI u : urls) {
            try {
                texts.add(fetch(u));
            } catch (IOException e) {
                if (!failing) log("could not load " + label(u.toString()) + " (" + e.getMessage()
                        + "); keeping the " + (table.size() == 0 ? "empty" : "previous") + " mapping.");
                failing = true;
                return;
            }
        }
        HostsTable next = HostsTable.parse(texts);
        boolean first = table == HostsTable.EMPTY;
        if (first || failing || next.size() != table.size()) {
            log((failing && !first ? "reloaded: " : "loaded: ") + next.size() + " host(s) from " + urls.size() + " URL(s)");
        }
        failing = false;
        table = next;
    }

    private static String fetch(URI uri) throws IOException {
        URL url = uri.toURL();
        // Straight to oeHubApp: a configured HTTP proxy would not know 127.0.0.1 as this PC.
        HttpURLConnection c = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setUseCaches(false);
        c.setInstanceFollowRedirects(false);
        try {
            int status = c.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) {
                    b.write(buf, 0, n);
                    if (b.size() > MAX_BYTES) throw new IOException("response too large");
                }
                return new String(b.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    /** A URL for the log without its token: the share URL's token is what grants access. */
    static String label(String url) {
        int h = url.indexOf("/hosts/");
        if (h < 0) return url;
        String rest = url.substring(h + 7);
        int slash = rest.indexOf('/');
        return url.substring(0, h + 7) + "..." + (slash >= 0 ? rest.substring(slash) : "");
    }

    static void debug(String fmt, Object... args) {
        if (debug) log(String.format(fmt, args));
    }

    static void log(String msg) {
        System.err.println(PREFIX + msg);
    }
}
