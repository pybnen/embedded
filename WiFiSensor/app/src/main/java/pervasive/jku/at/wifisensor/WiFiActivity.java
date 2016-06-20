package pervasive.jku.at.wifisensor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import java.util.HashMap;
import java.util.Map;

import pervasive.jku.at.wifisensor.wifi.WifiScanEvent;
import pervasive.jku.at.wifisensor.wifi.WifiScanListener;
import pervasive.jku.at.wifisensor.wifi.WifiService;
import pervasive.jku.at.wifisensor.wifi.pos.Position;
import pervasive.jku.at.wifisensor.wifi.pos.Positioning;

public class WiFiActivity extends ActionBarActivity implements WifiScanListener {

    private static final String TAG_REG = "reg";
    private static final String TAG_SEN = "sen";
    private static final String TAG_OTH = "oth";
    private static final String TAG_IO = "io";
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final float NOISE = 8.0f;

    private static final String WIFI_SENSOR_NAME = "WiFi RSSi Sensor";
    private static final String RSSI_LOG_FILENAME = "positions.csv";

    private boolean wifiBounded;
    private WifiService wifiService;
    private ServiceConnection wifiServiceConnection;

    private String semanticPos;
    private float posX = 0;
    private float posY = 0;
    private FileOutputStream fileOutput;
    private PrintWriter writer;
    private boolean learnMode = false;
    private Positioning positioning = null;
    private Position curPos = null;

    private SensorManager mSensorManager;
    private boolean mStart;

    private float[] mLastAccel = new float[3];
    private float[] mCurrDelta = new float[3];

