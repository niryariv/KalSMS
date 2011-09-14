package org.envaya.kalsms;

import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsMessage;
import org.apache.http.message.BasicNameValuePair;

public class IncomingMessage extends QueuedMessage {

    public String from;
    public String message;
    public long timestampMillis;    

    public IncomingMessage(App app, SmsMessage sms) {
        super(app);
        this.from = sms.getOriginatingAddress();
        this.message = sms.getMessageBody();
        this.timestampMillis = sms.getTimestampMillis();
    }
    
    public IncomingMessage(App app, String from, String message, long timestampMillis) {
        super(app);
        this.from = from;
        this.message = message;
        this.timestampMillis = timestampMillis;
    }    

    public String getMessageBody()
    {
        return message;
    }
    
    public String getFrom()
    {
        return from;
    }
    
    public String getId() 
    {
        return from + ":" + message + ":" + timestampMillis;
    }    
    
    public void retryNow() {
        app.log("Retrying forwarding SMS from " + from);
        tryForwardToServer();
    }

    public void tryForwardToServer() {        
        new ForwarderTask(this,
            new BasicNameValuePair("from", getFrom()),
            new BasicNameValuePair("message", getMessageBody())            
        ).execute();
    }
    
    
    protected Intent getRetryIntent() {
        Intent intent = new Intent(app.context, IncomingMessageRetry.class);
        intent.setData(Uri.parse("kalsms://incoming/" + this.getId()));
        return intent;
    }
}
