
package org.envaya.sms;

import org.envaya.sms.receiver.OutgoingMessageRetry;
import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;

public class OutgoingMessage extends QueuedMessage {
    
    private String serverId;    
    private String message;
    private String from;
    private String to;     
    private int priority;
    private int localId;        
    private static int nextLocalId = 1;            
    
    private ProcessingState state = ProcessingState.None;
        
    public enum ProcessingState
    {
        None,           // not doing anything with this sms now... just sitting around
        Queued,         // in the outgoing queue waiting to be sent
        Sending,        // passed to an expansion pack, waiting for status notification
        Scheduled       // waiting for a while before retrying after failure sending
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
    
    public void setProcessingState(ProcessingState status)
    {
        this.state = status;
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
    
    public String getLogName()
    {
        return (serverId == null) ? "SMS reply" : ("SMS id=" + serverId);
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
    
    public void trySend(ArrayList<String> bodyParts, String packageName)
    {
        if (numRetries == 0)
        {
            app.log("Sending " + getLogName() + " to " + getTo());
        }
        else
        {        
            app.log("Retrying sending " + getLogName() + " to " + getTo());
        }
                
        int numParts = bodyParts.size();
        if (numParts > 1)
        {
            app.log("(Multipart message with "+numParts+" parts)");
        }        

        Intent intent = new Intent(packageName + App.OUTGOING_SMS_INTENT_SUFFIX, this.getUri());
        intent.putExtra(App.OUTGOING_SMS_EXTRA_DELIVERY_REPORT, false);
        intent.putExtra(App.OUTGOING_SMS_EXTRA_TO, getTo());
        intent.putExtra(App.OUTGOING_SMS_EXTRA_BODY, bodyParts);
        
        app.sendBroadcast(intent, "android.permission.SEND_SMS");
    }

    protected Intent getRetryIntent() {
        Intent intent = new Intent(app, OutgoingMessageRetry.class);
        intent.setData(this.getUri());
        return intent;
    }       
}
