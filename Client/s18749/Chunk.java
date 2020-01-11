package s18749;

import java.util.HashMap;

public class Chunk {
    private boolean _completed = false;
    private int _id;
    private HashMap<Integer, byte[]> parts = new HashMap<>();

    Chunk(int id) {
        _id = id;
    }

    public boolean checkIntegrity(int chunkSize) {

        for (int j = 0; j < parts.size(); j++) {
            byte[] bytes = parts.get(j);

            if (bytes == null)  {
                return false;
            }
        }

        return true;
    }

    public void addPart(int id, byte[] part) {
        parts.put(id, part);
    }

    public void setCompleted(boolean complete) {
        _completed = complete;
    }

    public int getId() {
        return _id;
    }

    public boolean getCompleted() {
        return _completed;
    }

    public HashMap<Integer, byte[]> getParts() {
        return parts;
    }

    public int size() {
        return parts.size();
    }

    public byte[] get(int id) {
        return parts.get(id);
    }
}