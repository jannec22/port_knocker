package s18749;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.HashMap;

public class UdpFile {

    private File _file;
    private DatagramSocket _socket;
    private boolean _loaded = false;
    private UdpFileReader _reader;

    UdpFile(DatagramSocket socket) {
        _socket = socket;
    }

    public boolean load() {
        _reader = new UdpFileReader(_socket);

        _reader.start();
        _reader.join();

        if (_reader.loaded()) {
            _loaded = true;
            return true;
        } else {
            System.out.println("could not load file");
            return false;
        }
    }

    private void createFile() {
        if (_file == null || !_file.exists()) {
            File dir = new File("received");

            if (dir != null) {
                dir.mkdirs();
            }

            _file = new File(dir.getAbsolutePath() + File.separator + _reader.getFilename());
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
                _file = new File(dir.getAbsolutePath() + File.separator + _reader.getFilename());
                _file.createNewFile();
            }
            System.out.println("saving " + _file.getAbsolutePath());

            DataOutputStream stream = new DataOutputStream(new FileOutputStream(_file));
            HashMap<Integer, Chunk> chunks = _reader.getChunks();
            System.out.println(chunks);

            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);

                if (chunk != null) {
                    for (int j = 0; j < chunk.size(); j++) {
                        byte[] bytes = chunk.get(j);

                        if (bytes != null) {
                            stream.write(bytes, 0, bytes.length);
                        } else {
                            System.out.println("missing packet: " + j + " chunk: " + i);
                        }

                    }
                } else {
                    System.out.println("missing chunk: " + i);
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

    public DatagramSocket getSocket() {
        return _socket;
    }
}