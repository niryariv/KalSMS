package org.envaya.sms;

import org.envaya.sms.service.AmqpConsumerService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.ShutdownSignalException;
import org.envaya.sms.receiver.StartAmqpConsumer;
import org.envaya.sms.service.AmqpHeartbeatService;
import org.envaya.sms.task.HttpTask;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

public class AmqpConsumer {

    public static final long RETRY_DELAY = 5000; // ms
    public static final long RETRY_ERROR_DELAY = 60000; // ms
    public static final long MIN_EXPECTED_ERROR_DELAY = 10000; // ms            

    public static final int WIFI_MODE_FULL_HIGH_PERF = 3; 
        // constant not added until Android 3.1 but seems to work on older versions
        // (at least 2.3)
    
    protected Channel channel = null;
    protected Connection connection;
    
    protected App app;
    
    protected ConsumeThread consumeThread;
    
    protected WifiManager.WifiLock wifiLock;
    
    protected WifiManager wifiManager;
    protected AlarmManager alarmManager;
    //protected PowerManager powerManager;
    
    public AmqpConsumer(App app)
    {
        this.app = app;        
        wifiManager = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);                    
        alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        //powerManager = (PowerManager) app.getSystemService(Context.POWER_SERVICE);        
    }    

    // 'async' and 'delayed' methods must not be synchronized or app will deadlock ---
    // StartAmqpConsumerService thread owns lock on AmqpConsumer and needs lock on App to call app.log,
    // while main thread in onConnectivityChanged owns lock on App and must not take any locks on AmqpConsumer        
    public void startAsync()
    {
        startStopAsync(true);
    }
    
    public void stopAsync()
    {
        cancelStartDelayed();
        startStopAsync(false);
    }
    
    public void startStopAsync(boolean start)
    {
        Intent intent = new Intent(app, AmqpConsumerService.class);
        intent.putExtra("start", start);        
        app.startService(intent);   
    }
        
    public void cancelStartDelayed()
    {
        alarmManager.cancel(getStartPendingIntent());
    }
    
    public PendingIntent getStartPendingIntent()
    {
        return PendingIntent.getBroadcast(app,
                0,
                new Intent(app, StartAmqpConsumer.class),
                0);
    }
    
    public void startDelayed(long delay)
    {        
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delay,
            getStartPendingIntent()
        );
    }            
    
    public synchronized void startBlocking()
    {
        stopBlocking();
        
        boolean enabled = app.tryGetBooleanSetting("amqp_enabled", false);
        if (!enabled)
        {
            app.log("Real-time connection disabled");
            return;
        }
        
        String host = app.tryGetStringSetting("amqp_host", null);
        int port = app.tryGetIntegerSetting("amqp_port", 0);
        boolean ssl = app.tryGetBooleanSetting("amqp_ssl", false);
        String vhost = app.tryGetStringSetting("amqp_vhost", null);        
        String username = app.tryGetStringSetting("amqp_user", null);
        String password = app.tryGetStringSetting("amqp_password", null);
        String queue = app.tryGetStringSetting("amqp_queue", null);                     
        
        if (host == null || port == 0 || username == null || password == null || queue == null || vhost == null)
        {
            app.log("Real-time connection not configured");
            return;
        }
        
        boolean started = tryStart(host, port, ssl, vhost, username, password, queue);
        if (!started)
        {
            startDelayed(RETRY_ERROR_DELAY);
        }
    }    
    
    private Runnable heartbeatRunnable;
    
    public synchronized void setHeartbeatRunnable(Runnable heartbeatRunnable)
    {
        this.heartbeatRunnable = heartbeatRunnable;
    }
    
    public synchronized void sendHeartbeatBlocking()
    {
        if (heartbeatRunnable != null)
        {
            heartbeatRunnable.run();
        }        
        else
        {
            app.log("no heartbeat runnable");
        }
    }

    public PendingIntent getHeartbeatPendingIntent()
    {
        return PendingIntent.getService(app,
            0,
            new Intent(app, AmqpHeartbeatService.class),
            0);
    }                    
    
    public class HeartbeatExecutor extends ScheduledThreadPoolExecutor
    {
        public HeartbeatExecutor()
        {
            super(1);
        }        

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) 
        {            
            long delayMs = TimeUnit.MILLISECONDS.convert(initialDelay, unit);
            long periodMs = TimeUnit.MILLISECONDS.convert(period, unit);
            
            setHeartbeatRunnable(command);
            
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs,
                periodMs,
                getHeartbeatPendingIntent());
            
            //app.log("scheduleAtFixedRate " + delayMs + ", " + periodMs);
            
            return new ScheduledFuture<Integer>()
            {
                public long getDelay(TimeUnit u2)
                {
                    return 0;
                }
                public int compareTo(Delayed d2)
                {
                    return 0;
                }
                public Integer get()
                {
                    return null;
                }
                public Integer get(long timeout, TimeUnit unit)
                {
                    return null;
                }
                public boolean cancel(boolean interrupt)
                {
                    // doesn't do anything -- alarm cancelled by stopBlocking
                    cancelled = true;
                    return true;
                }
                
                private boolean cancelled;
                
                public boolean isCancelled()
                {
                    return cancelled;
                }
                public boolean isDone()
                {
                    return cancelled;
                }
            };
        }
    }
        
    private synchronized boolean tryStart(String host, int port, boolean ssl, String vhost, String username, String password, String queue)
    {                
        app.log("Establishing real-time connection...");
                                  
        if (wifiManager.isWifiEnabled())
        {                
            if (wifiLock == null)
            {
                wifiLock = wifiManager.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, "telerivet-amqp");        
                wifiLock.setReferenceCounted(false);
            }
            
            wifiLock.acquire();
        }        
        
        try
        {                
            ConnectionFactory connectionFactory = new ConnectionFactory();
            
            connectionFactory.setHost(host);
            connectionFactory.setPort(port);
            connectionFactory.setVirtualHost(vhost);
            connectionFactory.setUsername(username);
            connectionFactory.setPassword(password);                        
            
            /*
            connectionFactory.setLogger(new IILogger() {
                public void log(String str)
                {
                    app.log(str);
                }
            });
            */
            connectionFactory.setHeartbeatExecutor(new HeartbeatExecutor());
            
            TrustManager[] trustManagers = null; // use built-in SSL certificate verification
            
            if (ssl)
            {
                /*
                // could customize SSL certificate verification
                trustManagers = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) 
                            throws CertificateException
                        {
                        }
                    }
                };
                */

                SSLContext c = SSLContext.getInstance("TLS");
                c.init(null, trustManagers, new SecureRandom());

                connectionFactory.useSslProtocol(c);
            }
            
            // Need to periodically check if connection is still working
            // to allow the client and server to detect broken connections 
            // after 2*heartbeat seconds
            
            // AMQP heartbeat interval has a big effect on battery usage on idle phones.
            // The CPU will wake up every heartbeat for 5 seconds.    
            // For the rest of the time, the phone stays in deep sleep (CPU off) and is automatically 
            // woken up whenever the server sends a packet. 
            connectionFactory.setRequestedHeartbeat(app.tryGetIntegerSetting("amqp_heartbeat", 300));

            connection = connectionFactory.newConnection();
            channel = connection.createChannel();                     

            channel.queueDeclare(queue, true, false, false, null);            
            channel.basicQos(1);

            QueueingConsumer consumer = new QueueingConsumer(channel);

            channel.basicConsume(queue, false, consumer);                    

            consumeThread = new ConsumeThread(consumer);
            consumeThread.start();
            
            HttpTask task = new HttpTask(app, 
                new BasicNameValuePair("action", App.ACTION_AMQP_STARTED),
                new BasicNameValuePair("consumer_tag", consumer.getConsumerTag())
            );
            task.execute();
                     
            return true;
        }
        catch (Exception ex)
        {
            app.logError("Error establishing real-time connection", ex);
            return false;
        }
    }
    
    public synchronized void stopBlocking() {
        
        try 
        {
            if (wifiLock != null && wifiLock.isHeld())
            {                
                wifiLock.release();
            }        
            alarmManager.cancel(getHeartbeatPendingIntent());                        
            heartbeatRunnable = null;            
            
            if (consumeThread != null)
            {
                consumeThread.terminate();
                consumeThread = null;
            }
            
            if (channel != null)
            {
                channel = null;
            }
            
            if (connection != null) 
            {
                try
                {
                    connection.close(10000);
                }
                catch (AlreadyClosedException ex) {}
                catch (ShutdownSignalException ex) {}
                
                connection = null;
            }
        } 
        catch (IOException ex) 
        {
            app.logError("Error stopping real-time connection", ex);
        }
    }
    
    public class ConsumeThread extends Thread
    {
        private QueueingConsumer consumer;
        private boolean terminated = false;
        
        public ConsumeThread(QueueingConsumer consumer)
        {
            this.consumer = consumer;
        }
        
        public synchronized void terminate()
        {
            this.terminated = true;
        }
            
        public synchronized boolean isTerminated()
        {
            return terminated;
        }
        
        public void processMessage(QueueingConsumer.Delivery delivery)
        {
            String jsonStr = new String(delivery.getBody());
            
            try
            {
                JSONObject json = new JSONObject(jsonStr);                
                JsonUtils.processEvent(json, app, null);
            }
            catch (JSONException ex)
            {
                app.logError(ex);
            }
        }

        @Override
        public void run() {
            long startTime = SystemClock.elapsedRealtime();
            
            try
            {
                app.log("Real-time connection established.");
                
                Channel ch = consumer.getChannel();

                while(true)
                {
                    QueueingConsumer.Delivery delivery = consumer.nextDelivery();

                    if (isTerminated())
                    {
                        break;
                    }

                    ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    
                    processMessage(delivery);                                       
                    
                    if (isTerminated())
                    {   
                        break;
                    }
                }
                app.log("Real-time connection stopped.");
            }
            catch (Exception ex)
            {
                if (!isTerminated())
                {
                    if (ex instanceof ShutdownSignalException)
                    {
                        //app.logError("Real-time connection interrupted", ex);
                        app.log("Real-time connection interrupted");
                    }
                    else
                    {                    
                        app.logError("Real-time connection interrupted", ex);
                    }
                    
                    long age = SystemClock.elapsedRealtime() - startTime;

                    stopAsync();
                    if (age < MIN_EXPECTED_ERROR_DELAY)
                    {
                        startDelayed(RETRY_ERROR_DELAY);
                    }
                    else
                    {
                        startDelayed(RETRY_DELAY);
                    }
                }
                else
                {
                    app.log("Real-time connection stopped.");
                }
            }
        }        
    }    
}