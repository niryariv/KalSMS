package org.envaya.sms;

import android.content.Intent;
import org.envaya.sms.receiver.IncomingMessageRetry;

public abstract class IncomingMessage extends QueuedMessage {

    protected String from;
    protected long timestamp; // unix timestamp in milliseconds
    
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
    
    public abstract void tryForwardToServer();
}