    private int vertAccCount = 0;
    long tStartTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifi_sensor);

        positioning = new Positioning();
        readPositions();

        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggle_wifi_scan);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                        if (isChecked) {
                                                            openLogFile();
                                                            curPos = null;
                                                            TextView txtCurPos = (TextView) findViewById(R.id.txt_cur_pos);
                                                            txtCurPos.setText(getResources().getString(R.string.pos_learn_mode));

                                                        } else {
                                                            closeLogFile();
                                                            readPositions();
                                                        }
                                                    }
                                                }
        );
        Log.d(TAG_OTH, "on create");
        if (!wifiBounded) {
            bindWifiService();
        }

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
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

                Intent mIntent = new Intent(WiFiActivity.this, WifiService.class);
                Log.d(TAG_OTH, "starting WifiService");
                startService(mIntent);
                registerWifi();
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

        Map<String, Integer> fingerprint = new HashMap<>();
        sbCsv.append(semanticPos).append(",")
                .append(posX).append(",")
                .append(posY).append(",");
        for (ScanResult data : event.getResult()) {
            sb.append(data.BSSID).append("/")
                    .append(data.level).append(", ");

            sbCsv.append(data.BSSID).append(",")
                    .append(data.level).append(",");

            fingerprint.put(data.BSSID, data.level);
        }
        tc.setText(sb.toString());

        if (learnMode && writer != null) {
            // trim the last ',"
            sbCsv.setLength(sbCsv.length() - 1);
            writer.println(sbCsv.toString());
        } else if(!learnMode) {
            curPos = positioning.calcPosition(fingerprint);
            TextView txtCurPos = (TextView) findViewById(R.id.txt_cur_pos);
            txtCurPos.setText(curPos != null ?
                    curPos.toString() :
                    getResources().getString(R.string.pos_not_found));
        }
    }

    @Override
    protected void onDestroy() {
        Intent mIntent = new Intent(WiFiActivity.this, WifiService.class);
        Log.d(TAG_OTH, "stopping WifiService");
        unregisterWifi();

        unbindService(wifiServiceConnection);
        Log.d(TAG_OTH, "on destroy");
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG_OTH, "on resume");
        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG_OTH, "on pause");
        mSensorManager.unregisterListener(mSensorListener);
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
     * Checks if external storage is mounted
     * @return true if writable
     */
    private boolean isExternalStorageMounted() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * opens log file, and saves semantic position and absolute position input,
     * disables input for semantic position and absolute position
     */
    private void openLogFile() {
        EditText txtSemanticPos = (EditText) findViewById(R.id.txt_semantic_pos);
        EditText txtPos = (EditText) findViewById(R.id.txt_pos);

        semanticPos = txtSemanticPos.getText().toString();
        parseAbsolutePos(txtPos.getText().toString());
        if(isExternalStorageMounted()) {
            File file = new File(Environment.getExternalStoragePublicDirectory(""), RSSI_LOG_FILENAME);
            try {
                fileOutput = new FileOutputStream(file, true);
                writer = new PrintWriter(fileOutput);
                learnMode = true;
            } catch (IOException e) {
                Log.e(TAG_IO, "error while creating log file", e);
            }
        } else {
            Log.e(TAG_IO, "external storage not mounted");
        }

        // disable settings while scanning
        txtSemanticPos.setEnabled(false);
        txtPos.setEnabled(false);
    }

    /**
     * Closes print writer and file output stream
     * enables input for semantic position and absolute position
     */
    private void closeLogFile() {
        EditText txtSemanticPos = (EditText) findViewById(R.id.txt_semantic_pos);
        EditText txtPos = (EditText) findViewById(R.id.txt_pos);
        learnMode = false;
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
        txtSemanticPos.setEnabled(true);
        txtPos.setEnabled(true);
    }

    /**
     * Parses user input for absolute position
     * and sets position
     * @param input user input
     */
    private void parseAbsolutePos(String input) {
        if (!input.isEmpty()) {
            String[] sPos = input.split("/");
            if(sPos.length == 2) {
                posX = Float.parseFloat(sPos[0]);
                posY = Float.parseFloat(sPos[1]);
                return;
            }
        }
        // no valid input set to zero
        posX = 0;
        posY = 0;
    }

    /**
     * Reads position file
     */
    private void readPositions() {
        if(isExternalStorageMounted()) {
            File file = new File(Environment.getExternalStoragePublicDirectory(""), RSSI_LOG_FILENAME);
            try {
                positioning.parsePositions(file);
            } catch (IOException e) {
                Log.e(TAG_IO, "error while reading log file", e);
            }
        } else {
            Log.e(TAG_IO, "external storage not mounted");
        }
    }


    private final SensorEventListener mSensorListener = new SensorEventListener() {

        public void onSensorChanged(SensorEvent event) {
            if (vertAccCount == 0) {
                tStartTime = System.currentTimeMillis();
            } else if ((System.currentTimeMillis() - tStartTime) >= 4000) {
                vertAccCount = 0;
                tStartTime = System.currentTimeMillis();
            }

            if (!mStart) {
                mLastAccel[X] = event.values[X];
                mLastAccel[Y] = event.values[Y];
                mLastAccel[Z] = event.values[Z];
                mStart = true;
            } else {
                mCurrDelta[X] = Math.abs(mLastAccel[X] - event.values[X]);
                mCurrDelta[Y]  = Math.abs(mLastAccel[Y] - event.values[Y]);
                mCurrDelta[Z]  = Math.abs(mLastAccel[Z] - event.values[Z]);
                if (mCurrDelta[X] < NOISE) mCurrDelta[X] = (float)0.0;
                if (mCurrDelta[Y] < NOISE) mCurrDelta[Y] = (float)0.0;
                if (mCurrDelta[Z] < NOISE) mCurrDelta[Z] = (float)0.0;
                mLastAccel[X] = event.values[X];
                mLastAccel[Y] = event.values[Y];
                mLastAccel[Z] = event.values[Z];
                if (mCurrDelta[Y] > mCurrDelta[X]) {
                    Log.e(TAG_SEN, "###vertical X: " + mCurrDelta[X] + " Y: " + mCurrDelta[Y] + " Time Elapsed: " + (System.currentTimeMillis() - tStartTime));

                    if ((System.currentTimeMillis() - tStartTime) < 4000) {
                        vertAccCount++;
                    }
                }
            }

            if (vertAccCount > 4) {
                //HANDSHAKE DETECTED! COMMUNICATE CONTACT INFO
                Log.e(TAG_SEN, "###HANDSHAKE !!");
                Log.e(TAG_SEN, "###I am " + ((EditText)findViewById(R.id.txt_name)).getText() +
                        " we are at " + (curPos != null ? curPos.toString() : "?"));
                vertAccCount = 0;
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
}
