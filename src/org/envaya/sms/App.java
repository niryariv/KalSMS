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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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
import org.envaya.sms.receiver.DequeueOutgoingMessageReceiver;
import org.envaya.sms.receiver.OutgoingMessagePoller;
import org.envaya.sms.task.HttpTask;
import org.envaya.sms.task.PollerTask;
import org.json.JSONArray;
import org.json.JSONException;

public final class App extends Application {
    
    public static final String ACTION_OUTGOING = "outgoing";
    public static final String ACTION_INCOMING = "incoming";
    public static final String ACTION_SEND_STATUS = "send_status";
    public static final String ACTION_TEST = "test";
    
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SENT = "sent";
    
    public static final String MESSAGE_TYPE_MMS = "mms";
    public static final String MESSAGE_TYPE_SMS = "sms";
    
    public static final String LOG_NAME = "EnvayaSMS";
    
    // intent to signal to Main activity (if open) that log has changed
    public static final String LOG_INTENT = "org.envaya.sms.LOG";
                
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
    
    public static final int MAX_DISPLAYED_LOG = 4000;
    public static final int LOG_TIMESTAMP_INTERVAL = 60000; // ms
    
    public static final int HTTP_CONNECTION_TIMEOUT = 10000; // ms
    
    // Each QueuedMessage is identified within our internal Map by its Uri.
    // Currently QueuedMessage instances are only available within EnvayaSMS,
    // (but they could be made available to other applications later via a ContentProvider)
    public static final Uri CONTENT_URI = Uri.parse("content://org.envaya.sms");
    public static final Uri INCOMING_URI = Uri.withAppendedPath(CONTENT_URI, "incoming");
    public static final Uri OUTGOING_URI = Uri.withAppendedPath(CONTENT_URI, "outgoing");
    
    // max per-app outgoing SMS rate used by com.android.internal.telephony.SMSDispatcher
    // with a slightly longer check period to account for variance in the time difference
    // between when we prepare messages and when SMSDispatcher receives them
    public static int OUTGOING_SMS_CHECK_PERIOD = 3605000; // one hour plus 5 sec (in ms)    
    public static int OUTGOING_SMS_MAX_COUNT = 100;
    
    private Map<Uri, IncomingMessage> incomingMessages = new HashMap<Uri, IncomingMessage>();
    private Map<Uri, OutgoingMessage> outgoingMessages = new HashMap<Uri, OutgoingMessage>();    
    
    private int numPendingOutgoingMessages = 0;
    private PriorityQueue<OutgoingMessage> outgoingQueue = new PriorityQueue<OutgoingMessage>(10, 
        new Comparator<OutgoingMessage>() { 
            public int compare(OutgoingMessage t1, OutgoingMessage t2)
            {
                int pri2 = t2.getPriority();
                int pri1 = t1.getPriority();
                
                if (pri1 != pri2)
                {
                    return pri2 - pri1;
                }
                
                int order2 = t2.getLocalId();
                int order1 = t1.getLocalId();
                
                return order1 - order2;
            }
        }            
    );
    
    private SharedPreferences settings;
    private MmsObserver mmsObserver;
    private SpannableStringBuilder displayedLog = new SpannableStringBuilder();
    private long lastLogTime;    
    
    private PackageInfo packageInfo;
    
    // list of package names (e.g. org.envaya.sms, or org.envaya.sms.packXX)
    // for this package and all expansion packs
    private List<String> outgoingMessagePackages = new ArrayList<String>();
    
    // count to provide round-robin selection of expansion packs
    private int outgoingMessageCount = -1;
    
    private long nextValidOutgoingTime;
    
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
    
    private synchronized String chooseOutgoingSmsPackage(int numParts)
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
    private synchronized long getNextValidOutgoingTime(int numParts)
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
        
        this.nextValidOutgoingTime = 0;
        
