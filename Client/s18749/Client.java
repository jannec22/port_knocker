package s18749;

import java.util.Arrays;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;

public class Client {
    private static String _targetHost = "";
    private static DatagramSocket socket;

    private static ArrayList<Integer> ports = new ArrayList<>();

    private static void printHelp() {
        System.out.println( //
                "Port Knocking Client:\n\nUsage:\n" //
                        + "   -t --target host    starts knocking on the target\n" //
                        + "   -s --sequence <port> <port> ...    specifies a knock sequence for authentification\n" //
                        + "Or:\n" //
                        + "   <target ip> <port> <port> ...    specifies target and the knock sequence for authentification\n\n" //
        );
    }

    private static void sendAuthSequence() {
        System.out.println("knocking on " + _targetHost + " -> " + ports);
        InetAddress address;

        try {
            address = InetAddress.getByName(_targetHost);
            byte[] message = "Knock Knock".getBytes();

            for (Integer port : ports) {
                try {
                    DatagramPacket packet = new DatagramPacket(message, message.length, address, port);

                    socket.send(packet);
                    System.out.println("knock [" + _targetHost + ":" + port + "]");

                    Thread.sleep(100);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            printHelp();
            System.out.println("ERR: not enaugh arguments");
            System.exit(-1);
        }

        boolean readingPorts = false;

        try {
            if (!Collections.disjoint(Arrays.asList(args),
                    Arrays.asList(new String[] { "-t", "-s", "--target", "--sequence" }))) {
                for (int i = 0; i < args.length; i++) {
                    if ((args[i].equals("-t") || args[i].equals("--target")) && args.length > i + 2) {
                        _targetHost = args[i + 1];

                    } else if (args[i].equals("-s") || args[i].equals("--sequence")) {
                        readingPorts = true;
                    } else if (readingPorts) {
                        ports.add(Integer.parseInt(args[i]));
                    }
                }
            } else {
                _targetHost = args[0];

                for (int i = 1; i < args.length; i++) {
                    ports.add(Integer.parseInt(args[i]));
                }
            }

            if (_targetHost.isEmpty()) {
                throw new Exception("ERR: given host is empty");
            }

            if (ports.size() < 1) {
                throw new Exception("ERR: you have to specify at least one port of unlock sequence");
            }

            socket = new DatagramSocket();
            sendAuthSequence();
            UdpFile file = new UdpFile(socket);

            if (file.load()) {
                file.save();
            }
            socket.close();

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            printHelp();
            System.out.println("ERR: some of given ports are not valid");
        } catch (Exception e) {
            printHelp();
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
