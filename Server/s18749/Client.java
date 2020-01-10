package s18749;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;

public class Client implements Runnable {

    private static int PACKET_CHUNK_SIZE = 508;
    private static int WINDOW_SIZE = 1000;
    private static int WINDOW_GAP = 10; // ms
    // private static String THE_FILE_NAME = "smallfile";
    // private static String THE_FILE_NAME = "midfile";
    // private static String THE_FILE_NAME = "bigfile";
    private static String THE_FILE_NAME = "image";

    private File _file;
    private DatagramSocket _socket;
    private int _port;
    private String _host;
    private InetAddress address;
    private HashMap<Integer, DatagramPacket[]> _chunks;

    Client(String origin) throws SocketException {
        _socket = new DatagramSocket();
        System.out.println("client origin: " + origin);
        String[] addr = origin.split(":");

        if (addr.length < 2) {
            System.out.println("given client port or host is invalid");
            return;
        }

        try {
            _host = addr[0].substring(1);
            _port = Integer.parseInt(addr[1]);
            address = InetAddress.getByName(_host);

        } catch (NumberFormatException e) {
            System.out.println("given client port is invalid");
            return;
        } catch (UnknownHostException e) {
            System.out.println("ERR: client host address is invalid: " + _host);
            return;
        }
    }

    public DatagramSocket getSocket() {
        return _socket;
    }

    private DatagramPacket prepareMetadataPackage() {
        byte[] metadata = (THE_FILE_NAME + "::" //
                + _file.length() + "::" //
                + WINDOW_SIZE + "::" //
                + PACKET_CHUNK_SIZE).getBytes();
        byte[] extracted = new byte[metadata.length + 8];
        System.out.println("preparing matadata packet: " + metadata);

        for (int i = 0; i < extracted.length; i++) {
            if (i < 8) { // 4 for part and 4 for chunk
                extracted[i] = 0; // part
            } else {
                extracted[i] = metadata[i - 8];
            }
        }

        return new DatagramPacket(extracted, extracted.length, address, _port);
    }

    private DatagramPacket[] packChunk(byte[] bytes, int chunk) {
        DatagramPacket[] packets = new DatagramPacket[bytes.length / (PACKET_CHUNK_SIZE - 8) + 1];
        int packet = 0;

        for (int i = 0; i < bytes.length; i += PACKET_CHUNK_SIZE - 8) {
            byte[] extracted = new byte[Math.min(PACKET_CHUNK_SIZE, bytes.length - i + 8)];
            byte[] packetIndexBytes = ByteBuffer.allocate(4).putInt(packet + chunk).array();
            byte[] chunkBytes = ByteBuffer.allocate(4).putInt(chunk).array();

            for (int j = 0; j < extracted.length; j++) {
                if (j < 4) {
                    extracted[j] = packetIndexBytes[j];
                } else if (j < 8) {
                    extracted[j] = chunkBytes[j - 4];
                } else {
                    extracted[j] = bytes[i + j - 8];
                }
            }

            // System.out.println(new String(extracted, StandardCharsets.UTF_8));

            packets[packet++] = new DatagramPacket(extracted, extracted.length, address, _port);
        }

        System.out.println("packed chunk, packets: " + packet);

        return packets;
    }

    private HashMap<Integer, DatagramPacket[]> packFile(File file) {
        System.out.println("packing file");

        if (Math.ceil(file.length() / PACKET_CHUNK_SIZE) > Integer.MAX_VALUE) {
            System.out.println("WARN: file size is to be big to be packed in " + PACKET_CHUNK_SIZE + " chunks");
            PACKET_CHUNK_SIZE = (int) (file.length() / (PACKET_CHUNK_SIZE * 0.8));
        }

        try {
            if (file.length() == 0) {
                // empty file
                throw new FileNotFoundException("the file: " + THE_FILE_NAME + " is empty");
            }

            int chunkIndex = 0;
            int part = 0;
            HashMap<Integer, DatagramPacket[]> parts = new HashMap<>();
            int windowByteSize = WINDOW_SIZE * PACKET_CHUNK_SIZE;

            byte[] allBytes = Files.readAllBytes(file.toPath());

            int offset = 0;

            for (int i = Math.min((allBytes.length - offset) - 1, windowByteSize); i < allBytes.length; i += Math
                    .min(allBytes.length - offset, windowByteSize)) {
                DatagramPacket[] packets = packChunk(Arrays.copyOfRange(allBytes, offset, offset + i), ++part);

                if (packets != null) {
                    parts.put(++chunkIndex, packets);
                }
                offset = i;
            }

            System.out.println("file packed, chunks: " + chunkIndex + ", packets: " + part);

            return parts;

        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void sendChunk(DatagramPacket[] chunk) throws IOException {
        if (chunk != null && _socket != null && !_socket.isClosed()) {
            for (DatagramPacket packet : chunk) {
                if (packet != null) {
                    System.out.println("sending packet, chunk: " + chunk + " size: " + packet.getLength());
                    _socket.send(packet);
                } else {
                    System.out.println("could not send packet");
                }
            }
        } else {
            System.out.println("socket is closed or chunk is corrupted");
        }
    }

    public void onResendNeeded(int chunk) {
        WINDOW_GAP += 2;
        try {
            if (chunk == 0) {
                DatagramPacket[] packets = { prepareMetadataPackage() };
                sendChunk(packets);
            } else {
                DatagramPacket[] packets = _chunks.get(chunk);
                if (packets != null) {
                    sendChunk(packets);
                } else {
                    System.out.println("no chunk of index: " + chunk);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println("sending file: " + THE_FILE_NAME);
        _file = new File(THE_FILE_NAME);
        long size = _file.length();

        if (!_file.exists()) {
            System.out.println("the file: " + _file.getAbsolutePath() + " does not exist!");
            return;
        }

        _chunks = packFile(_file);
        if (_chunks == null) {
            System.out.println("could not pack the file, aborting");
            return;
        }

        try {
            Acker acker = new Acker(this);
            int chunk = 0;
            DatagramPacket[] metadataChunk = { prepareMetadataPackage() };
            _chunks.put(chunk, metadataChunk);

            acker.listen();

            for (DatagramPacket[] windowChunk : _chunks.values()) {
                // System.out.println("sending chunk, packets: " + windowChunk.length);
                acker.expect(chunk++);
                sendChunk(windowChunk);
                Thread.sleep(WINDOW_GAP);
            }

            System.out.println("file sent. size: " + size + ", chunks: " + (_chunks.size()));

        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}