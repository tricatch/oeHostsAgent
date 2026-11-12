package tricatch.oehosts.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs {@code ResolveMain} (src/testApp) in a child JVM with {@code -javaagent:<built jar>}, against
 * a local share server, on each supported JDK: 8 (nameServices hook), 11 and 17 (nameService hook)
 * and the build JDK (resolver SPI). 0 stands for the build JDK.
 */
class AgentTest {

    private HttpServer server;
    private final AtomicReference<String> hosts = new AtomicReference<>("");
    private String url;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/hosts/", ex -> {
            byte[] b = hosts.get().getBytes(StandardCharsets.UTF_8);
            int status = ex.getRequestURI().getPath().contains("missing") ? 404 : 200;
            ex.sendResponseHeaders(status, b.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(b);
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hosts/abc-def/dev";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static String java(int jdk) {
        if (jdk == 0) return ProcessHandle.current().info().command().orElse("java");
        String exe = System.getProperty("oehosts.test.java." + jdk);
        assertNotNull(exe, "no JDK " + jdk + " toolchain");
        return exe;
    }

    private static List<String> run(int jdk, String agentArg, List<String> jvmOpts, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(java(jdk));
        cmd.add("-javaagent:" + System.getProperty("oehosts.test.agentJar") + (agentArg == null ? "" : "=" + agentArg));
        cmd.addAll(jvmOpts);
        // Only the test application: the agent must come from the jar alone, as in real use.
        cmd.add("-cp");
        cmd.add(System.getProperty("oehosts.test.appClasses"));
        cmd.add("tricatch.oehosts.agent.ResolveMain");
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "child JVM did not finish");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), out);
        return out.lines().toList();
    }

    @ParameterizedTest(name = "JDK {0}")
    @ValueSource(ints = {8, 11, 17, 0})
    void mapsListedHostsAndLeavesOthersToTheSystem(int jdk) throws Exception {
        hosts.set("10.1.2.3 api.example.test\n10.9.9.9 *.wild.example.test\n");
        List<String> out = run(jdk, url, List.of(), "api.example.test", "a.wild.example.test", "localhost", "nothing.invalid");
        assertTrue(out.contains("api.example.test=10.1.2.3"), out.toString());
        assertTrue(out.contains("a.wild.example.test=10.9.9.9"), out.toString());
        assertTrue(out.stream().anyMatch(l -> l.startsWith("localhost=") && !l.endsWith("ERR")), out.toString());
        assertTrue(out.contains("nothing.invalid=ERR"), out.toString());
        assertTrue(out.stream().anyMatch(l -> l.contains("loaded: 2 host(s)")), out.toString());
        assertTrue(out.stream().anyMatch(l -> l.matches("\\[oehosts-agent] oeHosts-Agent \\d+\\.\\d+\\.\\d+ \\(Java \\d+\\)")),
                "the version from the jar manifest: " + out);
        if (jdk != 0) assertTrue(out.stream().anyMatch(l -> l.endsWith("(Java " + jdk + ")")), out.toString());
        assertFalse(out.stream().anyMatch(l -> l.contains("abc-def")), "the token is not logged: " + out);
        assertFalse(out.stream().anyMatch(l -> l.contains("could not hook") || l.contains("failed to start")), out.toString());
    }

    @ParameterizedTest(name = "JDK {0}")
    @ValueSource(ints = {8, 11, 17, 0})
    void picksUpChangesOnRefresh(int jdk) throws Exception {
        hosts.set("10.1.2.3 api.example.test\n");
        Thread change = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            hosts.set("10.4.5.6 api.example.test\n");
        });
        change.start();
        // The JDK's own cache is turned off, so the next refresh shows.
        List<String> out = run(jdk, url, List.of("-Doehosts.agent.refresh=1", "-Dsun.net.inetaddr.ttl=0"),
                "--wait", "api.example.test", "10.4.5.6", "15");
        change.join();
        assertTrue(out.contains("changed"), out.toString());
    }

    @ParameterizedTest(name = "JDK {0}")
    @ValueSource(ints = {8, 11, 17, 0})
    void aFailingUrlOrNoUrlLeavesTheAppRunning(int jdk) throws Exception {
        List<String> out = run(jdk, url.replace("abc-def", "missing"), List.of(), "api.example.test", "localhost");
        assertTrue(out.stream().anyMatch(l -> l.contains("could not load") && l.contains("HTTP 404")), out.toString());
        assertTrue(out.stream().anyMatch(l -> l.startsWith("localhost=") && !l.endsWith("ERR")), out.toString());

        out = run(jdk, null, List.of(), "localhost");
        assertTrue(out.stream().anyMatch(l -> l.contains("no share URL given")), out.toString());

        // The URL may also come from a system property.
        hosts.set("10.1.2.3 api.example.test\n");
        out = run(jdk, null, List.of("-Doehosts.agent.url=" + url, "-Doehosts.agent.debug=true"), "api.example.test");
        assertTrue(out.contains("api.example.test=10.1.2.3"), out.toString());
        assertTrue(out.stream().anyMatch(l -> l.contains("api.example.test -> 10.1.2.3")), out.toString());
    }
}
