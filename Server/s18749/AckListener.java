package s18749;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class AckListener implements Runnable {
    private Acker _acker;
    private volatile boolean _acked = false;
    private byte[] extracted;
    private Thread _thread;

    AckListener(Acker acker) {
        _acker = acker;
        _thread = new Thread(this);
    }

    public void start() {
        _thread.start();
    }

    public void interrupt() {
        _acked = true;
        _thread.interrupt();
        _acker.getSocket().close();
    }

    public void join() throws InterruptedException {
        if (_thread != null) {
            _thread.join();
        }
    }

    @Override
    public void run() {
        System.out.println("spawning ack listener");
        try {
            _acker.getSocket().setSoTimeout(1000);

            while (!_acked) {
                extracted = new byte[4]; // int -> 4 bytes
                DatagramPacket rcv = new DatagramPacket(extracted, extracted.length);

                // System.out.println(_thread.getId() + " waiting for ack's");
                _acker.getSocket().receive(rcv);
                // System.out.println("got new ack");
                String dataString = new String(extracted, StandardCharsets.UTF_8);
                String[] data = dataString.split("::");

                if (data.length == 2) {
                    int chunk;

                    try {
                        chunk = Integer.parseInt(data[1]);

                        if (data[0].equals("a")) {
                            _acker.onAck(chunk);
                        } else {
                            _acker.onNAck(chunk);
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("received ack has illformated chunk id: " + data[1]);
                    }

                } else {
                    System.out.println("received ack is illformated: " + dataString);
                }
            }

        } catch (SocketTimeoutException e) {
        } catch (IOException e) {
            // ignore, will occur when metadata package arrives as the last packet
            // then the handle packet thread will interrupt the receive method by closing
            // the _nAcker.getSocket()
        }
    }
}