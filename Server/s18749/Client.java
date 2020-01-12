package s18749;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;

public class Client implements Runnable {

    private static int PACKET_CHUNK_SIZE = 508;
    private static int WINDOW_SIZE = 100; // packets per chunk
    // private int WINDOW_GAP = 5; // ms

    private String _fileName = "image";
    private File _file;
    private DatagramSocket _socket;
    private int _port;
    private String _host;
    private InetAddress _address;
    private HashMap<Integer, DatagramPacket[]> _chunks;

    Client(String origin, String fileName) throws SocketException {
        _socket = new DatagramSocket();
        _fileName = fileName;

        System.out.println("client origin: " + origin);
        String[] addr = origin.split(":");

        if (addr.length < 2) {
            System.out.println("given client port or host is invalid");
            return;
        }

        try {
            _host = addr[0].substring(1);
            _port = Integer.parseInt(addr[1]);
            _address = InetAddress.getByName(_host);

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
        byte[] metadata = (_fileName + "::" //
                + _file.length() + "::" //
                + (Math.min(WINDOW_SIZE, (_file.length() / PACKET_CHUNK_SIZE) + 1)) + "::" //
                + PACKET_CHUNK_SIZE + "::" //
                + _chunks.size() //
        ).getBytes();
        byte[] extracted = new byte[metadata.length + 8];
        System.out.println("preparing matadata packet: " + metadata);

        for (int i = 0; i < extracted.length; i++) {
            if (i < 8) { // 4 for part and 4 for chunk
                extracted[i] = 0; // part
            } else {
                extracted[i] = metadata[i - 8];
            }
        }

        return new DatagramPacket(extracted, extracted.length, _address, _port);
    }

    private DatagramPacket[] packChunk(byte[] bytes, int chunk) {
        DatagramPacket[] packets = new DatagramPacket[bytes.length / (PACKET_CHUNK_SIZE - 8) + 1];
        int packet = 0;

        for (int i = 0; i < bytes.length; i += PACKET_CHUNK_SIZE - 8) {
            byte[] extracted = new byte[Math.min(PACKET_CHUNK_SIZE, bytes.length - i + 8)];
            byte[] packetIndexBytes = ByteBuffer.allocate(4).putInt(packet).array();
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

            // System.out.println(" XXXXX " + new String(extracted,
            // StandardCharsets.UTF_8));
            // System.out.println("packing packet " + chunk + packet);

            packets[packet++] = new DatagramPacket(extracted, extracted.length, _address, _port);
        }

        // System.out.println("packed chunk, packets: " + packet);

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
                throw new FileNotFoundException("the file: " + _fileName + " is empty");
            }

            int chunkIndex = 0;
            int part = 0;
            HashMap<Integer, DatagramPacket[]> parts = new HashMap<>();
            int windowByteSize = WINDOW_SIZE * PACKET_CHUNK_SIZE;

            byte[] allBytes = Files.readAllBytes(file.toPath());

            int offset = 0;
            int i = Math.min(allBytes.length, windowByteSize);

            do {
                DatagramPacket[] packets = packChunk(Arrays.copyOfRange(allBytes, offset, i), ++part);

                if (packets != null) {
                    parts.put(++chunkIndex, packets);
                }

                offset = i;
                i += Math.min(allBytes.length - offset, windowByteSize);
            } while (offset < allBytes.length);

            System.out.println("\nfile packed, chunks: " + chunkIndex);

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
                    // System.out.println("sending packet, size: " + packet.getLength());
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
        try {
            if (chunk == 0) {
                System.out.println("resending metadata package");
                DatagramPacket[] packets = { prepareMetadataPackage() };
                sendChunk(packets);
            } else {
                DatagramPacket[] packets = _chunks.get(chunk);
                if (packets != null) {
                    System.out.println("resending chunk: " + chunk);
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
        System.out.println("sending file: " + _fileName);
        _file = new File(_fileName);
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
            int chunk = 0;
            byte[] extracted = new byte[1];
            DatagramPacket[] metadataChunk = { prepareMetadataPackage() };
            DatagramPacket ack = new DatagramPacket(extracted, extracted.length);
            _chunks.put(chunk, metadataChunk);

            _socket.setSoTimeout(5000);

            for (DatagramPacket[] windowChunk : _chunks.values()) {
                // System.out.println("sending chunk, packets: " + windowChunk.length);
                sendChunk(windowChunk);
                try {
                    _socket.receive(ack);
                    char type = (char) extracted[0];

                    try {

                        if (type == 'n')  {
                            onResendNeeded(chunk);
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("received ack has illformated chunk id: " + chunk);
                        onResendNeeded(chunk);
                    }
                } catch (SocketTimeoutException e) {
                    onResendNeeded(chunk);
                }
                // Thread.sleep(WINDOW_GAP);
            }

            System.out.println("file sent. size: " + size + ", chunks: " + _chunks.size());

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        // catch (InterruptedException e) {
        //     e.printStackTrace();
        // }
    }

}