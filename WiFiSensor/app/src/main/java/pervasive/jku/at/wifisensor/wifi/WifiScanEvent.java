package pervasive.jku.at.wifisensor.wifi;

import android.net.wifi.ScanResult;

import java.util.Collections;
import java.util.List;

public class WifiScanEvent {
    private final long time;
    private final List<ScanResult> result;
    private String mac;

    public WifiScanEvent(String mac, List<ScanResult> result, long time) {
        this.mac=mac;
        this.result = Collections.unmodifiableList(result);
        this.time = time;
    }

    public List<ScanResult> getResult() {
        return result;
    }

    public long getTime() {
        return time;
    }

    public String getMAC() {
        return mac;
    }

}
