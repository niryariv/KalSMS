
package org.envaya.kalsms;

import android.net.Uri;
import android.telephony.SmsMessage;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.kalsms.task.ForwarderTask;


public class IncomingSms extends IncomingMessage {
    
    protected String message;
    protected long timestampMillis;           
    
    // constructor for SMS retrieved from android.provider.Telephony.SMS_RECEIVED intent
    public IncomingSms(App app, SmsMessage sms) {
        super(app, sms.getOriginatingAddress());
        this.message = sms.getMessageBody();
        this.timestampMillis = sms.getTimestampMillis();
    }
    
    // constructor for SMS retrieved from Messaging inbox
    public IncomingSms(App app, String from, String message, long timestampMillis) {
        super(app, from);
        this.message = message;
        this.timestampMillis = timestampMillis;
    }        
    
    public String getMessageBody()
    {
        return message;
    }
    
    public String getDisplayType()
    {
        return "SMS";
    }    
    
    public Uri getUri() 
    {
        return Uri.withAppendedPath(App.INCOMING_URI, 
                "sms/" + 
                Uri.encode(from) + "/" 
                + timestampMillis + "/" + 
                Uri.encode(message));
    }        

    public void tryForwardToServer() {        
        new ForwarderTask(this,
            new BasicNameValuePair("from", getFrom()),
            new BasicNameValuePair("message_type", App.MESSAGE_TYPE_SMS),
            new BasicNameValuePair("message", getMessageBody())            
        ).execute();
    }
    
}
