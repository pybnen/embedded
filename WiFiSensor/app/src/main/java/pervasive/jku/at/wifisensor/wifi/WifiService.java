package pervasive.jku.at.wifisensor.wifi;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.HashSet;

public class WifiService extends Service {

    private static final String TAG_OTH = "ws";
    private BroadcastReceiver yourReceiver;
    private WifiManager wifiManager;
    private HashSet<WifiScanListener> listeners;
    private boolean scanning;

    private IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public WifiService getServerInstance() {
            return WifiService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG_OTH, "on start called");
        if(!scanning){
            this.registerReceiver(this.yourReceiver, new IntentFilter(
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            wifiManager.startScan();
        }
        scanning=true;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        Log.d(TAG_OTH, "on create");
        super.onCreate();
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        listeners=new HashSet<WifiScanListener>(1);
        this.yourReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                for (WifiScanListener l : listeners) {
                    l.onWifiChanged(new WifiScanEvent(wifiManager.getConnectionInfo().getMacAddress(),wifiManager.getScanResults(), System.currentTimeMillis()));
                }
                wifiManager.startScan();
            }
        };
    }

    public void stopScanning(){
        if(scanning) {
            scanning = false;
            this.unregisterReceiver(this.yourReceiver);
        }
        stopSelf();
    }

    public boolean isScanning(){
        return scanning;
    }

    @Override
    public void onDestroy() {
        stopScanning();
        Log.d(TAG_OTH, "on destory");
        super.onDestroy();
    }

    public void registerListener(WifiScanListener listener) {
        listeners.add(listener);
    }

    public void unregisterListener(WifiScanListener listener) {
        listeners.remove(listener);
    }
}