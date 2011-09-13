package org.envaya.kalsms;

import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsMessage;
import org.apache.http.message.BasicNameValuePair;

public class IncomingMessage extends QueuedMessage {

    public SmsMessage sms;

    public IncomingMessage(App app, SmsMessage sms) {
        super(app);
        this.sms = sms;
    }

    public String getMessageBody()
    {
        return sms.getMessageBody();
    }
    
    public String getFrom()
    {
        return sms.getOriginatingAddress();
    }
    
    public String getId() 
    {
        return sms.getOriginatingAddress() + ":" + sms.getMessageBody() + ":" + sms.getTimestampMillis();
    }    
    
    public void retryNow() {
        app.log("Retrying forwarding SMS from " + sms.getOriginatingAddress());
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
