package org.envaya.kalsms;

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
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Log;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.envaya.kalsms.receiver.OutgoingMessagePoller;
import org.envaya.kalsms.task.HttpTask;
import org.envaya.kalsms.task.PollerTask;
import org.json.JSONArray;
import org.json.JSONException;

public final class App extends Application {
    
    public static final String ACTION_OUTGOING = "outgoing";
    public static final String ACTION_INCOMING = "incoming";
    public static final String ACTION_SEND_STATUS = "send_status";
    
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SENT = "sent";
    
    public static final String MESSAGE_TYPE_MMS = "mms";
    public static final String MESSAGE_TYPE_SMS = "sms";
    
    public static final String LOG_NAME = "KALSMS";
    
    // intent to signal to Main activity (if open) that log has changed
    public static final String LOG_INTENT = "org.envaya.kalsms.LOG";
                
    public static final String QUERY_EXPANSION_PACKS_INTENT = "org.envaya.kalsms.QUERY_EXPANSION_PACKS";
    public static final String QUERY_EXPANSION_PACKS_EXTRA_PACKAGES = "packages";
    
    // Interface for sending outgoing messages to expansion packs
    public static final String OUTGOING_SMS_INTENT_SUFFIX = ".OUTGOING_SMS";    
    public static final String OUTGOING_SMS_EXTRA_TO = "to";
    public static final String OUTGOING_SMS_EXTRA_BODY = "body";    
    public static final int OUTGOING_SMS_UNHANDLED = Activity.RESULT_FIRST_USER;
    
    // intent for MessageStatusNotifier to receive status updates for outgoing SMS
    // (even if sent by an expansion pack)
    public static final String MESSAGE_STATUS_INTENT = "org.envaya.kalsms.MESSAGE_STATUS";
    
    public static final int MAX_DISPLAYED_LOG = 4000;
    public static final int LOG_TIMESTAMP_INTERVAL = 60000;
    
    // Each QueuedMessage is identified within our internal Map by its Uri.
    // Currently QueuedMessage instances are only available within KalSMS,
    // (but they could be made available to other applications later via a ContentProvider)
    public static final Uri CONTENT_URI = Uri.parse("content://org.envaya.kalsms");
    public static final Uri INCOMING_URI = Uri.withAppendedPath(CONTENT_URI, "incoming");
    public static final Uri OUTGOING_URI = Uri.withAppendedPath(CONTENT_URI, "outgoing");
    
    // max per-app outgoing SMS rate used by com.android.internal.telephony.SMSDispatcher
    // with a slightly longer check period to account for variance in the time difference
    // between when we prepare messages and when SMSDispatcher receives them
    public static int OUTGOING_SMS_CHECK_PERIOD = 3605000; // one hour plus 5 sec (in ms)    
    public static int OUTGOING_SMS_MAX_COUNT = 100;
    
    private Map<Uri, IncomingMessage> incomingMessages = new HashMap<Uri, IncomingMessage>();
    private Map<Uri, OutgoingMessage> outgoingMessages = new HashMap<Uri, OutgoingMessage>();    
    
    private SharedPreferences settings;
    private MmsObserver mmsObserver;
    private SpannableStringBuilder displayedLog = new SpannableStringBuilder();
    private long lastLogTime;    
    
    private PackageInfo packageInfo;
    
    // list of package names (e.g. org.envaya.kalsms, or org.envaya.kalsms.packXX)
    // for this package and all expansion packs
    private List<String> outgoingMessagePackages = new ArrayList<String>();
    
    // count to provide round-robin selection of expansion packs
    private int outgoingMessageCount = -1;
    
    // map of package name => sorted list of timestamps of outgoing messages
    private HashMap<String, ArrayList<Long>> outgoingTimestamps
            = new HashMap<String, ArrayList<Long>>();
        
    private MmsUtils mmsUtils;
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        
        settings = PreferenceManager.getDefaultSharedPreferences(this);        
        mmsUtils = new MmsUtils(this);
        
        outgoingMessagePackages.add(getPackageName());
        
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
        
        mmsObserver = new MmsObserver(this);
        mmsObserver.register();
        
