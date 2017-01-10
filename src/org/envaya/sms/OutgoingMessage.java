
package org.envaya.sms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import org.envaya.sms.receiver.OutgoingMessageRetry;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import org.envaya.sms.receiver.OutgoingMessageTimeout;

public abstract class OutgoingMessage extends QueuedMessage {
    
    private String serverId;    
    private String message;
    private String from;
    private String to;     
    private int priority;
    private int localId;
    private static int nextLocalId = 1;            
    
    private ProcessingState state = ProcessingState.None;
        
    public class ScheduleInfo
    {
        public boolean now = false;
        public long time = 0;
    }
    
    public enum ProcessingState
    {
        None,           // not doing anything with this sms now... just sitting around
        Queued,         // in the outgoing queue waiting to be sent
        Sending,        // passed to an expansion pack, waiting for status notification
        Scheduled,      // waiting for a while before retrying after failure sending
        Sent
    }
    
    public OutgoingMessage(App app)
    {
        super(app);
        this.localId = getNextLocalId();
    }
    
    public ProcessingState getProcessingState()
    {
        return state;
    }
    
    public static OutgoingMessage newFromMessageType(App app, String type)
    {
        return new OutgoingSms(app);
    }
    
    public void setProcessingState(ProcessingState status)
    {
        this.state = status;
    }
    
    public boolean isCancelable()
    {
        return this.state == ProcessingState.None 
                || this.state == ProcessingState.Queued
                || this.state == ProcessingState.Scheduled;
    }
    
    static synchronized int getNextLocalId()
    {
        return nextLocalId++;
    }
    
    public int getLocalId()
    {
        return localId;
    }
    
    public Uri getUri()
    {
        return Uri.withAppendedPath(App.OUTGOING_URI, ((serverId == null) ? 
                ("_o" + localId) : serverId));
    }
    
    public static Uri getUriForServerId(String serverId)
    {
        return Uri.withAppendedPath(App.OUTGOING_URI, serverId);
    }    
    
    public String getServerId()
    {
        return serverId;
    }
    
    public void setServerId(String id)
    {
        this.serverId = id;
    }    
           
    public String getMessageBody()
    {
        return message;
    }
    
    public void setMessageBody(String message)
    {
        this.message = message;
    }
    
    public String getFrom()
    {
        return from;
    }
    
    public void setFrom(String from)
    {
        this.from = from;
    }
    
    public String getTo()
    {
        return to;
    }
    
    public void setTo(String to)
    {
        this.to = to;
    }

    public void setPriority(int priority)
    {
        this.priority = priority;
    }
    
    public int getPriority()
    {
        return priority;
    }    

    protected Intent getRetryIntent() {
        Intent intent = new Intent(app, OutgoingMessageRetry.class);
        intent.setData(this.getUri());
        return intent;
    }       
    
    public String getStatusText()
    {
        switch (state)
        {
            case Scheduled:
                return "scheduled retry";
            case Queued:
                return "queued to send";
            case Sending:
                return "sending";
            default:
                return "";
        }
    }
    
    public String getDescription()
    {
        return getDisplayType() + " to " + getTo();
    }
    
    public void validate() throws ValidationException
    {
    }
    
    abstract String getMessageType();
    abstract ScheduleInfo scheduleSend();
    abstract void send(ScheduleInfo schedule);

    protected PendingIntent getTimeoutPendingIntent()
    {
        Intent timeout = new Intent(app, OutgoingMessageTimeout.class);
        timeout.setData(getUri());
        
        return PendingIntent.getBroadcast(app,
            0,
            timeout,
            0);
    }
    
    public void setSendTimeout()
    {
        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);

        PendingIntent timeoutIntent = getTimeoutPendingIntent();

        alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                SystemClock.elapsedRealtime() + App.MESSAGE_SEND_TIMEOUT, timeoutIntent);
    }
    
    public void clearSendTimeout()
    {
        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);

        PendingIntent timeoutIntent = getTimeoutPendingIntent();

        alarm.cancel(timeoutIntent);
    }
}
