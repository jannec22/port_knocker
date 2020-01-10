package s18749;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.HashMap;

public class UdpFile {
    private UdpFileReader[] _readers;
    private int READERS_COUNT = 3;

    private File _file;
    private boolean _ready = false;;
    private DatagramSocket _socket;
    private boolean _loaded = false;
    private String _fileName = "noname.txt";
    private HashMap<Integer, HashMap<Integer, byte[]>> _chunks = new HashMap<>();

    UdpFile(DatagramSocket socket) {
        _readers = new UdpFileReader[READERS_COUNT];
        _socket = socket;
    }

    public DatagramSocket getSocket() {
        return _socket;
    }

    public void setFilename(String name) {
        _fileName = name;
    }

    public void ready() {
        for (UdpFileReader reader : _readers) {
            reader.interrupt();

            try {
                reader.join();
            } catch (InterruptedException e) {
            }

            _ready = true;
        }
    }

    public boolean load() {
        System.out.println("loading " + _fileName);

        for (int i = 0; i < READERS_COUNT; ++i) {
            UdpFileReader reader = new UdpFileReader(this);
            _readers[i] = reader;
            reader.start();
        }

        for (UdpFileReader reader : _readers) {
            try {
                reader.join();
            } catch (InterruptedException e) {
            }
        }

        if (_ready) {
            createFile();

            _loaded = true;
        } else {
            System.out.println("could not load file");
        }

        return _ready;
    }

    private void createFile() {
        if (_file == null || !_file.exists()) {
            File dir = new File("received");

            if (dir != null) {
                dir.mkdirs();
            }

            _file = new File(dir.getAbsolutePath() + File.separator + _fileName);
            try {
                _file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void save() {
        if (_loaded == false) {
            load();
        }
        createFile();

        try {
            if (_file == null || !_file.exists()) {
                File dir = new File("received");
                if (dir != null) {
                    dir.getParentFile().mkdirs();
                }
                _file = new File(dir.getAbsolutePath() + File.separator + _fileName);
                _file.createNewFile();
            }
            System.out.println("received" + File.separator + _fileName);
            System.out.println("saving " + _file.getAbsolutePath());

            DataOutputStream stream = new DataOutputStream(new FileOutputStream(_file));
            int offset = 0;

            for (int i = 0; i < _chunks.size(); i++) {
                HashMap<Integer, byte[]> chunk = _chunks.get(i);

                for (int j = 0; j < chunk.size(); j++) {
                    byte[] bytes = chunk.get(j);

                    stream.write(bytes, offset, bytes.length);
                    offset += bytes.length;

                }
            }

            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File getRawFile() {
        return _file;
    }
}