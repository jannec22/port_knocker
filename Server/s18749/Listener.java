package s18749;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class Listener implements Runnable {

    private DatagramSocket socket;
    private int _port;

    Listener(int port) throws SocketException {
        socket = new DatagramSocket(port);
        _port = port;
    }

    Listener() throws SocketException {
        socket = new DatagramSocket();
        _port = socket.getLocalPort();
    }

    @Override
    public void run() {
        System.out.println("INFO: listening on port: " + _port);
        byte[] extracted = new byte[65535];
        DatagramPacket packet = new DatagramPacket(extracted, extracted.length);

        try {
            socket.setSoTimeout(5000);
        } catch (SocketException e) {
            System.out.println("unable to set receive timeout, aborting");
            System.exit(-2);
        }

        while (true) {
            try {
                socket.receive(packet);
                Server.onInteraction(packet.getAddress().toString() + ":" + packet.getPort(), _port);

            } catch (SocketTimeoutException e) {
                if(packet != null && packet.getAddress() != null) {
                    Server.abortClient(packet.getAddress().toString() + ":" + packet.getPort());
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}