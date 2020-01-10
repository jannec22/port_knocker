package s18749;

import java.util.Timer;
import java.util.TimerTask;

public class AckTimer {
    private long _timeout;
    private int _chunk;
    private int _attempts = 0;
    private Acker _acker;
    private Timer _timer;

    AckTimer(long miliseconds, int chunk, Acker acker) {
        _timeout = miliseconds;
        _chunk = chunk;
        _acker = acker;
        start();
    }

    public void start() {
        _timer = new Timer();

        _timer.schedule(new TimerTask() {

            @Override
            public void run() {
                Client client = _acker.getClient();
                
                if (client != null) {
                    // System.out.println("got NAck on chunk: " + _chunk + ", attempt: " + _attempts);
                    _acker.onNAck(_chunk);
                }
            }
        }, _timeout);
    }

    public void stop() {
        // System.out.println("stopping timer for chunk: " + _chunk);
        _timer.cancel();
    }

    public void reset() {
        if (++_attempts < 2) {
            _timer.cancel();
            start();
        }
    }
}