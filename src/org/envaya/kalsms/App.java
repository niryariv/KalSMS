/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpResponse;
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
    
    private Map<String, QueuedIncomingSms> incomingSmsMap = new HashMap<String, QueuedIncomingSms>();
    private Map<String, QueuedOutgoingSms> outgoingSmsMap  = new HashMap<String, QueuedOutgoingSms>();           
    
    public Context context;
    public SharedPreferences settings;
    
    private abstract class QueuedMessage<T>
    {
        public T sms;
        public long nextAttemptTime = 0;
        public int numAttempts = 0;                     
                
        public boolean canAttemptNow()
        {
            return (nextAttemptTime > 0 && nextAttemptTime < SystemClock.elapsedRealtime());
        }

        public boolean scheduleNextAttempt()
        {            
            long now = SystemClock.elapsedRealtime();
            numAttempts++;            
            
            if (numAttempts > 4)
            {
                log("5th failure: giving up");
                return false;
            }
            
            int second = 1000;            
            int minute = second * 60;            
            
            if (numAttempts == 1)
            {
                log("1st failure; retry in 1 minute");
                nextAttemptTime = now + 1 * minute; 
            }
            else if (numAttempts == 2)
            {
                log("2nd failure; retry in 10 minutes");
                nextAttemptTime = now + 10 * minute; 
            }
            else if (numAttempts == 3)
            {
                log("3rd failure; retry in 1 hour");
                nextAttemptTime = now + 60 * minute; 
            }
            else
            {
                log("4th failure: retry in 1 day");
                nextAttemptTime = now + 24 * 60 * minute; 
            }   
            
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);        
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                0,
                getAttemptIntent(), 
                0);

            alarm.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                nextAttemptTime,
                pendingIntent);                        
            
            return true;            
        }
        
        public abstract void attemptNow();
        protected abstract Intent getAttemptIntent();            
    }                            
    
    private class QueuedIncomingSms extends QueuedMessage<SmsMessage> {
        public QueuedIncomingSms(SmsMessage sms)
        {
            this.sms = sms;
        }
        
        public void attemptNow()
        {
            log("Retrying forwarding SMS from " + sms.getOriginatingAddress());                
            trySendMessageToServer(sms);
        }
        
        protected Intent getAttemptIntent()
        {
            Intent intent = new Intent(context, IncomingMessageRetry.class);
            intent.setData(Uri.parse("kalsms://incoming/" + getSmsId(sms)));
            return intent;
        }
    }    
               
    private class QueuedOutgoingSms extends QueuedMessage<OutgoingSmsMessage> {
        public QueuedOutgoingSms(OutgoingSmsMessage sms)
        {
            this.sms = sms;
        }

        public void attemptNow()
        {
            log("Retrying sending " +sms.getLogName() + " to " + sms.getTo());                
            trySendSMS(sms);           
        }
        
        protected Intent getAttemptIntent()
        {
            Intent intent = new Intent(context, OutgoingMessageRetry.class);
            intent.setData(Uri.parse("kalsms://outgoing/" + sms.getId()));
            log("id=" + sms.getId());
            return intent;
        }        
    }            
            
    protected App(Context context)
    {
        this.context = context;
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    public static App getInstance(Context context)
    {
        if (app == null)
        {
            app = new App(context);
        }
        return app;
    }
    
    public void debug(String msg)
    {
        Log.d(LOG_NAME, msg);          
    }
                       
    public void log(String msg) 
    {
        Log.d(LOG_NAME, msg);
        
        Intent broadcast = new Intent(App.LOG_INTENT);
        broadcast.putExtra("message", msg);
        context.sendBroadcast(broadcast);
    }

    public void checkOutgoingMessages()
    {
        String serverUrl = getServerUrl();
        if (serverUrl.length() > 0) 
        {
            log("Checking for outgoing messages");
            new PollerTask().execute(                
                new BasicNameValuePair("action", App.ACTION_OUTGOING)
            );
        }
        else
        {
            log("Can't check outgoing messages; server URL not set");
        }
    }
    
    public void setOutgoingMessageAlarm()
    {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);        
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                0,
                new Intent(context, OutgoingMessagePoller.class), 
                0);
        
        alarm.cancel(pendingIntent);        
        
        int pollSeconds = getOutgoingPollSeconds();
        
        if (pollSeconds > 0)
        {
            alarm.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime(),                                
                pollSeconds * 1000, 
                pendingIntent);
            log("Checking for outgoing messages every " + pollSeconds + " sec");            
        }
        else
        {
            log("Not checking for outgoing messages.");
        }
    }
    
    public void logError(Throwable ex)
    {
        logError("ERROR", ex);
    }    
    
    public void logError(String msg, Throwable ex)
    {
        logError(msg, ex, false);
    }
    
    public void logError(String msg, Throwable ex, boolean detail)
    {
        log(msg + ": " + ex.getClass().getName() + ": " + ex.getMessage());
        
        if (detail)
        {
            for (StackTraceElement elem : ex.getStackTrace())
            {
                log(elem.getClassName() + ":" + elem.getMethodName() + ":" + elem.getLineNumber());
            }
            Throwable innerEx = ex.getCause();
            if (innerEx != null)
            {
                logError("Inner exception:", innerEx, true);
            }
        }
    }        
        
    public String getDisplayString(String str)
    {
        if (str.length() == 0)
        {
            return "(not set)";
        }
        else
        {
            return str;
        }   
    }
    
    public String getServerUrl()
    {
        return settings.getString("server_url", "");
    }

    public String getPhoneNumber()
    {
        return settings.getString("phone_number", "");
    }
    
    public int getOutgoingPollSeconds()
    {                
        return Integer.parseInt(settings.getString("outgoing_interval", "0"));        
    }    
    
    public boolean getLaunchOnBoot()
    {                
        return settings.getBoolean("launch_on_boot", true);
    }        
    
    public String getPassword()
    {
        return settings.getString("password", "");
    }               
        
    private void notifyStatus(OutgoingSmsMessage sms, String status, String errorMessage)
    {
        String serverId = sms.getServerId();
        
        String logMessage;
        if (status.equals(App.STATUS_SENT))
        {
            logMessage = "sent successfully"; 
        }
        else if (status.equals(App.STATUS_FAILED))
        {
            logMessage = "could not be sent (" + errorMessage + ")";
        }
        else
        {
            logMessage = "queued";
        }
        String smsDesc = sms.getLogName();
        
        if (serverId != null)
        {        
            app.log("Notifying server " + smsDesc + " " + logMessage);            

            new HttpTask(app).execute(
                new BasicNameValuePair("id", serverId),
                new BasicNameValuePair("status", status),
                new BasicNameValuePair("error", errorMessage),
                new BasicNameValuePair("action", App.ACTION_SEND_STATUS)
            );
        }
        else
        {
            app.log(smsDesc + " " + logMessage);
        }
    }
    
    public synchronized void retryStuckMessages()
    {
        retryStuckOutgoingMessages();
        retryStuckIncomingMessages();
    }
    
    public synchronized int getStuckMessageCount()
    {
        return outgoingSmsMap.size() + incomingSmsMap.size();
    }
    
    public synchronized void retryStuckOutgoingMessages()
    {
        for (QueuedOutgoingSms queuedSms : outgoingSmsMap.values())
        {
            queuedSms.attemptNow();
        }
    }
        
    public synchronized void retryStuckIncomingMessages()
    {        
        for (QueuedIncomingSms queuedSms : incomingSmsMap.values())
        {
            queuedSms.attemptNow();
        }        
    }
    
    public synchronized void notifyOutgoingMessageStatus(String id, int resultCode)
    {
        QueuedOutgoingSms queuedSms = outgoingSmsMap.get(id);
        
        if (queuedSms == null)
        {
            return;
        }
        
        OutgoingSmsMessage sms = queuedSms.sms;                
        
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
                if (!queuedSms.scheduleNextAttempt())
                {                    
                    outgoingSmsMap.remove(id);
                }
                break;
            default:
                outgoingSmsMap.remove(id);
                break;
        }        
        
    }
    
    public synchronized void sendSMS(OutgoingSmsMessage sms)
    {       
        String id = sms.getId();
        if (outgoingSmsMap.containsKey(id))
        {
            log(sms.getLogName() + " already sent, skipping");
            return;
        }

        QueuedOutgoingSms queueEntry = new QueuedOutgoingSms(sms);
        outgoingSmsMap.put(id, queueEntry);
        
        log("Sending " +sms.getLogName() + " to " + sms.getTo());        
        trySendSMS(sms);
    }
    
    private void trySendSMS(OutgoingSmsMessage sms)
    {
        SmsManager smgr = SmsManager.getDefault();
                
        Intent intent = new Intent(context, MessageStatusNotifier.class);
        intent.setData(Uri.parse("kalsms://outgoing/" + sms.getId()));
        
        PendingIntent sentIntent = PendingIntent.getBroadcast(
                this.context,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT);
                
        smgr.sendTextMessage(sms.getTo(), null, sms.getMessage(), sentIntent, null);        
    }
    
    private class PollerTask extends HttpTask {

        public PollerTask()
        {
            super(app);
        }
        
        @Override
        protected void handleResponse(HttpResponse response) throws Exception {
            for (OutgoingSmsMessage reply : parseResponseXML(response)) {
                app.sendSMS(reply);
            }                                        
        }                
    }
    

    private class ForwarderTask extends HttpTask {

        private SmsMessage originalSms;

        public ForwarderTask(SmsMessage originalSms) {
            super(app);
            this.originalSms = originalSms;
        }

        @Override
        protected String getDefaultToAddress()
        {
            return originalSms.getOriginatingAddress();
        }        
                
        @Override
        protected void handleResponse(HttpResponse response) throws Exception {
            
            for (OutgoingSmsMessage reply : parseResponseXML(response)) {
                app.sendSMS(reply);
            }                                        
            
            app.notifyIncomingMessageStatus(originalSms, true);            
        }
        
        @Override
        protected void handleFailure()
        {
            app.notifyIncomingMessageStatus(originalSms, false);
        }        
    }        
    
    private String getSmsId(SmsMessage sms)
    {
        return sms.getOriginatingAddress() + ":" + sms.getMessageBody() + ":" + sms.getTimestampMillis();
    }
    
    public synchronized void sendMessageToServer(SmsMessage sms) 
    {
        String id = getSmsId(sms);
        if (incomingSmsMap.containsKey(id))
        {
            log("Duplicate incoming SMS, skipping");
            return;
        }        
                
        QueuedIncomingSms queuedSms = new QueuedIncomingSms(sms);        
        incomingSmsMap.put(id, queuedSms);
                
        app.log("Received SMS from " + sms.getOriginatingAddress());
        
        trySendMessageToServer(sms);
    }    
    
    public void trySendMessageToServer(SmsMessage sms) 
    {
        String message = sms.getMessageBody();
        String sender = sms.getOriginatingAddress();
        
        new ForwarderTask(sms).execute(
            new BasicNameValuePair("from", sender),
            new BasicNameValuePair("message", message),
            new BasicNameValuePair("action", App.ACTION_INCOMING)
        );

    }
    
    private synchronized void notifyIncomingMessageStatus(SmsMessage sms, boolean success)
    {
        String id = getSmsId(sms);

        QueuedIncomingSms queuedSms = incomingSmsMap.get(id);
        
        if (queuedSms != null)
        {
            if (success || !queuedSms.scheduleNextAttempt())
            {             
                incomingSmsMap.remove(id);
            }               
        }
    }
    
    public synchronized void retryIncomingMessage(String id)
    {
        QueuedIncomingSms queuedSms = incomingSmsMap.get(id);
        if (queuedSms != null)
        {
            queuedSms.attemptNow();
        }        
    }
    
    public synchronized void retryOutgoingMessage(String id)
    {
        QueuedOutgoingSms queuedSms = outgoingSmsMap.get(id);
        if (queuedSms != null)
        {
            queuedSms.attemptNow();
        }        
    }
    
}
