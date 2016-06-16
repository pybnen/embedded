package pervasive.jku.at.wifisensor;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.ScanResult;
import android.os.Environment;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import pervasive.jku.at.wifisensor.wifi.WifiScanEvent;
import pervasive.jku.at.wifisensor.wifi.WifiScanListener;
import pervasive.jku.at.wifisensor.wifi.WifiService;

public class WiFiActivity extends ActionBarActivity implements WifiScanListener {

    private static final String TAG_REG = "reg";
    private static final String TAG_SEN = "sen";
    private static final String TAG_OTH = "oth";
    private static final String TAG_IO = "io";

    private static final String WIFI_SENSOR_NAME = "WiFi RSSi Sensor";

    private boolean wifiBounded;
    private WifiService wifiService;
    private ServiceConnection wifiServiceConnection;

    private String semanticPos;
    private FileOutputStream fileOutput;
    private PrintWriter writer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifi_sensor);

        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggle_wifi_scan);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                        if (isChecked) {
                                                            openLogFile();

                                                            Intent mIntent = new Intent(WiFiActivity.this, WifiService.class);
                                                            Log.d(TAG_OTH, "starting WifiService");
                                                            startService(mIntent);
                                                            registerWifi();
                                                        } else {
                                                            Intent mIntent = new Intent(WiFiActivity.this, WifiService.class);
                                                            Log.d(TAG_OTH, "stopping WifiService");
                                                            unregisterWifi();

                                                            closeLogFile();;
                                                        }
                                                    }
                                                }
        );
        Log.d(TAG_OTH, "on create");
        if (!wifiBounded) {
            bindWifiService();
        }
    }

    private void bindWifiService() {
        Log.d(TAG_OTH, "binding wifiService");

        Intent mIntent = new Intent(this, WifiService.class);
        wifiServiceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG_OTH, "service " + name.toShortString() + " is disconnected");
                wifiBounded = false;
                wifiService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG_OTH, "service " + name.toShortString() + " is connected");
                wifiBounded = true;
                WifiService.LocalBinder mLocalBinder = (WifiService.LocalBinder) service;
                wifiService = mLocalBinder.getServerInstance();
                registerWifi();
                ((ToggleButton) findViewById(R.id.toggle_wifi_scan)).setChecked(wifiService.isScanning());
            }
        };
        bindService(mIntent, wifiServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onWifiChanged(WifiScanEvent event) {
        Log.d(TAG_SEN, "sensor event received from " + WIFI_SENSOR_NAME + " " + event.getMAC());
        TextView tc = (TextView) findViewById(R.id.sensorContent);
        StringBuffer sb = new StringBuffer();
        StringBuilder sbCsv = new StringBuilder();

        sbCsv.append(semanticPos + ",");
        for (ScanResult data : event.getResult()) {
            sb.append(data.BSSID + "/" + data.level + ", ");
            sbCsv.append(data.BSSID + "," + data.level + ",");
        }
        tc.setText(sb.toString());

        if (writer != null) {
            // trim the last ',"
            sbCsv.setLength(sbCsv.length() - 1);
            writer.println(sbCsv.toString());
        }
    }

    @Override
    protected void onDestroy() {
        unbindService(wifiServiceConnection);
        Log.d(TAG_OTH, "on destroy");
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG_OTH, "on resume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG_OTH, "on pause");
    }

    private void registerWifi() {
        Log.d(TAG_REG, "registering " + WIFI_SENSOR_NAME);
        wifiService.registerListener(this);
    }

    private void unregisterWifi() {
        if (wifiService != null) {
            Log.d(TAG_REG, "unregistering " + WIFI_SENSOR_NAME);
            wifiService.unregisterListener(this);
            wifiService.stopScanning();
        }
    }

    /**
     * Checks if external storage is writable
     * @return true if writable
     */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * opens log file, and saves semantic position input,
     * disables input for semantic position and log file name
     */
    private void openLogFile() {
        EditText txtLogfile = (EditText) findViewById(R.id.txt_log_file);
        EditText txtSemanticPos = (EditText) findViewById(R.id.txt_semantic_pos);

        semanticPos = txtSemanticPos.getText().toString();

        if(isExternalStorageWritable()) {
            File file = new File(Environment.getExternalStoragePublicDirectory(""),
                    txtLogfile.getText().toString() + ".csv");
            try {
                fileOutput = new FileOutputStream(file, true);
                writer = new PrintWriter(fileOutput);
            } catch (IOException e) {
                Log.e(TAG_IO, "error while creating log file", e);
            }
        } else {
            Log.e(TAG_IO, "external storage not writable");
        }

        // disable settings while scanning
        txtLogfile.setEnabled(false);
        txtSemanticPos.setEnabled(false);
    }

    /**
     * Closes print writer and file output stream
     * enables input for semantic position and log file name
     */
    private void closeLogFile() {
        EditText txtLogfile = (EditText) findViewById(R.id.txt_log_file);
        EditText txtSemanticPos = (EditText) findViewById(R.id.txt_semantic_pos);

        if (writer != null) {
            writer.close();
            writer = null;
        }

        if(fileOutput != null) {
            try {
                fileOutput.close();
            } catch (IOException e) {
                Log.e(TAG_IO, "error while closing logfile", e);
            }
            fileOutput = null;
        }

        // allow settings to be changed
        txtLogfile.setEnabled(true);
        txtSemanticPos.setEnabled(true);
    }
}