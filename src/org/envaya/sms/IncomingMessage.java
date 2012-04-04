package org.envaya.sms;

import android.content.Intent;
import android.os.SystemClock;
import org.envaya.sms.receiver.IncomingMessageRetry;
import org.envaya.sms.task.ForwarderTask;
import org.apache.http.message.BasicNameValuePair;

public abstract class IncomingMessage extends QueuedMessage {

    protected String from;  // phone number of other party, for messages with direction=Direction.Incoming
    
    protected String to;    // phone number of other party, for messages with direction=Direction.Sent
    
    protected Direction direction = Direction.Incoming;    
    
    protected long messagingId; // _id from Messaging app content provider tables (if applicable)

    protected String message = "";
    protected long timestamp; // unix timestamp in milliseconds
    
    protected long timeCreated; // SystemClock.elapsedRealtime
    
    private ProcessingState state = ProcessingState.None;
        
    public enum Direction
    {
        Incoming,   // a message that was received by this phone
        
        Sent        // Message was sent via Messaging app (so it's "Incoming" from 
                    // the phone to the server, but we don't actually send it)
    }
    
    public enum ProcessingState
    {
        None,           // not doing anything with this sms now... just sitting around
        Queued,         // waiting to forward to server
        Forwarding,     // currently sending to server
        Scheduled,      // waiting for a while before retrying after failure forwarding
        Forwarded
    }    
    
    public IncomingMessage(App app)
    {
        super(app);
    }
    
    public IncomingMessage(App app, String from, long timestamp)
    {
        super(app);
        
        if (from == null)
        {
            from = "";
        }
        
        this.from = from;
        this.timestamp = timestamp;        
        this.timeCreated = SystemClock.elapsedRealtime();
    }
        
    public void setDirection(Direction direction)
    {
        this.direction = direction;
    }
    
    public Direction getDirection()
    {
        return this.direction;
    }
    
    public String getMessageBody()
    {
        return message;
    }    
    
    public void setMessageBody(String message)
    {
        this.message = message;
    }
    
    public long getAge()
    {
        return SystemClock.elapsedRealtime() - timeCreated;
    }
    
    public long getTimestamp()
    {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp)
    {
        this.timestamp = timestamp;
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
        if (direction == Direction.Sent)
        {
            return app.isForwardingSentMessagesEnabled() && app.isForwardablePhoneNumber(to);
        }
        else
        {
            return app.isForwardablePhoneNumber(from);
        }
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
        
    public long getMessagingId()
    {
        return messagingId;
    }
    
    public void setMessagingId(long messagingId)
    {
        this.messagingId = messagingId;
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
        if (direction == Direction.Sent)
        {
            return "Sent " + getDisplayType() + " to " + getTo();
        }
        else
        {
            return getDisplayType() + " from " + getFrom();
        }
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
    
    public void onForwardComplete()
    {
        
    }
    
    protected ForwarderTask getForwarderTask()
    {
        ForwarderTask task = new ForwarderTask(this,
            new BasicNameValuePair("message_type", getMessageType()),
            new BasicNameValuePair("message", getMessageBody()),
            new BasicNameValuePair("timestamp", "" + getTimestamp())
        );
        
        if (direction == Direction.Sent)
        {
            task.addParam("action", App.ACTION_FORWARD_SENT);
            task.addParam("to", getTo());
        }
        else
        {
            task.addParam("action", App.ACTION_INCOMING);
            task.addParam("from", getFrom());
        }

        return task;
    }
}
