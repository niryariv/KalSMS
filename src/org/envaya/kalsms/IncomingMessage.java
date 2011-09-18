package org.envaya.kalsms;

import android.content.Intent;
import android.net.Uri;
import org.envaya.kalsms.receiver.IncomingMessageRetry;

public abstract class IncomingMessage extends QueuedMessage {

    protected String from;
    
    public IncomingMessage(App app, String from)
    {
        super(app);
        this.from = from;
    }
    
    public abstract String getDisplayType();
       
    public boolean isForwardable()
    {
        /* 
         * don't forward messages from shortcodes
         * because they're likely to be spam or messages from network
         */
        return from.length() > 5;
    }
    
    public String getFrom()
    {
        return from;
    }
    
    public void retryNow() {
        app.log("Retrying forwarding message from " + from);
        tryForwardToServer();
    }    
 
    protected Intent getRetryIntent() {
        Intent intent = new Intent(app, IncomingMessageRetry.class);
        intent.setData(this.getUri());
        return intent;
    }    
    
    public abstract void tryForwardToServer();
}
