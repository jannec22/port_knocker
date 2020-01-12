package s18749;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;

public class UdpFileReader implements Runnable {
    private DatagramSocket _socket;
    private byte[] _extracted;
    private Thread _thread;

    private String _fileName = "noname.txt";
    private volatile HashMap<Integer, Chunk> _chunks = new HashMap<>();

    private InetAddress _address;
    private int _port;
    private int _packetsReceived;
    private volatile boolean _ready = false;

    private volatile int _percent = 0;
    private volatile int _lastPercent = -1;
    private volatile int _packetSize = 65535;
    private volatile int _chunkSize = 5;
    private volatile long _expectedBytes = 0;
    private volatile long _extractedBytes = 0;
    private volatile int _expectedChunks = 1;

    UdpFileReader(DatagramSocket socket) {
        _socket = socket;
        _thread = new Thread(this);
    }

    public void start() {
        _thread.start();
    }

    public void join() {
        try {
            _thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean loaded() {
        return _ready;
    }

    public HashMap<Integer, Chunk> getChunks() {
        return _chunks;
    }

    public String getFilename() {
        return _fileName;
    }

    public void setMetadata(int[] attrs) {
        _expectedBytes = attrs[1];
        _chunkSize = attrs[2];
        _packetSize = attrs[3];
        _expectedChunks = attrs[4];
        System.out.println("\nexpected bytes: " + _expectedBytes);
        System.out.println("chunk size: " + _chunkSize);
        System.out.println("packet size: " + _packetSize);
        System.out.println("expected chunks: " + _expectedChunks + "\n");
    }

    public void onProgress(int length) {
        _lastPercent = _percent;
        _extractedBytes += length;
        ++_packetsReceived;
        if (_expectedBytes != 0)
            _percent = (int)(_extractedBytes * 100 / _expectedBytes);

        if (_percent != _lastPercent || _percent == 0) {
            System.out.print("\r[");
            for (int i = 0; i < 20; i++) {
                if (i <= _percent / 5) {
                    System.out.print(".");
                } else {
                    System.out.print(" ");
                }
            }
            System.out.print("] " + _percent + "% " + "chunks: " + _chunks.size() + " packets: " + _packetsReceived);
        }
    }

    private void sendAck(int chunk) {
        if (_socket.isClosed())
            return; // file received

        byte[] bytes = "a".getBytes();
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, _address, _port);

        try {
            _socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendNAck(int chunk) {
        if (_socket.isClosed())
            return; // file received

        byte[] bytes = "n".getBytes();
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, _address, _port);

        try {
            _socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleMetadata(int part, int chunk, byte[] data) {
        System.out.println("received metadata packet");
        String metadata = new String(data, 0, data.length, StandardCharsets.UTF_8);
        String[] attrs = metadata.split("::");

        if (attrs.length == 5) {
            _fileName = attrs[0].replaceAll("[^a-zA-Z0-9\\._]+", "");

            try {
                int[] attrsInt = new int[attrs.length];

                for (int i = 1; i < attrsInt.length; ++i) {
                    attrsInt[i] = Integer.parseInt(attrs[i]);
                }

                setMetadata(attrsInt);

                sendAck(0);
            } catch (NumberFormatException e) {
                System.out.println("metadata packet file size or part size is not a valid number: [" + attrs[1] + " or "
                        + attrs[2] + " or " + attrs[3] + " or " + attrs[4] + "]");

                sendNAck(chunk);
                return;
            }

            System.out.println("file size (bytes): " + _expectedBytes + " extracted bytes: " + _extractedBytes
                    + " chunks: " + _chunks.size());

            if (_chunks.size() >= _expectedChunks && _expectedChunks != 0) {
                System.out.println("metadata packet arrived as the last packet, interuppting readers");
                _thread.interrupt();
                _ready = true;
            }

        } else {
            System.out.println("received metadata packet is invalid: " + metadata);

            sendNAck(chunk);
        }
    }

    private void handleData(int part, int chunk, byte[] data) {
        if (data == null) {
            sendNAck(chunk);
            return;
        }

        Chunk byteChunk = _chunks.get(chunk - 1);

        if (byteChunk == null) {
            byteChunk = new Chunk(chunk);
        }

        byteChunk.addPart(part, data);
        _chunks.put(chunk - 1, byteChunk); // chunks starts from 1 because metadata has chunk 0
        onProgress(data.length);

        if (byteChunk.size() >= _chunkSize || (_expectedBytes != 0 && _expectedBytes <= _extractedBytes)) { // completed
            // chunk
            if (!byteChunk.getCompleted()) {
                if (byteChunk.checkIntegrity(_chunkSize)) {
                    byteChunk.setCompleted(true);
                    sendAck(chunk);
                } else {
                    // System.out.println("chunk is not ready");
                    sendNAck(chunk);
                }
            }
        }
    }

    private void handlePacket(DatagramPacket packet) {
        if (packet.getLength() < 8) {
            System.out.println(
                    "received packet(size: " + packet.getLength() + ") does not contain packet and chunk number: "
                            + new String(packet.getData(), StandardCharsets.UTF_8));
            return;
        }

        ByteBuffer wrappedPart = ByteBuffer.wrap(Arrays.copyOfRange(packet.getData(), 0, 4));
        ByteBuffer wrappedChunk = ByteBuffer.wrap(Arrays.copyOfRange(packet.getData(), 4, 8));
        byte[] data = Arrays.copyOfRange(packet.getData(), 8, packet.getLength());

        int packetNumber = wrappedPart.getInt();
        int chunk = wrappedChunk.getInt();
        // Chunk chunkExists = _chunks.get(chunk);

        // if (chunkExists != null) {
        //     byte[] packetExists = chunkExists.get(packetNumber);

        //     if (packetExists != null && packetExists.equals(data)) {
        //         System.out.println("dropping duplicated packet " + packetNumber);
        //         return; // drop duplicated packet
        //     }
        // }

        if (_address == null) {
            _address = packet.getAddress();
            _port = packet.getPort();
        }

        try {
            if (packetNumber == 0 && chunk == 0) {
                handleMetadata(packetNumber, chunk, data);
            } else {
                handleData(packetNumber, chunk, data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (_expectedChunks <= _chunks.size() && _expectedChunks != 1) {
            System.out.println("\nfile is ready");
            _ready = true;
        }
    }

    @Override
    public void run() {
        System.out.println("spawning udp file reader");
        try {
            _socket.setSoTimeout(10000);

            while (!_ready && !_thread.isInterrupted()) {
                _extracted = new byte[_packetSize];
                DatagramPacket rcv = new DatagramPacket(_extracted, _extracted.length);

                _socket.receive(rcv);
                if (!_ready) {
                    handlePacket(rcv);
                }
            }

        } catch (SocketTimeoutException e) {
            System.out.println("could not receive package, connection timed out");
        } catch (IOException e) {
            // will occur when metadata package is last
        }
    }

}