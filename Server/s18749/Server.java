package s18749;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class Server {
    private static ArrayList<Integer> unlockSequence;
    private static ArrayList<Integer> ports;
    private static HashMap<String, Integer> knockers = new HashMap<>();
    private static String _filename;
    private static final String DEFAULT_FILE_NAME = "24Mfile";

    private static void printHelp() {
        System.out.println( //
                "Port Knocking Server:\n\nUsage:\n" //
                        + "   -p --ports <1024+> <1024+> ...    listen on given ports, unlock sequence: ports sorted in given order\n" //
                        + "   -f --file <file name>    file to serve\n\n" //
                        + "Or:\n" //
                        + "   <1024+> <1024+> ...   listen on given ports wit the given order and serve default file\n\n" //
        );
    }

    public static synchronized void onInteraction(String origin, int port) {
        // System.out.println("client interaction detected, ip: " + ip + ", port: " +
        // port);

        if (validateSequence(origin, port)) {
            try {
                (new Thread(new Client(origin, _filename))).start();

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
        ports = new ArrayList<>(args.length - 1);
        boolean readingPorts = false;

        try {

            if (!Collections.disjoint(Arrays.asList(args),
                    Arrays.asList(new String[] { "-p", "-f", "--ports", "--file" }))) {
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

                    if (args[i].equals("-f") || args[i].equals("--file")) {
                        if (args.length >= i + 1) {
                            _filename = args[i + 1];
                        } else {
                            throw new Exception("ERR: not enaugh arguments");
                        }
                    }

                    if (args[i].equals("-p") || args[i].equals("--ports")) {
                        readingPorts = true;
                    }
                }

                if (_filename == null || _filename.isEmpty())
                    throw new Exception("ERR: you need to specify the file name");
            } else {
                _filename = DEFAULT_FILE_NAME;

                for (String port : args) {
                    ports.add(Integer.parseInt(port));
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
