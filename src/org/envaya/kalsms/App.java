package org.envaya.kalsms;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.message.BasicNameValuePair;

public class App {

    public static final String ACTION_OUTGOING = "outgoing";
    public static final String ACTION_INCOMING = "incoming";
    public static final String ACTION_SEND_STATUS = "send_status";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SENT = "sent";
    public static final String LOG_NAME = "KALSMS";
    public static final String LOG_INTENT = "org.envaya.kalsms.LOG";
    private static App app;
    
    private Map<String, IncomingMessage> incomingSmsMap = new HashMap<String, IncomingMessage>();
    private Map<String, OutgoingMessage> outgoingSmsMap = new HashMap<String, OutgoingMessage>();
    
    public Context context;
    public SharedPreferences settings;

    protected App(Context context) {
        this.context = context;
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static App getInstance(Context context) {
        if (app == null) {
            app = new App(context);
        }
        return app;
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
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                0,
                new Intent(context, OutgoingMessagePoller.class),
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

    public boolean getLaunchOnBoot() {
        return settings.getBoolean("launch_on_boot", false);
    }
    
    public boolean isEnabled()
    {
        return settings.getBoolean("enabled", false);
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
            app.log("Notifying server " + smsDesc + " " + logMessage);

            new HttpTask(app,
                new BasicNameValuePair("id", serverId),
                new BasicNameValuePair("status", status),
                new BasicNameValuePair("error", errorMessage),
                new BasicNameValuePair("action", App.ACTION_SEND_STATUS)                    
            ).execute();
        } else {
            app.log(smsDesc + " " + logMessage);
        }
    }

    public synchronized void retryStuckMessages() {
        retryStuckOutgoingMessages();
        retryStuckIncomingMessages();
    }

    public synchronized int getStuckMessageCount() {
        return outgoingSmsMap.size() + incomingSmsMap.size();
    }

    public synchronized void retryStuckOutgoingMessages() {
        for (OutgoingMessage sms : outgoingSmsMap.values()) {
            sms.retryNow();
        }
    }

    public synchronized void retryStuckIncomingMessages() {
        for (IncomingMessage sms : incomingSmsMap.values()) {
            sms.retryNow();
        }
    }
    
    public synchronized void setIncomingMessageStatus(IncomingMessage sms, boolean success) {        
        String id = sms.getId();
        if (success)
        {
            incomingSmsMap.remove(id);
        }
        else if (!sms.scheduleRetry())
        {
            incomingSmsMap.remove(id);
        }
    }    

    public synchronized void notifyOutgoingMessageStatus(String id, int resultCode) {
        OutgoingMessage sms = outgoingSmsMap.get(id);

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
                    outgoingSmsMap.remove(id);
                }
                break;
            default:
                outgoingSmsMap.remove(id);
                break;
        }
    }

    public synchronized void sendOutgoingMessage(OutgoingMessage sms) {
        String id = sms.getId();
        if (outgoingSmsMap.containsKey(id)) {
            log(sms.getLogName() + " already sent, skipping");
            return;
        }

        outgoingSmsMap.put(id, sms);

        log("Sending " + sms.getLogName() + " to " + sms.getTo());
        sms.trySend();
    }

    public synchronized void forwardToServer(IncomingMessage sms) {
        String id = sms.getId();
        
        if (incomingSmsMap.containsKey(id)) {
            log("Duplicate incoming SMS, skipping");
            return;
        }

        incomingSmsMap.put(id, sms);

        app.log("Received SMS from " + sms.getFrom());

        sms.tryForwardToServer();
    }

    public synchronized void retryIncomingMessage(String id) {
        IncomingMessage sms = incomingSmsMap.get(id);
        if (sms != null) {
            sms.retryNow();
        }
    }

    public synchronized void retryOutgoingMessage(String id) {
        OutgoingMessage sms = outgoingSmsMap.get(id);
        if (sms != null) {
            sms.retryNow();
        }
    }

        public void debug(String msg) {
        Log.d(LOG_NAME, msg);
    }

    public void log(String msg) {
        Log.d(LOG_NAME, msg);

        Intent broadcast = new Intent(App.LOG_INTENT);
        broadcast.putExtra("message", msg);
        context.sendBroadcast(broadcast);
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

}
