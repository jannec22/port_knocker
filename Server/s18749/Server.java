package s18749;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;

public class Server {
    private static ArrayList<Integer> unlockSequence;
    private static ArrayList<Integer> ports;
    private static HashMap<String, Integer> knockers = new HashMap<>();

    private static void printHelp() {
        System.out.println( //
                "Port Knocking Server:\n\nUsage:\n" //
                        + "   -p --ports <1024+> <1024+> ...    listen on given ports, unlock sequence: ports sorted in given order\n\n" //
        );
    }

    public static synchronized void onInteraction(String origin, int port) {
        // System.out.println("client interaction detected, ip: " + ip + ", port: " +
        // port);

        if (validateSequence(origin, port)) {
            try {
                (new Thread(new Client(origin))).start();

            } catch (SocketException e) {
                System.out.println("ERR: could not connect with the client");
            }
        }
    }

    public static synchronized void abortClient(String origin) {

        if (knockers.get(origin) != null) {
            System.out.println("aborting authentication for: " + origin);
            knockers.remove(origin);
        }
    }

    private static synchronized boolean validateSequence(String origin, int targetPort) {
        Integer step = knockers.get(origin);
        System.out.println(origin + " -> " + targetPort + " " + (step != null ? step : 0));

        if (step != null) {
            if (step + 1 >= unlockSequence.size()) {
                knockers.remove(origin);
                return true;
            }

            if (unlockSequence.get(step) == targetPort) {
                knockers.put(origin, step + 1);
            } else {
                knockers.remove(origin);
                System.out.println(origin + " authentication failed");
            }
        } else if (unlockSequence.get(0) == targetPort) {
            knockers.put(origin, 1);
        } else {
            System.out.println(origin + " authentication failed");
        }

        return false;
    }

    public static void spawnListeners() {
        for (Integer port : ports) {
            try {
                Listener listener = new Listener(port);
                (new Thread(listener)).start();

            } catch (SocketException e) {
                System.out.println("ERR: cannot listen on port: " + port);
                System.exit(-1);
            }
        }
    }

    public static void main(String[] args) {
        unlockSequence = new ArrayList<>(args.length);
        ports = new ArrayList<>(args.length);
        boolean readingPorts = false;

        try {
            for (int i = 0; i < args.length; ++i) {
                if (readingPorts) {
                    int port = Integer.parseInt(args[i]);

                    if (port < 1024) {
                        throw new Exception("ERR: " + args[i] + " is not a valid port number");
                    }

                    unlockSequence.add(port);

                    if (!ports.contains(port)) {
                        ports.add(port);
                    }
                }

                if (args[i].equals("-p") || args[i].equals("--ports")) {
                    readingPorts = true;
                }
            }

            if (ports.size() > 0) {
                spawnListeners();
                System.out.println("unlock sequence is: " + unlockSequence.toString());
            } else {
                throw new Exception("ERR: you have to specify at least one port");
            }

        } catch (NumberFormatException e) {
            printHelp();
            System.out.println("ERR: given port list is not valid");
        } catch (Exception e) {
            printHelp();
            System.out.println(e.getMessage());
        }
    }
}
