package org.envaya.sms;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Log;
import java.io.IOException;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.envaya.sms.receiver.OutgoingMessagePoller;
import org.envaya.sms.receiver.ReenableWifiReceiver;
import org.envaya.sms.task.HttpTask;
import org.envaya.sms.task.PollerTask;
import org.json.JSONArray;
import org.json.JSONException;

public final class App extends Application {
    
    public static final String ACTION_OUTGOING = "outgoing";
    public static final String ACTION_INCOMING = "incoming";
    public static final String ACTION_SEND_STATUS = "send_status";
    public static final String ACTION_DEVICE_STATUS = "device_status";
    public static final String ACTION_TEST = "test";
    
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SENT = "sent";
    
    public static final String DEVICE_STATUS_POWER_CONNECTED = "power_connected";
    public static final String DEVICE_STATUS_POWER_DISCONNECTED = "power_disconnected";
    public static final String DEVICE_STATUS_BATTERY_LOW = "battery_low";
    public static final String DEVICE_STATUS_BATTERY_OKAY = "battery_okay";
    
    public static final String MESSAGE_TYPE_MMS = "mms";
    public static final String MESSAGE_TYPE_SMS = "sms";
    
    public static final String LOG_NAME = "EnvayaSMS";
    
    // intent to signal to Main activity (if open) that log has changed
    public static final String LOG_CHANGED_INTENT = "org.envaya.sms.LOG_CHANGED";

    // signal to PendingMessages activity (if open) that inbox/outbox has changed
    public static final String INBOX_CHANGED_INTENT = "org.envaya.sms.INBOX_CHANGED";
    public static final String OUTBOX_CHANGED_INTENT = "org.envaya.sms.OUTBOX_CHANGED";
                
    public static final String QUERY_EXPANSION_PACKS_INTENT = "org.envaya.sms.QUERY_EXPANSION_PACKS";
    public static final String QUERY_EXPANSION_PACKS_EXTRA_PACKAGES = "packages";
    
    // Interface for sending outgoing messages to expansion packs
    public static final String OUTGOING_SMS_INTENT_SUFFIX = ".OUTGOING_SMS";    
    public static final String OUTGOING_SMS_EXTRA_TO = "to";
    public static final String OUTGOING_SMS_EXTRA_BODY = "body";    
    public static final String OUTGOING_SMS_EXTRA_DELIVERY_REPORT = "delivery";
    public static final int OUTGOING_SMS_UNHANDLED = Activity.RESULT_FIRST_USER;
    
    // intent for MessageStatusNotifier to receive status updates for outgoing SMS
    // (even if sent by an expansion pack)
    public static final String MESSAGE_STATUS_INTENT = "org.envaya.sms.MESSAGE_STATUS";
    public static final String MESSAGE_DELIVERY_INTENT = "org.envaya.sms.MESSAGE_DELIVERY";    
    
    public static final String STATUS_EXTRA_INDEX = "status";
    public static final String STATUS_EXTRA_NUM_PARTS = "num_parts";            
    
    public static final int MAX_DISPLAYED_LOG = 8000;
    public static final int LOG_TIMESTAMP_INTERVAL = 60000; // ms
    
    public static final int HTTP_CONNECTION_TIMEOUT = 10000; // ms
    
    // Each QueuedMessage is identified within our internal Map by its Uri.
    // Currently QueuedMessage instances are only available within EnvayaSMS,
    // (but they could be made available to other applications later via a ContentProvider)
    public static final Uri CONTENT_URI = Uri.parse("content://org.envaya.sms");
    public static final Uri INCOMING_URI = Uri.withAppendedPath(CONTENT_URI, "incoming");
    public static final Uri OUTGOING_URI = Uri.withAppendedPath(CONTENT_URI, "outgoing");
    
    // how long we disable wifi when there is no connection to the server
    // (should be longer than CONNECTIVITY_FAILOVER_INTERVAL)
    public static final int DISABLE_WIFI_INTERVAL = 3600000; 
    
    // how often we can automatically failover between wifi/mobile connection
    public static final int CONNECTIVITY_FAILOVER_INTERVAL = 1800000;
    
    // max per-app outgoing SMS rate used by com.android.internal.telephony.SMSDispatcher
    // with a slightly longer check period to account for variance in the time difference
    // between when we prepare messages and when SMSDispatcher receives them
    public static final int OUTGOING_SMS_CHECK_PERIOD = 3605000; // one hour plus 5 sec (in ms)    
    public static final int OUTGOING_SMS_MAX_COUNT = 100;
        