        retryStuckOutgoingMessages();
        retryStuckIncomingMessages();
    }

    public synchronized int getStuckMessageCount() {        
        return outgoingMessages.size() + incomingMessages.size();
    }

    public synchronized void retryStuckOutgoingMessages() {
        for (OutgoingMessage sms : outgoingMessages.values()) {
            
            OutgoingMessage.ProcessingState state = sms.getProcessingState();
            
            if (state != OutgoingMessage.ProcessingState.Queued
                && state != OutgoingMessage.ProcessingState.Sending)
            {
                enqueueOutgoingMessage(sms);
            }
        }
        maybeDequeueOutgoingMessage();
    }

    public synchronized void retryStuckIncomingMessages() {
        for (IncomingMessage sms : incomingMessages.values()) {            
            IncomingMessage.ProcessingState state = sms.getProcessingState();            
            if (state != IncomingMessage.ProcessingState.Forwarding)
            {            
                enqueueIncomingMessage(sms);
            }
        }
    }
    
    public synchronized void setIncomingMessageStatus(IncomingMessage message, boolean success) {        
        
        message.setProcessingState(IncomingMessage.ProcessingState.None);
        
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
        else 
        {                        
            if (message.scheduleRetry())
            {
                message.setProcessingState(IncomingMessage.ProcessingState.Scheduled);
            }
            else
            {
                incomingMessages.remove(uri);
            }
        }
    }    

    public synchronized void notifyOutgoingMessageStatus(Uri uri, int resultCode, int partIndex, int numParts) {
        OutgoingMessage sms = outgoingMessages.get(uri);

        if (sms == null) {
            return;
        }
        
        if (partIndex != 0)
        {
            // TODO: process message status for parts other than the first one
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
        
        sms.setProcessingState(OutgoingMessage.ProcessingState.None);

        switch (resultCode) {
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
            case SmsManager.RESULT_ERROR_RADIO_OFF:
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                if (sms.scheduleRetry()) {
                    sms.setProcessingState(OutgoingMessage.ProcessingState.Scheduled);
                }
                else {                    
                    outgoingMessages.remove(uri);
                }
                break;
            default:
                outgoingMessages.remove(uri);
                break;
        }
                
        numPendingOutgoingMessages--;
        maybeDequeueOutgoingMessage();
    }

    public synchronized void sendOutgoingMessage(OutgoingMessage sms) {
        
        String to = sms.getTo();
        if (to == null || to.length() == 0)
        {
            notifyStatus(sms, App.STATUS_FAILED, "Destination address is empty");
            return;
        }        
        
        if (isTestMode() && !isTestPhoneNumber(to))
        {
            // this is mostly to prevent accidentally sending real messages to
            // random people while testing...        
            notifyStatus(sms, App.STATUS_FAILED, "Destination number is not in list of test senders");
            return;
        }
        
        String messageBody = sms.getMessageBody();
        
        if (messageBody == null || messageBody.length() == 0)
        {
            notifyStatus(sms, App.STATUS_FAILED, "Message body is empty");
            return;
        }               
        
        Uri uri = sms.getUri();
        if (outgoingMessages.containsKey(uri)) {
            debug("Duplicate outgoing " + sms.getLogName() + ", skipping");
            return;
        }

        outgoingMessages.put(uri, sms);        
        enqueueOutgoingMessage(sms);
    }
    
    public synchronized void maybeDequeueOutgoingMessage()
    {
        long now = System.currentTimeMillis();        
        if (nextValidOutgoingTime <= now && numPendingOutgoingMessages < 2)
        {
            OutgoingMessage sms = outgoingQueue.peek();
            
            if (sms == null)
            {
                return;
            }
            
            SmsManager smgr = SmsManager.getDefault();
            ArrayList<String> bodyParts = smgr.divideMessage(sms.getMessageBody());
            
            int numParts = bodyParts.size();
            
            if (numParts > App.OUTGOING_SMS_MAX_COUNT)
            {
                outgoingQueue.poll();
                outgoingMessages.remove(sms.getUri());
                notifyStatus(sms, App.STATUS_FAILED, "Message has too many parts ("+(numParts)+")");
                return;
            }
            
            String packageName = chooseOutgoingSmsPackage(numParts);            
            
            if (packageName == null)
            {            
                nextValidOutgoingTime = getNextValidOutgoingTime(numParts);                
                                
                if (nextValidOutgoingTime <= now) // should never happen
                {
                    nextValidOutgoingTime = now + 2000;
                }
                
                long diff = nextValidOutgoingTime - now;
                
                log("Waiting for " + (diff/1000) + " seconds");
                
                AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

                Intent intent = new Intent(this, DequeueOutgoingMessageReceiver.class);
                
                PendingIntent pendingIntent = PendingIntent.getBroadcast(this,
                    0,
                    intent,
                    0);

                alarm.set(
                    AlarmManager.RTC_WAKEUP,
                    nextValidOutgoingTime,
                    pendingIntent);
                
                return;
            }
            
            outgoingQueue.poll();            
            numPendingOutgoingMessages++;
            
            sms.setProcessingState(OutgoingMessage.ProcessingState.Sending);
            
            sms.trySend(bodyParts, packageName);
        }  
    }
    
    public synchronized void enqueueOutgoingMessage(OutgoingMessage sms) 
    {
        outgoingQueue.add(sms);
        sms.setProcessingState(OutgoingMessage.ProcessingState.Queued);
        maybeDequeueOutgoingMessage();
    }

    public synchronized void forwardToServer(IncomingMessage message) {
        Uri uri = message.getUri();
        
        if (incomingMessages.containsKey(uri)) {
            log("Duplicate incoming "+message.getDisplayType()+", skipping");
            return;
        }

        incomingMessages.put(uri, message);

        log("Received "+message.getDisplayType()+" from " + message.getFrom());
        
        enqueueIncomingMessage(message);
    }
    
    public synchronized void enqueueIncomingMessage(IncomingMessage message)
    {
        message.setProcessingState(IncomingMessage.ProcessingState.Forwarding);
        message.tryForwardToServer();
    }

    public synchronized void retryIncomingMessage(Uri uri) {
        IncomingMessage message = incomingMessages.get(uri);
        if (message != null) {
            enqueueIncomingMessage(message);
        }
    }

    public synchronized void retryOutgoingMessage(Uri uri) {
        OutgoingMessage sms = outgoingMessages.get(uri);
        if (sms != null) {
            enqueueOutgoingMessage(sms);
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
}
