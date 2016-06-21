package pervasive.jku.at.wifisensor.comm;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.mqtt.client.Callback;
import org.fusesource.mqtt.client.CallbackConnection;
import org.fusesource.mqtt.client.Listener;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommService extends Service implements Listener {

    private static final String TAG_COM = "com";

    private CallbackConnection connection;
    private MQTT mqtt;
    private Set<CommunicationListener> listeners;
    private Set<String> topics;
    private boolean connected;
    private IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public CommService getServerInstance() {
            return CommService.this;
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG_COM, "commService start called");
        if (!connected) {
            setupListener();
            setupConnection(true);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private final Callback<Void> DEFAULT_SEND_CALLBACK = new Callback<Void>() {
        @Override
        public void onSuccess(Void value) {
            Log.d(TAG_COM, "sending of message succeeded");
        }

        @Override
        public void onFailure(Throwable value) {
            Log.d(TAG_COM, "sending of message failed");
            if(!connected){
                reconnect();
            }
        }
    };

    public boolean isConnected() {
        return connected;
    }

    public void addTopic(String topic) {
        if (!topics.contains(topic)) {
            if (isConnected()) {
                Log.d(TAG_COM, "adding topic with subscription: " + topic);
                subscribeToTopic(topic);
            } else {
                Log.d(TAG_COM, "adding topic without subscription: " + topic);
                topics.add(topic);
            }
        } else {
            Log.d(TAG_COM, "already subscripted: " + topic);
        }
    }

    public void removeTopic(String topic) {
        Log.d(TAG_COM, "removing topic: " + topic);
        if (!topics.contains(topic)) {
            if (isConnected()) {
                unsubscribeToTopic(topic);
            }
            topics.remove(topics);
        }
    }

    public void registerListener(CommunicationListener listener) {
        Log.d(TAG_COM, "registering listener");
        listeners.add(listener);
    }

    public void unregisterListener(CommunicationListener listener) {
        Log.d(TAG_COM, "removing listener");
        listeners.remove(listener);
    }

    public void sendMessage(String topic, String content) {
        sendMessage(topic, new UTF8Buffer(content.getBytes()));
    }

    public void sendMessage(String topic, Buffer content) {
        connection.publish(new UTF8Buffer(topic), content, QoS.AT_LEAST_ONCE, false, DEFAULT_SEND_CALLBACK);
    }

    public void sendMessage(String topic, Buffer content, QoS qos, boolean retain, Callback<Void> callBack) {
        connection.publish(new UTF8Buffer(topic), content, QoS.AT_LEAST_ONCE, false, callBack);
    }

    public void close() {
        connection.disconnect(null);
    }

    public void reconnect() {
        if (connection != null) {
            connection.disconnect(new Callback<Void>() {
                @Override
                public void onSuccess(Void value) {
                    Log.d(TAG_COM, "disconnecting successful. reconnecting ...");
                    connect();
                }

                @Override
                public void onFailure(Throwable value) {
                    Log.d(TAG_COM, "disconnecting failed - killing connection.");
                    connection.kill(new Callback<Void>() {
                        @Override
                        public void onSuccess(Void value) {
                            Log.d(TAG_COM, "killing connection successful. reconnecting ...");
                            connect();
                        }

                        @Override
                        public void onFailure(Throwable value) {
                            Log.d(TAG_COM, "failed to kill connection. reconnecting ...");
                            connect();
                        }
                    });
                }
            });
        } else {
            connect();
        }
    }


    @Override
    public void onCreate() {
        Log.d(TAG_COM, "on create");
        super.onCreate();
        mqtt = new MQTT();
        try {
            mqtt.setHost("iot.soft.uni-linz.ac.at", 1883);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        listeners = new HashSet<CommunicationListener>(1);
        topics = new HashSet<String>(1);
    }

    private class ListenerUpdateTask extends AsyncTask<Buffer, Void, Void> {
        @Override
        protected Void doInBackground(Buffer... params) {
            for (CommunicationListener listener : listeners) {
                listener.messageReceived(((UTF8Buffer) params[0]).toString(), params[1]);
            }
            return null;
        }
    }

    ;

    private void setupListener() {
        if(connection==null){
            connection=mqtt.callbackConnection();
            connection.listener(this);
        }
    }
    /** Listener.class on */

    @Override
    public void onDisconnected() {
        Log.d(TAG_COM, "disconnect from " + mqtt.getHost());
        connected=false;
    }

    @Override
    public void onConnected() {
        Log.d(TAG_COM, "connected to " + mqtt.getHost());
        connected=true;
    }

    @Override
    public void onPublish(final UTF8Buffer topic, final Buffer payload, Runnable ack) {
        Log.d(TAG_COM, "processing new message");
        //TODO: check why original implementation doesn't work
        //new ListenerUpdateTask().execute(topic, payload);
        for (CommunicationListener listener : listeners) {
            listener.messageReceived(((UTF8Buffer) topic).toString(), payload);
        }
        ack.run();
    }

    @Override
    public void onFailure(Throwable value) {
        Log.d(TAG_COM, "got failure " + value.getMessage() + " with connection to " + mqtt.getHost());
        reconnect();
    }
    /** Listener.class off */

    private void setupConnection() {
        setupConnection(false);
    }

    private void setupConnection(final boolean subscribe) {
        connection.connect(new Callback<Void>() {
            public void onFailure(Throwable value) {
                Log.d(TAG_COM, "unable to connect to " + mqtt.getHost());
                connection.failure();
            }

            public void onSuccess(Void v) {
                Log.d(TAG_COM, "connect to " + mqtt.getHost());
                if (subscribe) {
                    setupSubscriptionToTopics();
                }
            }
        });
    }

    private void setupSubscriptionToTopics() {
        final Topic[] topicList = new Topic[topics.size()];
        int i = 0;
        for (String strTopic : topics) {
            topicList[i] = new Topic(strTopic, QoS.AT_LEAST_ONCE);
            i++;
        }
        if(topicList.length>=1) {
            connection.subscribe(topicList, new Callback<byte[]>() {
                public void onSuccess(byte[] qoses) {
                    Log.d(TAG_COM, "subscriptions to topics successful " + Arrays.toString(topics.toArray()));
                }

                public void onFailure(Throwable value) {
                    Log.d(TAG_COM, "subscriptions to topics failed");
                    connection.disconnect(null);
                }
            });
        } else {
            Log.d(TAG_COM, "subscriptions to topics ("+ topics.size()+"/"+topicList.length+ ") unnecessary");
        }
    }

    private void subscribeToTopic(final String topic) {
        connection.subscribe(new Topic[]{new Topic(topic, QoS.AT_LEAST_ONCE)}, new Callback<byte[]>() {
            public void onSuccess(byte[] qoses) {
                Log.d(TAG_COM, "subscription to topic " + topic + " successful");
            }

            public void onFailure(Throwable value) {
                Log.d(TAG_COM, "subscription to topic  " + topic + " failed");
                connection.disconnect(null);
            }
        });
    }

    private void unsubscribeToTopic(final String topic) {
        connection.unsubscribe(new UTF8Buffer[]{new UTF8Buffer(topic)}, new Callback<Void>() {
            public void onSuccess(Void v) {
                Log.d(TAG_COM, "unsubscribe to topic " + topic + " succeded");
            }

            public void onFailure(Throwable value) {
                Log.d(TAG_COM, "unsubscribe to topic  " + topic + " failed");
                connection.disconnect(null);
            }
        });
    }

    private void connect() {
        connection = null;
        setupListener();
        setupConnection(true);
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
