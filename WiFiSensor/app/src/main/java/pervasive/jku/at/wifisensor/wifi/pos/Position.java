package pervasive.jku.at.wifisensor.wifi.pos;

import java.util.HashMap;
import java.util.Map;

public class Position {
    /** semantic name of position */
    private String name;
    /** x position */
    private float x;
    /** y position */
    private float y;
    /** maps BSSID to signal strength */
    private Map<String, Integer> fingerprint;
    /** csv positions */
    private static final int NAME_IDX = 0;
    private static final int POSX_IDX = 1;
    private static final int POSY_IDX = 2;
    private static final int FIRST_BSSID_IDX = 3;

    public Position(String name, float x, float y, Map<String, Integer> fingerprint) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.fingerprint = fingerprint;
    }

    public Map<String, Integer> getFingerprint() {
        return fingerprint;
    }

    public float getY() {
        return y;
    }

    public float getX() {
        return x;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Position position = (Position) o;

        if (Float.compare(position.x, x) != 0) return false;
        if (Float.compare(position.y, y) != 0) return false;
        return name.equals(position.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (x != +0.0f ? Float.floatToIntBits(x) : 0);
        result = 31 * result + (y != +0.0f ? Float.floatToIntBits(y) : 0);
        return result;
    }

    @Override
    public String toString() {
        return name + "(" + x + "/" + y + ")";
    }

    /**
     * Parses csv line and returns positon
     * @param line csv line
     * @return parsed position
     */
    public static Position parseCSVLine(String line) {
        if (line == null) {
            return null;
        }
        String[] fields = line.split(",");
        // line must contain at least name, x, y and one BSSID + Level
        // BSSID + Level must always come in pairs
        if (fields.length < 5 || (fields.length - 3) % 2 != 0) {
            return null;
        }
        Map<String, Integer> fingerprint = new HashMap<>();
        for (int i = FIRST_BSSID_IDX; i < fields.length; i += 2) {
            fingerprint.put(fields[i], Integer.parseInt(fields[i+1]));
        }

        return new Position(fields[NAME_IDX],
                Float.parseFloat(fields[POSX_IDX]),
                Float.parseFloat(fields[POSY_IDX]),
                fingerprint);
    }
}
