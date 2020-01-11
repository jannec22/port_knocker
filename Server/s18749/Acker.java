package s18749;

import java.net.DatagramSocket;
import java.util.HashMap;

public class Acker {
    private static int NACKERS_COUNT = 3;
    private static final long CHUNK_TIMEOUT = 1000;
    private AckListener[] _listeners;
    private Client _client;
    private DatagramSocket _socket;
    private HashMap<Integer, AckTimer> _timers = new HashMap<>();

    Acker(Client client) {
        _socket = client.getSocket();
        _client = client;
        _listeners = new AckListener[NACKERS_COUNT];
    }

    public DatagramSocket getSocket() {
        return _socket;
    }

    public Client getClient() {
        return _client;
    }

    public void expect(int chunk) {
        _timers.put(chunk, new AckTimer(CHUNK_TIMEOUT, chunk, this));
        // System.out.println("expecting chunk: " + chunk + " in " + CHUNK_TIMEOUT);
    }

    public synchronized void interruptListeners() {
        for (AckListener listener : _listeners) {
            listener.interrupt();

            try {
                listener.join();
            } catch (InterruptedException e) {
            }
        }
    }

    public void listen() {
        for (int i = 0; i < NACKERS_COUNT; ++i) {
            AckListener listener = new AckListener(this);
            _listeners[i] = listener;
            listener.start();
        }
    }

    public synchronized void onNAck(int chunk) {
        AckTimer timer = _timers.get(chunk);

        if (timer != null) {
            _timers.get(chunk).reset();
            _client.onResendNeeded(chunk);
        } else {
            System.out.println("no timer for chunk: " + chunk);
        }
    }

    public synchronized void onAck(int chunk) {
        AckTimer timer = _timers.get(chunk);
        _client.onAck();

        if (timer != null) {
            // System.out.println("acked " + chunk + " chunk");
            timer.stop();
            _timers.put(chunk, null);
        } else {
            System.out.println("chunk: " + chunk + " already acked");
        }
    }

}