    public final Inbox inbox = new Inbox(this);
    public final Outbox outbox = new Outbox(this);    
    
    public final Queue<HttpTask> queuedTasks = new LinkedList<HttpTask>();
    
    private SharedPreferences settings;
    private MmsObserver mmsObserver;
    private SpannableStringBuilder displayedLog = new SpannableStringBuilder();
    private long lastLogTime;    
    
    private PackageInfo packageInfo;
    
    // list of package names (e.g. org.envaya.sms, or org.envaya.sms.packXX)
    // for this package and all expansion packs
    private List<String> outgoingMessagePackages = new ArrayList<String>();
                
    // map of package name => sorted list of timestamps of outgoing messages
    private HashMap<String, ArrayList<Long>> outgoingTimestamps
            = new HashMap<String, ArrayList<Long>>();
    
    // count to provide round-robin selection of expansion packs
    private int outgoingMessageCount = -1;
    
    private MmsUtils mmsUtils;
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        
        settings = PreferenceManager.getDefaultSharedPreferences(this);        
        mmsUtils = new MmsUtils(this);
        
        outgoingMessagePackages.add(getPackageName());
        
        mmsObserver = new MmsObserver(this);
        
        try
        {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        }
        catch (NameNotFoundException ex)
        {
            // should not happen
            logError("Error finding package info", ex);
            return;
        }              
        
        updateExpansionPacks();
        
        log(Html.fromHtml(
            isEnabled() ? "<b>SMS gateway running.</b>" : "<b>SMS gateway disabled.</b>"));

        log("Server URL is: " + getDisplayString(getServerUrl()));
        log("Your phone number is: " + getDisplayString(getPhoneNumber()));        
                
        if (isTestMode())
        {
            log("Test mode is ON");
            log("Test phone numbers:");
            
            for (String sender : getTestPhoneNumbers())
            {
                log("  " + sender);
            }
        }                          
                
        enabledChanged();
        
