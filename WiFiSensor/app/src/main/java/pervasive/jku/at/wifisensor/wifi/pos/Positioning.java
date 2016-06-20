package pervasive.jku.at.wifisensor.wifi.pos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Positioning {
    private List<Position> positions;

    /**
     * Returns most likely position based on given fingerprint
     * @param fingerprint current rssi finger print
     * @return likely position
     */
    public Position calcPosition(Map<String, Integer> fingerprint) {
        if(fingerprint == null || fingerprint.isEmpty()) {
            return null;
        }

        double minDistance = Double.MAX_VALUE;
        Position likelyPos = null;

        for(Position pos : positions) {
            // calculate euclidian distance for every position
            double d = 0.0;
            int matchingBssid = 0;
            Map<String, Integer> posFingerprint = pos.getFingerprint();

            for (Map.Entry<String, Integer> rssi : fingerprint.entrySet()) {
                String bssid = rssi.getKey();
                if(posFingerprint.containsKey(bssid)) {
                    d += Math.pow(posFingerprint.get(bssid) - rssi.getValue(), 2);
                    matchingBssid++;
                }
            }
            d = Math.sqrt(d);
            // use position only if it has one bssid in common with given fingerprint
            if (matchingBssid > 0 && d < minDistance) {
                minDistance = d;
                likelyPos = pos;
            }
        }

        return likelyPos;
    }

    public void parsePositions(File file) throws IOException {
        if (file == null) {
            return;
        }
        positions = new ArrayList<>();

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
            for(String line = br.readLine(); line != null; line = br.readLine()) {
                Position pos = Position.parseCSVLine(line);
                // only keep last entry of position (see Position.equal)
                positions.remove(pos);
                positions.add(pos);
            }
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
