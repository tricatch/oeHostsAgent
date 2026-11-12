package tricatch.oehosts.agent;

import java.net.InetAddress;

/**
 * The application the agent tests run in a child JVM. {@code host...}: prints {@code host=address}
 * (or {@code host=ERR}) per host. {@code --wait host address seconds}: resolves repeatedly until the
 * host has that address, printing {@code changed} or {@code timeout}.
 */
public final class ResolveMain {

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("--wait")) {
            long end = System.currentTimeMillis() + Long.parseLong(args[3]) * 1000;
            while (System.currentTimeMillis() < end) {
                if (args[2].equals(resolve(args[1]))) {
                    System.out.println("changed");
                    return;
                }
                Thread.sleep(200);
            }
            System.out.println("timeout");
            return;
        }
        for (String h : args) System.out.println(h + "=" + resolve(h));
    }

    private static String resolve(String host) {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            return "ERR";
        }
    }
}
