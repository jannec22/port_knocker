package s18749;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;

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
                extracted = new byte[5];
                DatagramPacket rcv = new DatagramPacket(extracted, extracted.length);

                // System.out.println(_thread.getId() + " waiting for ack's");
                _acker.getSocket().receive(rcv);
                // System.out.println("got new ack");

                ByteBuffer wrapped = ByteBuffer.wrap(Arrays.copyOfRange(extracted, 1, 5));
                char type = (char) extracted[0];
                int chunk = wrapped.getInt();

                try {

                    if (type == 'a') {
                        _acker.onAck(chunk);
                    } else {
                        _acker.onNAck(chunk);
                    }

                } catch (NumberFormatException e) {
                    System.out.println("received ack has illformated chunk id: " + chunk);
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