        setOutgoingMessageAlarm();        
        updateEnabledNotification();
    }   
    
    public void updateEnabledNotification()
    {     
        startService(new Intent(this, ForegroundService.class));        
    }
    
    public PackageInfo getPackageInfo()
    {
        return packageInfo;
    }
    
    
    public synchronized String chooseOutgoingSmsPackage()
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

            if ( (sent.size() + 1) <= OUTGOING_SMS_MAX_COUNT)
            {
                sent.add(ct);
                return packageName;
            }            
        }
        
        log("Can't send outgoing SMS: maximum limit of "
            + getOutgoingMessageLimit() + " in 1 hour reached");
        log("To increase this limit, install an expansion pack.");
                
        return null;
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
    
    public boolean isTestMode()
    {
        return settings.getBoolean("test_mode", false);
    }
    
    public boolean getKeepInInbox() 
    {
        return settings.getBoolean("keep_in_inbox", false);        
    }

    public String getPassword() {
        return settings.getString("password", "");
    }

    private void notifyStatus(OutgoingMessage sms, String status, String errorMessage) {
        String serverId = sms.getServerId();

        String logMessage;
        if (status.equals(App.STATUS_SENT)) {
            logMessage = "sent successfully";
        } else if (status.equals(App.STATUS_FAILED)) {
            logMessage = "could not be sent (" + errorMessage + ")";
        } else {
            logMessage = "queued";
        }
        String smsDesc = sms.getLogName();

        if (serverId != null) {
            log("Notifying server " + smsDesc + " " + logMessage);

            new HttpTask(this,
                new BasicNameValuePair("id", serverId),
                new BasicNameValuePair("status", status),
                new BasicNameValuePair("error", errorMessage),
                new BasicNameValuePair("action", App.ACTION_SEND_STATUS)                    
            ).execute();
        } else {
            log(smsDesc + " " + logMessage);
        }
    }

    public synchronized void retryStuckMessages() {
        retryStuckOutgoingMessages();
        retryStuckIncomingMessages();
    }

    public synchronized int getStuckMessageCount() {
        return outgoingMessages.size() + incomingMessages.size();
    }

    public synchronized void retryStuckOutgoingMessages() {
        for (OutgoingMessage sms : outgoingMessages.values()) {
            sms.retryNow();
        }
    }

    public synchronized void retryStuckIncomingMessages() {
        for (IncomingMessage sms : incomingMessages.values()) {
            sms.retryNow();
        }
    }
    
    public synchronized void setIncomingMessageStatus(IncomingMessage message, boolean success) {        
        Uri uri = message.getUri();
        if (success)
        {
            incomingMessages.remove(uri);
            
            if (message instanceof IncomingMms)
            {
                IncomingMms mms = (IncomingMms)message;
                if (!getKeepInInbox())
                {
                    log("Deleting MMS " + mms.getId() + " from inbox...");
                    mmsUtils.deleteFromInbox(mms);
                }            
            }
        }
        else if (!message.scheduleRetry())
        {
            incomingMessages.remove(uri);
        }
    }    

    public synchronized void notifyOutgoingMessageStatus(Uri uri, int resultCode) {
        OutgoingMessage sms = outgoingMessages.get(uri);

        if (sms == null) {
            return;
        }

        switch (resultCode) {
            case Activity.RESULT_OK:
                this.notifyStatus(sms, App.STATUS_SENT, "");
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                this.notifyStatus(sms, App.STATUS_FAILED, "generic failure");
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                this.notifyStatus(sms, App.STATUS_FAILED, "radio off");
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                this.notifyStatus(sms, App.STATUS_FAILED, "no service");
                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:
                this.notifyStatus(sms, App.STATUS_FAILED, "null PDU");
                break;
            default:
                this.notifyStatus(sms, App.STATUS_FAILED, "unknown error");
                break;
        }

        switch (resultCode) {
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
            case SmsManager.RESULT_ERROR_RADIO_OFF:
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                if (!sms.scheduleRetry()) {
                    outgoingMessages.remove(uri);
                }
                break;
            default:
                outgoingMessages.remove(uri);
                break;
        }
    }

    public synchronized void sendOutgoingMessage(OutgoingMessage sms) {
        
        if (isTestMode() && !isTestPhoneNumber(sms.getTo()))
        {
            // this is mostly to prevent accidentally sending real messages to
            // random people while testing...
            
            log("Ignoring outgoing SMS to " + sms.getTo());
            return;
        }
        
        Uri uri = sms.getUri();
        if (outgoingMessages.containsKey(uri)) {
            log("Duplicate outgoing " + sms.getLogName() + ", skipping");
            return;
        }

        outgoingMessages.put(uri, sms);

        log("Sending " + sms.getLogName() + " to " + sms.getTo());
        sms.trySend();
    }

    public synchronized void forwardToServer(IncomingMessage message) {
        Uri uri = message.getUri();
        
        if (incomingMessages.containsKey(uri)) {
            log("Duplicate incoming "+message.getDisplayType()+", skipping");
            return;
        }

        incomingMessages.put(uri, message);

        log("Received "+message.getDisplayType()+" from " + message.getFrom());

        message.tryForwardToServer();
    }

    public synchronized void retryIncomingMessage(Uri uri) {
        IncomingMessage message = incomingMessages.get(uri);
        if (message != null) {
            message.retryNow();
        }
    }

    public synchronized void retryOutgoingMessage(Uri uri) {
        OutgoingMessage sms = outgoingMessages.get(uri);
        if (sms != null) {
            sms.retryNow();
        }
    }

    public void debug(String msg) {
        Log.d(LOG_NAME, msg);
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
        }

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
            
        Intent broadcast = new Intent(App.LOG_INTENT);
        sendBroadcast(broadcast);
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
    
    public List<String> getTestPhoneNumbers()
    {        
        if (testPhoneNumbers == null)
        {          
            testPhoneNumbers = new ArrayList<String>();
            String phoneNumbersJson = settings.getString("test_phone_numbers", "");
            
            if (phoneNumbersJson.length() > 0)
            {
                try
                {                                
                    JSONArray arr = new JSONArray(phoneNumbersJson);
                    int numSenders = arr.length();
                    for (int i = 0; i < numSenders; i++)
                    {                    
                        testPhoneNumbers.add(arr.getString(i));                    
                    }
                }
                catch (JSONException ex)
                {
                    logError("Error parsing test phone numbers", ex);
                }            
            }
        }
        return testPhoneNumbers;
    }
    
    public void addTestPhoneNumber(String phoneNumber)
    {
        List<String> phoneNumbers = getTestPhoneNumbers();
        log("Added test phone number: " + phoneNumber);
        phoneNumbers.add(phoneNumber);
        saveTestPhoneNumbers(phoneNumbers);
    }
    
    public void removeTestPhoneNumber(String phoneNumber)
    {
        List<String> phoneNumbers = getTestPhoneNumbers();
        phoneNumbers.remove(phoneNumber);
        log("Removed test phone number: " + phoneNumber);
        saveTestPhoneNumbers(phoneNumbers);
    }
    
    private void saveTestPhoneNumbers(List<String> phoneNumbers)
    {
        settings.edit().putString("test_phone_numbers", 
            new JSONArray(phoneNumbers).toString()
        ).commit();
    }    
    
    public boolean isTestPhoneNumber(String phoneNumber)
    {            
        for (String testNumber : getTestPhoneNumbers())
        {
            // handle inexactness due to various different ways of formatting numbers
            if (testNumber.contains(phoneNumber) || phoneNumber.contains(testNumber))
            {
                return true;
            }
        }        
        return false;
    }    
    
    private HttpClient httpClient;
    
    public synchronized HttpClient getHttpClient()
    {
        if (httpClient == null)
        {
            // via http://thinkandroid.wordpress.com/2009/12/31/creating-an-http-client-example/
            // also http://hc.apache.org/httpclient-3.x/threading.html
            
            HttpParams httpParams = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 8000);
            HttpConnectionParams.setSoTimeout(httpParams, 8000);                    
            HttpProtocolParams.setContentCharset(httpParams, "utf-8");            
            
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
}
