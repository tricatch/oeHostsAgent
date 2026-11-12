package tricatch.oehosts.agent;

import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostsTableTest {

    private static String ip(HostsTable t, String host) {
        InetAddress a = t.lookup(host);
        return a == null ? null : a.getHostAddress();
    }

    @Test
    void parsesHostsLines() {
        HostsTable t = HostsTable.parse("""
                # comment
                10.0.0.1   api.example.test  www.example.test   # trailing comment
                10.0.0.2\tapi.example.test
                ﻿10.0.0.3 Upper.Example.TEST.
                999.0.0.1 bad-ip.test
                not-an-ip  x.test
                10.0.0.4
                ::1 v6.example.test
                [fe80::1] v6b.example.test
                10.0.0.5 bad/name.test ok.example.test
                """);
        assertEquals("10.0.0.1", ip(t, "api.example.test"), "the first line wins");
        assertEquals("10.0.0.1", ip(t, "www.example.test"), "every name on a line");
        assertEquals("10.0.0.3", ip(t, "upper.example.test"), "case and a trailing dot are ignored");
        assertEquals("10.0.0.3", ip(t, "UPPER.example.test."));
        assertNull(ip(t, "bad-ip.test"));
        assertNull(ip(t, "x.test"));
        assertNull(ip(t, "bad/name.test"));
        assertEquals("10.0.0.5", ip(t, "ok.example.test"));
        assertInstanceOf(Inet6Address.class, t.lookup("v6.example.test"));
        assertInstanceOf(Inet6Address.class, t.lookup("v6b.example.test"));
        assertEquals("api.example.test", t.lookup("api.example.test").getHostName(), "carries the name");
        assertNull(ip(t, "unknown.test"));
    }

    @Test
    void wildcards() {
        HostsTable t = HostsTable.parse("""
                10.0.0.1 *.example.test
                10.0.0.2 *.deep.example.test
                10.0.0.3 exact.deep.example.test
                """);
        assertNull(ip(t, "example.test"), "a wildcard does not match its base name");
        assertEquals("10.0.0.1", ip(t, "a.example.test"));
        assertEquals("10.0.0.1", ip(t, "a.b.example.test"));
        assertEquals("10.0.0.2", ip(t, "x.deep.example.test"), "the longer wildcard wins");
        assertEquals("10.0.0.3", ip(t, "exact.deep.example.test"), "an exact name beats a wildcard");
        assertEquals("x.deep.example.test", t.lookup("x.deep.example.test").getHostName());
    }

    @Test
    void earlierTextsWinAndFamiliesFilter() {
        HostsTable t = HostsTable.parse(List.of("10.0.0.1 a.test\n", "10.0.0.2 a.test\n::2 b.test\n"));
        assertEquals("10.0.0.1", ip(t, "a.test"));
        assertEquals(1, t.lookup("a.test", true, false).size());
        assertTrue(t.lookup("a.test", false, true).isEmpty(), "mapped, but not to IPv6");
        assertNull(t.lookup("c.test", true, true));
        assertEquals(2, t.size());
    }

    @Test
    void literals() {
        assertArrayEquals(new byte[]{10, 0, 0, 1}, HostsTable.literal("10.0.0.1"));
        assertNull(HostsTable.literal("256.0.0.1"));
        assertNull(HostsTable.literal("10.0.0"));
        assertNull(HostsTable.literal("example.test"));
        assertNull(HostsTable.literal("::zz"));
        assertEquals(16, HostsTable.literal("::1").length);
    }

    @Test
    void options() {
        assertEquals(2, HostsAgent.parseUrls(" http://127.0.0.1:1/hosts/a , https://x/hosts/b ,, ").size());
        assertTrue(HostsAgent.parseUrls("file:///etc/hosts").isEmpty());
        assertEquals(60, HostsAgent.refreshSeconds(null));
        assertEquals(0, HostsAgent.refreshSeconds("0"));
        assertEquals(60, HostsAgent.refreshSeconds("soon"));
        assertEquals("http://127.0.0.1:36981/hosts/.../dev_api",
                HostsAgent.label("http://127.0.0.1:36981/hosts/bpw1mr5rd8vhg0-at4w49v2/dev_api"));
        assertEquals(8, Agent.feature("1.8"));
        assertEquals(17, Agent.feature("17"));
        assertEquals(25, Agent.feature("25"));
        assertEquals(0, Agent.feature(null));
    }
}
