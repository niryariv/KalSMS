package org.envaya.sms;

import android.content.Intent;
import android.os.SystemClock;
import org.envaya.sms.receiver.IncomingMessageRetry;
import org.envaya.sms.task.ForwarderTask;
import org.apache.http.message.BasicNameValuePair;

public abstract class IncomingMessage extends QueuedMessage {

    protected String from;
    protected String message = "";
    protected long timestamp; // unix timestamp in milliseconds
    
    protected long timeReceived; // SystemClock.elapsedRealtime
    
    private ProcessingState state = ProcessingState.None;
        
    public enum ProcessingState
    {
        None,           // not doing anything with this sms now... just sitting around
        Queued,         // waiting to forward to server
        Forwarding,     // currently sending to server
        Scheduled,      // waiting for a while before retrying after failure forwarding
        Forwarded
    }    
    
    public IncomingMessage(App app, String from, long timestamp)
    {
        super(app);
        this.from = from;
        this.timestamp = timestamp;
        
        this.timeReceived = SystemClock.elapsedRealtime();
    }
    
    public String getMessageBody()
    {
        return message;
    }    
    
    public long getAge()
    {
        return SystemClock.elapsedRealtime() - timeReceived;
    }
    
    public long getTimestamp()
    {
        return timestamp;
    }
    
    public ProcessingState getProcessingState()
    {
        return state;
    }
    
    public void setProcessingState(ProcessingState status)
    {
        this.state = status;
    }    
    
    public boolean isForwardable()
    {
        return app.isForwardablePhoneNumber(from);
    }
    
    public String getFrom()
    {
        return from;
    }
     
    protected Intent getRetryIntent() {
        Intent intent = new Intent(app, IncomingMessageRetry.class);
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
                return "queued to forward";
            case Forwarding:
                return "forwarding to server";            
            default:
                return "";
        }
    }    
    
    public String getDescription()
    {
        return getDisplayType() + " from " + getFrom();
    }
    
    public void tryForwardToServer()
    {        
        if (numRetries > 0)
        {
            app.log("Retrying forwarding " + getDescription());
        }
        
        getForwarderTask().execute();
    }
    
    public abstract String getMessageType();
    
    protected ForwarderTask getForwarderTask()
    {
        return new ForwarderTask(this,
            new BasicNameValuePair("message_type", getMessageType()),
            new BasicNameValuePair("message", getMessageBody()),
            new BasicNameValuePair("action", App.ACTION_INCOMING),
            new BasicNameValuePair("from", getFrom()),
            new BasicNameValuePair("timestamp", "" + getTimestamp()),
            new BasicNameValuePair("age", "" + getAge())
        );
    }
}
