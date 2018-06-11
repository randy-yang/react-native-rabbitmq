package nl.kega.reactnativerabbitmq;

import android.util.Log;
import android.os.AsyncTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ShutdownListener; 
import com.rabbitmq.client.ShutdownSignalException; 
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoverableConnection;

class RabbitMqConnection extends ReactContextBaseJavaModule  {

    private ReactApplicationContext context;

    public ReadableMap config;

    private ConnectionFactory factory;
    private RecoverableConnection connection;
    private Channel channel;

    private Callback status;

    private ArrayList<RabbitMqQueue> queues = new ArrayList<RabbitMqQueue>();
    private ArrayList<RabbitMqExchange> exchanges = new ArrayList<RabbitMqExchange>(); 

    public RabbitMqConnection(ReactApplicationContext reactContext) {
        super(reactContext);

        this.context = reactContext;

    }

    @Override
    public String getName() {
        return "RabbitMqConnection";
    }

    @ReactMethod
    public void initialize(ReadableMap config) {
        this.config = config;

        SSLContext c = null;
        try {
            TrustManager[] trustManagers = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        // TODO Auto-generated method stub

                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        // TODO Auto-generated method stub

                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        // TODO Auto-generated method stub
                        return null;
                    }
                }
            };

            c = SSLContext.getInstance("TLSv1.1");
            c.init(null, trustManagers, null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        this.factory = new ConnectionFactory();
        this.factory.setUsername(this.config.getString("username"));
        this.factory.setPassword(this.config.getString("password"));
        this.factory.setVirtualHost(this.config.getString("virtualhost"));
        this.factory.setHost(this.config.getString("host"));
        this.factory.setPort(this.config.getInt("port"));
        this.factory.setAutomaticRecoveryEnabled(true);
        this.factory.setRequestedHeartbeat(10);
        this.factory.setConnectionTimeout(30000);
        if (this.config.getInt("port") == 5671) {
            this.factory.useSslProtocol(c);
        }

    }

    @ReactMethod
    public void status(Callback onStatus) {
        this.status = onStatus;
    }

    @ReactMethod
    public void connect() {
        // async execute to prevent UI freeze
        // connection 要使用非同步處理，不然畫面會凍結 (連很久連不上時)
        new ConnectionAsyncTask().execute();
    }

    @ReactMethod
    public void addQueue(ReadableMap queue_condig, ReadableMap arguments) {
        RabbitMqQueue queue = new RabbitMqQueue(this.context, this.channel, queue_condig, arguments);
        this.queues.add(queue);
    }

    @ReactMethod
    public void bindQueue(String exchange_name, String queue_name, String routing_key) {
        
        RabbitMqQueue found_queue = null;
        for (RabbitMqQueue queue : queues) {
		    if (Objects.equals(queue_name, queue.name)){
                found_queue = queue;
            }
        }

        RabbitMqExchange found_exchange = null;
        for (RabbitMqExchange exchange : exchanges) {
		    if (Objects.equals(exchange_name, exchange.name)){
                found_exchange = exchange;
            }
        }

        if (!found_queue.equals(null) && !found_exchange.equals(null)){
            found_queue.bind(found_exchange, routing_key);
        }
    }

    @ReactMethod
    public void unbindQueue(String exchange_name, String queue_name) {
        
        RabbitMqQueue found_queue = null;
        for (RabbitMqQueue queue : queues) {
		    if (Objects.equals(queue_name, queue.name)){
                found_queue = queue;
            }
        }

        RabbitMqExchange found_exchange = null;
        for (RabbitMqExchange exchange : exchanges) {
		    if (Objects.equals(exchange_name, exchange.name)){
                found_exchange = exchange;
            }
        }

        if (!found_queue.equals(null) && !found_exchange.equals(null)){
            found_queue.unbind();
        }
    }

    @ReactMethod
    public void removeQueue(String queue_name) {
        RabbitMqQueue found_queue = null;
        for (RabbitMqQueue queue : queues) {
		    if (Objects.equals(queue_name, queue.name)){
                found_queue = queue;
            }
        }

        if (!found_queue.equals(null)){
            found_queue.delete();
        }
    }

    /*
    @ReactMethod
    public void publishToQueue(String message, String exchange_name, String routing_key) {

        for (RabbitMqQueue queue : queues) {
		    if (Objects.equals(exchange_name, queue.exchange_name)){
                Log.e("RabbitMqConnection", "publish " + message);
                queue.publish(message, exchange_name);
                return;
            }
		}

    }
    */

    @ReactMethod
    public void addExchange(ReadableMap exchange_condig) {

        RabbitMqExchange exchange = new RabbitMqExchange(this.context, this.channel, exchange_condig);

        this.exchanges.add(exchange);
    }

    @ReactMethod
    public void publishToExchange(String message, String exchange_name, String routing_key, ReadableMap message_properties) {

         for (RabbitMqExchange exchange : exchanges) {
		    if (Objects.equals(exchange_name, exchange.name)){
                Log.i("RabbitMqConnection", "Exchange publish: " + message);
                exchange.publish(message, routing_key, message_properties);
                return;
            }
		}

    }

    @ReactMethod
    public void deleteExchange(String exchange_name, Boolean if_unused) {

        for (RabbitMqExchange exchange : exchanges) {
		    if (Objects.equals(exchange_name, exchange.name)){
                exchange.delete(if_unused);
                return;
            }
		}

    }

    @ReactMethod
    public void close() {
        try {
            if (this.queues != null && this.queues.size() > 0) {
                for (RabbitMqQueue qu : this.queues) {
                    qu.delete();
                }
            }

            if (this.exchanges != null && this.exchanges.size() > 0) {
                for (RabbitMqExchange ex : this.exchanges) {
                    ex.delete(false);
                }
            }

            this.queues = new ArrayList<RabbitMqQueue>();
            this.exchanges = new ArrayList<RabbitMqExchange>(); 

            if (this.channel != null) {
                this.channel.close();
            }

            if (this.connection != null) {
                this.connection.close();
            }
         } catch (Exception e){
            Log.e("RabbitMqConnection", "Connection closing error " + e);
            e.printStackTrace();
        } finally { 
            this.connection = null; 
            this.factory = null;
            this.channel = null;
        } 
    }

    private void onClose(ShutdownSignalException cause) {
        Log.i("RabbitMqConnection", "Closed");

        WritableMap event = Arguments.createMap();
        event.putString("name", "closed");

        this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RabbitMqConnectionEvent", event);
    } 

    private void onRecovered() {
        Log.i("RabbitMqConnection", "Recovered");

        WritableMap event = Arguments.createMap();
        event.putString("name", "reconnected");

        this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RabbitMqConnectionEvent", event);
    }

    private class ConnectionAsyncTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... arg) {
            if (connection != null && connection.isOpen()){
                WritableMap event = Arguments.createMap();
                event.putString("name", "connected");

                context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RabbitMqConnectionEvent", event);
            } else {

                try {
                    connection = (RecoverableConnection)factory.newConnection();
                } catch (Exception e) {

                    WritableMap event = Arguments.createMap();
                    event.putString("name", "error");
                    event.putString("type", "failedtoconnect");
                    event.putString("code", "");
                    event.putString("description", e.getMessage());

                    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RabbitMqConnectionEvent", event);

                    connection = null;

                }

                if (connection != null) {

                    try {

                        connection.addShutdownListener(new ShutdownListener() {
                            @Override
                            public void shutdownCompleted(ShutdownSignalException cause) {
                                Log.i("RabbitMqConnection", "Shutdown signal received " + cause);
                                onClose(cause);
                            }
                        });


                        connection.addRecoveryListener(new RecoveryListener() {

                            @Override
                            public void handleRecoveryStarted(Recoverable recoverable) {
                                Log.i("RabbitMqConnection", "RecoveryStarted " + recoverable);
                            }

                            @Override
                            public void handleRecovery(Recoverable recoverable) {
                                Log.i("RabbitMqConnection", "Recoverable " + recoverable);
                                onRecovered();
                            }

                        });


                        channel = connection.createChannel();
                        channel.basicQos(1);

                        WritableMap event = Arguments.createMap();
                        event.putString("name", "connected");

                        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RabbitMqConnectionEvent", event);

                    } catch (Exception e) {
                        Log.e("RabbitMqConnectionChannel", "Create channel error " + e);
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress)
        {
            // 這裡接收傳入的 progress 值, 並更新進度表畫面
            // 參數是 Integer 型態的陣列
        }
    }
}