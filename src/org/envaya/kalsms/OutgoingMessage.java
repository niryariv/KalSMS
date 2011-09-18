
package org.envaya.kalsms;

import org.envaya.kalsms.receiver.OutgoingMessageRetry;
import org.envaya.kalsms.receiver.MessageStatusNotifier;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsManager;

public class OutgoingMessage extends QueuedMessage {
    
    private String serverId;    
    private String message;
    private String from;
    private String to;     
    
    private String localId;
        
    private static int nextLocalId = 1;            
    
    public OutgoingMessage(App app)
    {
        super(app);
        this.localId = "_o" + getNextLocalId();
    }
    
    static synchronized int getNextLocalId()
    {
        return nextLocalId++;
    }
    
    public Uri getUri()
    {
        return Uri.withAppendedPath(App.OUTGOING_URI, ((serverId == null) ? localId : serverId));
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

    public void retryNow() {
        app.log("Retrying sending " + getLogName() + " to " + getTo());
        trySend();
    }
    
    public void trySend()
    {
        SmsManager smgr = SmsManager.getDefault();

        Intent intent = new Intent(app, MessageStatusNotifier.class);
        intent.setData(this.getUri());

        PendingIntent sentIntent = PendingIntent.getBroadcast(
                app,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT);

        smgr.sendTextMessage(getTo(), null, getMessageBody(), sentIntent, null);        
    }

    protected Intent getRetryIntent() {
        Intent intent = new Intent(app, OutgoingMessageRetry.class);
        intent.setData(this.getUri());
        return intent;
    }   
}