        log(Html.fromHtml("<b>Press Menu to edit settings.</b>"));
    }   
    
    public void enabledChanged()
    {        
        if (isEnabled())
        {
            mmsObserver.register();   
        }
        else
        {
            mmsObserver.unregister();
        }        
        
        setOutgoingMessageAlarm();
        startService(new Intent(this, ForegroundService.class));        
    }    
    
    public PackageInfo getPackageInfo()
    {
        return packageInfo;
    }
    
    public synchronized String chooseOutgoingSmsPackage(int numParts)
    {
        outgoingMessageCount++;
        
        int numPackages = outgoingMessagePackages.size();
        
        // round robin selection of packages that are under max sending rate
        for (int i = 0; i < numPackages; i++)
        {
            int packageIndex = (outgoingMessageCount + i) % numPackages;           
            String packageName = outgoingMessagePackages.get(packageIndex);
            
            // implement rate-limiting algorithm from
            // com.android.internal.telephony.SMSDispatcher.SmsCounter
            
            if (!outgoingTimestamps.containsKey(packageName)) {
                outgoingTimestamps.put(packageName, new ArrayList<Long>());
            }
            
            ArrayList<Long> sent = outgoingTimestamps.get(packageName);        
            
            Long ct = System.currentTimeMillis();

            //log(packageName + " SMS send size=" + sent.size());

            // remove old timestamps
            while (sent.size() > 0 && (ct - sent.get(0)) > OUTGOING_SMS_CHECK_PERIOD ) 
            {
                sent.remove(0);
            }

            if ( (sent.size() + numParts) <= OUTGOING_SMS_MAX_COUNT)
            {
                // each part counts towards message limit
                for (int j = 0; j < numParts; j++)
                {
                    sent.add(ct);
                }
                return packageName;
            }
        }
        
        log("Can't send outgoing SMS: maximum limit of "
            + getOutgoingMessageLimit() + " in 1 hour reached");
        log("To increase this limit, install an expansion pack.");
                
        return null;
    }    

    /*
     * Returns the next time (in currentTimeMillis) that we can send an 
     * outgoing SMS with numParts parts. Only valid immediately after 
     * chooseOutgoingSmsPackage returns null.
     */
    public synchronized long getNextValidOutgoingTime(int numParts)
    {       
        long minTime = System.currentTimeMillis() + OUTGOING_SMS_CHECK_PERIOD;
        
        for (String packageName : outgoingMessagePackages)
        {
            ArrayList<Long> timestamps = outgoingTimestamps.get(packageName);            
            if (timestamps == null) // should never happen
            {
                continue;
            }
            
            int numTimestamps = timestamps.size();
            
            // get 100th-to-last timestamp for 1 part msg, 
            // 99th-to-last timestamp for 2 part msg, etc.
            
            int timestampIndex = numTimestamps - 1 - OUTGOING_SMS_MAX_COUNT + numParts;            
                        
            if (timestampIndex < 0 || timestampIndex >= numTimestamps) 
            {
                // should never happen 
                // (unless someone tries to send a 101-part SMS message)
                continue;
            }
            
            long minTimeForPackage = timestamps.get(timestampIndex) + OUTGOING_SMS_CHECK_PERIOD;
            if (minTimeForPackage < minTime)
            {
                minTime = minTimeForPackage;
            }            
        }
        
        // return time immediately after limiting timestamp
        return minTime + 1;
    }
    
    private synchronized void setExpansionPacks(List<String> packages)
    {
        int prevLimit = getOutgoingMessageLimit();
        
        if (packages == null)
        {
            packages = new ArrayList<String>();
        }
        
        packages.add(getPackageName());        
        
        outgoingMessagePackages = packages;        
        
        int newLimit = getOutgoingMessageLimit();
        
        if (prevLimit != newLimit)
        {        
            log("Outgoing SMS limit: " + newLimit + " messages/hour");
        }
    }
    
    public int getOutgoingMessageLimit()
    {
        return outgoingMessagePackages.size() * OUTGOING_SMS_MAX_COUNT;
    }
    
    public void updateExpansionPacks()
    {
        ArrayList<String> packages = new ArrayList<String>();
        Bundle extras = new Bundle();
        extras.putStringArrayList(App.QUERY_EXPANSION_PACKS_EXTRA_PACKAGES, packages);
        
        sendOrderedBroadcast(
                new Intent(App.QUERY_EXPANSION_PACKS_INTENT), 
                "android.permission.SEND_SMS",
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent resultIntent) {
                        
                        setExpansionPacks(this.getResultExtras(false)
                            .getStringArrayList(App.QUERY_EXPANSION_PACKS_EXTRA_PACKAGES));
                        
                    }
                }, 
                null, 
                Activity.RESULT_OK,
                null, 
                extras);
    }
    
    public void checkOutgoingMessages() 
    {
        String serverUrl = getServerUrl();
        if (serverUrl.length() > 0) {
            log("Checking for outgoing messages");
            new PollerTask(this).execute();
        } else {
            log("Can't check outgoing messages; server URL not set");
        }
    }

    public void setOutgoingMessageAlarm() {
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this,
                0,
                new Intent(this, OutgoingMessagePoller.class),
                0);

        alarm.cancel(pendingIntent);

        int pollSeconds = getOutgoingPollSeconds();

        if (isEnabled())
        {        
            if (pollSeconds > 0) {
                alarm.setRepeating(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime(),
                        pollSeconds * 1000,
                        pendingIntent);
                log("Checking for outgoing messages every " + pollSeconds + " sec");
            } else {
                log("Not checking for outgoing messages.");
            }
        }
    }

    public String getDisplayString(String str) {
        if (str.length() == 0) {
            return "(not set)";
        } else {
            return str;
        }
    }

    public String getServerUrl() {
        return settings.getString("server_url", "");
    }

    public String getPhoneNumber() {
        return settings.getString("phone_number", "");
    }

    public int getOutgoingPollSeconds() {
        return Integer.parseInt(settings.getString("outgoing_interval", "0"));
    }

    public boolean isEnabled()
    {
        return settings.getBoolean("enabled", false);
    }
    
    public boolean isNetworkFailoverEnabled()
    {
        return settings.getBoolean("network_failover", false);
    }
    
    public boolean isTestMode()
    {
        return settings.getBoolean("test_mode", false);
    }
    
    public boolean getKeepInInbox() 
    {
        return settings.getBoolean("keep_in_inbox", false);        
    }
    
    public boolean ignoreShortcodes()
    {
        return settings.getBoolean("ignore_shortcodes", true);
    }
    
    public boolean ignoreNonNumeric()
    {
        return settings.getBoolean("ignore_non_numeric", true);
    }

    public String getPassword() {
        return settings.getString("password", "");
    }

    public synchronized void retryStuckMessages() {        
        outbox.retryAll();
        inbox.retryAll();
        retryQueuedTasks();
    }

    public synchronized int getPendingTaskCount() {        
        return outbox.size() + inbox.size() + queuedTasks.size();
    }
    
    public void debug(String msg) {
        Log.d(LOG_NAME, msg);
    }

    private int logEpoch = 0;
    
    private StringBuilder newLogBuffer = new StringBuilder();
    
    public synchronized String getNewLogEntries()
    {
        String res = newLogBuffer.toString();
        newLogBuffer.setLength(0);
        return res;
    }
    
    // clients may sometimes unget log entries out of order,
    // but most of the time this will be the right order
    public synchronized void ungetNewLogEntries(String logEntries)
    {
        newLogBuffer.insert(0, logEntries);
    }
    
    public synchronized void log(CharSequence msg) 
    {        
        Log.d(LOG_NAME, msg.toString());       
                                 
        // prevent displayed log from growing too big
        int length = displayedLog.length();
        if (length > MAX_DISPLAYED_LOG)
        {
            int startPos = length - MAX_DISPLAYED_LOG * 3 / 4;
            
            for (int cur = startPos; cur < startPos + 100 && cur < length; cur++)
            {
                if (displayedLog.charAt(cur) == '\n')
                {
                    startPos = cur;
                    break;
                }
            }

            displayedLog.replace(0, startPos, "[Older log messages not shown]\n");
            logEpoch++;
        }

        int prevLength = displayedLog.length();
        
        // display a timestamp in the log occasionally        
        long logTime = SystemClock.elapsedRealtime();        
        if (logTime - lastLogTime > LOG_TIMESTAMP_INTERVAL)
        {
            Date date = new Date();
            displayedLog.append("[" + DateFormat.getTimeInstance().format(date) + "]\n");                
            lastLogTime = logTime;
        }
        
        displayedLog.append(msg);
        displayedLog.append("\n");  
                
        newLogBuffer.append(displayedLog, prevLength, displayedLog.length());
    
        sendBroadcast(new Intent(App.LOG_CHANGED_INTENT));
    }

    /*
     * Changes whenever we change the beginning of the displayed log.
     * If it doesn't change, the Main activity can update the log view much
     * faster by using TextView.append() instead of TextView.setText()
     */
    public int getLogEpoch()
    {
        return logEpoch;
    }
    
    public synchronized CharSequence getDisplayedLog()
    {
        return displayedLog;
    }
    
    public void logError(Throwable ex) {
        logError("ERROR", ex);
    }

    public void logError(String msg, Throwable ex) {
        logError(msg, ex, false);
    }

    public void logError(String msg, Throwable ex, boolean detail) {
        log(msg + ": " + ex.getClass().getName() + ": " + ex.getMessage());

        if (detail) {
            for (StackTraceElement elem : ex.getStackTrace()) {
                log(elem.getClassName() + ":" + elem.getMethodName() + ":" + elem.getLineNumber());
            }
            Throwable innerEx = ex.getCause();
            if (innerEx != null) {
                logError("Inner exception:", innerEx, true);
            }
        }
    }    
    
    public MmsUtils getMmsUtils()
    {
        return mmsUtils;
    }
    
    private List<String> testPhoneNumbers;    
    public synchronized List<String> getTestPhoneNumbers()
    {
        if (testPhoneNumbers == null)
        {          
            testPhoneNumbers = loadStringListSetting("test_phone_numbers");
        }
        return testPhoneNumbers;
    }
    
    public synchronized void addTestPhoneNumber(String phoneNumber)
    {
        List<String> phoneNumbers = getTestPhoneNumbers();
        log("Added test phone number: " + phoneNumber);
        phoneNumbers.add(phoneNumber);
        saveStringListSetting("test_phone_numbers", phoneNumbers);
    }
    
    public synchronized void removeTestPhoneNumber(String phoneNumber)
    {
        List<String> phoneNumbers = getTestPhoneNumbers();
        phoneNumbers.remove(phoneNumber);
        log("Removed test phone number: " + phoneNumber);
        saveStringListSetting("test_phone_numbers", phoneNumbers);
    }
    
    private List<String> ignoredPhoneNumbers;    
    public synchronized List<String> getIgnoredPhoneNumbers()
    {
        if (ignoredPhoneNumbers == null)
        {          
            ignoredPhoneNumbers = loadStringListSetting("ignored_phone_numbers");
        }
        return ignoredPhoneNumbers;
    }
    
    public synchronized void addIgnoredPhoneNumber(String phoneNumber)
    {
        List<String> phoneNumbers = getIgnoredPhoneNumbers();
        log("Added ignored phone number: " + phoneNumber);
        phoneNumbers.add(phoneNumber);
        saveStringListSetting("ignored_phone_numbers", phoneNumbers);
    }
    
    public synchronized void removeIgnoredPhoneNumber(String phoneNumber)
    {
        List<String> phoneNumbers = getIgnoredPhoneNumbers();
        phoneNumbers.remove(phoneNumber);
        log("Removed ignored phone number: " + phoneNumber);
        saveStringListSetting("ignored_phone_numbers", phoneNumbers);
    }    
    
    public synchronized  void saveStringListSetting(String key, List<String> values)
    {
        settings.edit().putString(key, 
            new JSONArray(values).toString()
        ).commit();
    }    
    
    public synchronized void saveBooleanSetting(String key, boolean value)
    {
        settings.edit().putBoolean(key, value).commit();
    }    
    
    private List<String> loadStringListSetting(String key)
    {
        List<String> values = new ArrayList<String>();
        String valuesJson = settings.getString(key, "");

        if (valuesJson.length() > 0)
        {
            try
            {                                
                JSONArray arr = new JSONArray(valuesJson);
                int numSenders = arr.length();
                for (int i = 0; i < numSenders; i++)
                {                    
                    values.add(arr.getString(i));                    
                }
            }
            catch (JSONException ex)
            {
                logError("Error parsing setting " + key, ex);
            }            
        }        
        return values;
    }
    
    public boolean isForwardablePhoneNumber(String phoneNumber)
    {
        if (isTestMode())
        {
            return getTestPhoneNumbers().contains(phoneNumber);
        }        
        
        if (getIgnoredPhoneNumbers().contains(phoneNumber))
        {
            return false;
        }
        
        int numDigits = 0;
        int length = phoneNumber.length();

        for (int i = 0; i < length; i++)
        {
            if (Character.isDigit(phoneNumber.charAt(i)))
            {
                numDigits++;
            }
        }        
        
        if (numDigits == 0)
        {
            if (ignoreNonNumeric())
            {
                return false;
            }
        }
        else if (numDigits < 7)
        {
            if (ignoreShortcodes())
            {
                return false;
            }
        }

        return true;
    }
    
    private HttpClient httpClient;
    
    public HttpParams getDefaultHttpParams()
    {
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, HTTP_CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpParams, HTTP_CONNECTION_TIMEOUT);                    
        HttpProtocolParams.setContentCharset(httpParams, "UTF-8");            
        return httpParams;
    }
    
    public synchronized HttpClient getHttpClient()
    {
        if (httpClient == null)
        {
            // via http://thinkandroid.wordpress.com/2009/12/31/creating-an-http-client-example/
            // also http://hc.apache.org/httpclient-3.x/threading.html
            
            HttpParams httpParams = getDefaultHttpParams();            
            
            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            
            final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();            
            sslSocketFactory.setHostnameVerifier(SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
            
            registry.register(new Scheme("https", sslSocketFactory, 443));

            ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(httpParams, registry);            
            
            httpClient = new DefaultHttpClient(manager, httpParams);        
        }
        return httpClient;
    }      
    
    private class ConnectivityCheckState
    {
        //private int networkType;
        private long lastCheckTime;  // when we checked connectivity on this network
        
        public ConnectivityCheckState(int networkType)
        {
            //this.networkType = networkType;
        }
        
        public synchronized boolean canCheck()
        {
            long time = SystemClock.elapsedRealtime();                
            return (time - lastCheckTime >= App.CONNECTIVITY_FAILOVER_INTERVAL);
        }                    
        
        public void setChecked()
        {
            lastCheckTime = SystemClock.elapsedRealtime();
        }
    }
    
    private Map<Integer,ConnectivityCheckState> connectivityCheckStates
        = new HashMap<Integer, ConnectivityCheckState>();
        
    private Thread connectivityThread;    
        
    /*
     * Normally we rely on Android to automatically switch between 
     * mobile data and Wi-Fi, but if the phone is connected to a Wi-Fi router
     * that doesn't have a connection to the internet, Android won't know 
     * the difference. So we if we can't actually reach the remote host via
     * the current connection, we toggle the Wi-Fi radio so that Android
     * will switch to the other connection. 
     * 
     * If the host is unreachable on both connections, we don't want to
     * keep toggling the radio forever, so there is a timeout before we can 
     * recheck connectivity on a particular connection.
     * 
     * When we disable the Wi-Fi radio, we set a timeout to reenable it after
     * a while in hopes that connectivity will be restored.
     */
    public synchronized void asyncCheckConnectivity()
    {          
        ConnectivityManager cm = 
            (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();                

        if (activeNetwork == null || !activeNetwork.isConnected())
        {
            WifiManager wmgr = (WifiManager)getSystemService(Context.WIFI_SERVICE);            

            if (activeNetwork != null)
            {
                log(activeNetwork.getTypeName() + "=" + activeNetwork.getState());
            }
            else
            {
                log("Not connected to any network.");   
            }
            
            if (!wmgr.isWifiEnabled() && isNetworkFailoverEnabled())
            {
                log("Enabling WIFI...");
                wmgr.setWifiEnabled(true);
            }

            return;
        }
        
        final int networkType = activeNetwork.getType();                
        
        ConnectivityCheckState state = 
            connectivityCheckStates.get(networkType);
        
        if (state == null)
        {
            state = new ConnectivityCheckState(networkType);
            connectivityCheckStates.put(networkType, state);
        }

        if (!state.canCheck()
            || (connectivityThread != null && connectivityThread.isAlive()))
        {
            return;
        }
        
        state.setChecked();
        
        connectivityThread = new Thread() {
            @Override
            public void run()
            {
                Uri serverUrl = Uri.parse(getServerUrl());
                String hostName = serverUrl.getHost();

                log("Checking connectivity to "+hostName+"...");

                try
                {
                    InetAddress addr = InetAddress.getByName(hostName);
                    if (addr.isReachable(App.HTTP_CONNECTION_TIMEOUT))
                    {
                        log("OK");                        
                        onConnectivityRestored();
                        return;
                    }
                }
                catch (IOException ex)
                {
                    // just what we suspected... 
                    // server not reachable on this interface
                }
                
                log("Can't connect to "+hostName+".");
                
                WifiManager wmgr = (WifiManager)getSystemService(Context.WIFI_SERVICE);
                
                if (!isNetworkFailoverEnabled())
                {
                    log("Network failover disabled.");
                }
                else if (networkType == ConnectivityManager.TYPE_WIFI)
                {
                    log("Switching from WIFI to MOBILE");                

                    PendingIntent pendingIntent = PendingIntent.getBroadcast(App.this,
                        0,
                        new Intent(App.this, ReenableWifiReceiver.class),
                        0);

                    // set an alarm to try restoring Wi-Fi in a little while
                    AlarmManager alarm = 
                        (AlarmManager)getSystemService(Context.ALARM_SERVICE);

                    alarm.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,                        
                        SystemClock.elapsedRealtime() + App.DISABLE_WIFI_INTERVAL,
                        pendingIntent);   

                    wmgr.setWifiEnabled(false);
                }
                else if (networkType == ConnectivityManager.TYPE_MOBILE 
                        && !wmgr.isWifiEnabled())
                {
                    log("Switching from MOBILE to WIFI");
                    wmgr.setWifiEnabled(true);                    
                }
                else
                {
                    log("Can't automatically fix connectivity.");
                }     
            }
        };
        connectivityThread.start();
    }
    
    private int activeNetworkType = -1;
    
    public synchronized void onConnectivityChanged()
    {
        ConnectivityManager cm = 
            (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        
        if (networkInfo == null || !networkInfo.isConnected())
        {
            return;
        }
        
        int networkType = networkInfo.getType();
        
        if (networkType == activeNetworkType)
        {
            return;
        }
        
        activeNetworkType = networkType;        
        log("Connected to " + networkInfo.getTypeName());        
        asyncCheckConnectivity();
    }
    
    private void onConnectivityRestored()
    {
        inbox.retryAll();
        
        if (getOutgoingPollSeconds() > 0)
        {
            checkOutgoingMessages();
        }
        
        retryQueuedTasks();
    }    
    
    public synchronized void retryQueuedTasks()
    {        
        while (true)
        {
            HttpTask task = queuedTasks.poll();
            
            if (task == null)
            {
                break;
            }
            
            task.execute();
        }
    }
    
    public synchronized void addQueuedTask(HttpTask task)
    {
        queuedTasks.add(task);
    }
}
