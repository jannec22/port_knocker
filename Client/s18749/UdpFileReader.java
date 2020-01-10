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
    private UdpFile _udpFile;
    private InetAddress _address;
    private int _port;

    private int _percent = 0;
    private int _lastPercent = -1;
    private int _packetSize = 65535;
    private int _chunkSize = 100;
    private int _expectedBytes = 0;
    private int _extractedBytes = 0;
    private int _expectedChunks = 1;
    private int _completedChunks = 0;
    private HashMap<Integer, HashMap<Integer, byte[]>> _chunks = new HashMap<>();

    UdpFileReader(UdpFile file) {
        _socket = file.getSocket();
        _udpFile = file;
        _thread = new Thread(this);
    }

    public void start() {
        _thread.start();
    }

    public void interrupt() {
        _thread.interrupt();
        _socket.close();
    }

    public void join() throws InterruptedException {
        if (_thread != null) {
            _thread.join();
        }
    }

    private void sendAck(int chunk) {
        if (_socket.isClosed())
            return; // file received
        byte[] bytes = ("a::" + chunk).getBytes();
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, _address, _port);

        try {
            System.out.println("sending ack for chunk: " + chunk);
            _socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendNAck(int chunk) {
        if (_socket.isClosed())
            return; // file received
        byte[] bytes = ("n::" + chunk).getBytes();
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

        if (attrs.length == 4) {
            _udpFile.setFilename(attrs[0].replaceAll("[^a-zA-Z0-9\\._]+", ""));

            try {
                _expectedBytes = Integer.parseInt(attrs[1].trim());
                System.out.println("\nexpected bytes: " + _expectedBytes);
                _chunkSize = Integer.parseInt(attrs[2].trim());
                System.out.println("chunk size: " + _chunkSize);
                _packetSize = Integer.parseInt(attrs[3].trim());
                System.out.println("packet size: " + _packetSize + "\n");

                sendAck(0);
            } catch (NumberFormatException e) {
                System.out.println("metadata packet file size or part size is not a valid number: [" + attrs[1] + " or "
                        + attrs[3] + "]");

                sendNAck(chunk);
                return;
            }

            _expectedChunks = Math.round(_expectedBytes / (_packetSize - 4)) + 1; // 4 is the part number

            System.out.println("file size (bytes): " + _expectedBytes + " part size: " + _packetSize + " chunks count: "
                    + _expectedChunks);

            if (_completedChunks >= _expectedChunks) {
                System.out.println("metadata packet arrived as the last packet, interuppting readers");
                _udpFile.ready();
            }

        } else {
            System.out.println("received metadata packet is invalid: " + metadata);

            sendNAck(chunk);
        }
    }

    private void handleData(int part, int chunk, byte[] data) {
        // System.out.println("data packet arrived: " + new String(data,
        // StandardCharsets.UTF_8));
        _lastPercent = _percent;
        if (_expectedBytes != 0)
            _percent = _extractedBytes * 100 / _expectedBytes;
        _extractedBytes += data.length;

        if (_expectedBytes != 0 && _percent != _lastPercent) {
            System.out.print("\r[");
            for (int i = 0; i < 20; i++) {
                if (i <= _percent / 5) {
                    System.out.print(".");
                } else {
                    System.out.print(" ");
                }
            }
            System.out.print("] " + _percent + "% " + part + " packets received");
        }

        HashMap<Integer, byte[]> byteChunk = _chunks.get(chunk);

        if (byteChunk == null) {
            byteChunk = new HashMap<>();
        }

        System.out.println("\nexpected bytes: " + _expectedBytes + ", extracted bytes: " + _extractedBytes);
        if (byteChunk.size() == _chunkSize || (_expectedBytes != 0 && _expectedBytes <= _extractedBytes)) { // completed
                                                                                                            // chunk
            sendAck(chunk);
            _completedChunks++;
        }

        byteChunk.put(part, data);
        _chunks.put(chunk, byteChunk);

        System.out.println("chunk size: " + byteChunk.size() + ", completed chunks: " + _completedChunks);
    }

    private void handlePacket(DatagramPacket packet) {
        ByteBuffer wrappedPart = ByteBuffer.wrap(Arrays.copyOfRange(packet.getData(), 0, 4));
        ByteBuffer wrappedChunk = ByteBuffer.wrap(Arrays.copyOfRange(packet.getData(), 4, 8));
        byte[] data = Arrays.copyOfRange(packet.getData(), 8, packet.getLength());

        int packetNumber = wrappedPart.getInt();
        int chunk = wrappedChunk.getInt();
        HashMap<Integer, byte[]> chunkExists = _chunks.get(chunk);

        if (chunkExists != null && chunkExists.get(packetNumber) != null) {
            return; // drop duplicated packet
        }

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

        System.out.println("packet(" + packetNumber + ") arrived, chunk(" + chunk + "), chunks: " + _completedChunks
                + "/" + _expectedChunks);
        if (_completedChunks >= _expectedChunks) {
            System.out.println("file is ready");
            _udpFile.ready();
        }
    }

    @Override
    public void run() {
        System.out.println("spawning udp file reader");
        try {
            _socket.setSoTimeout(5000);

            while (_expectedChunks == 0 || _completedChunks < _expectedChunks) {
                _extracted = new byte[_packetSize];
                DatagramPacket rcv = new DatagramPacket(_extracted, _extracted.length);

                _socket.receive(rcv);
                handlePacket(rcv);
            }

        } catch (SocketTimeoutException e) {
        } catch (IOException e) {
            // ignore, will occur when metadata package arrives as the last packet
            // then the handle packet thread will interrupt the receive method by closing
            // the _socket
        }
    }

}