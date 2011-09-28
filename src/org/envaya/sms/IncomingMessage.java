package org.envaya.sms;

import android.content.Intent;
import android.net.Uri;
import org.envaya.sms.receiver.IncomingMessageRetry;

public abstract class IncomingMessage extends QueuedMessage {

    protected String from;
        
    private ProcessingState state = ProcessingState.None;
        
    public enum ProcessingState
    {
        None,           // not doing anything with this sms now... just sitting around
        Forwarding,     // currently sending to server
        Scheduled       // waiting for a while before retrying after failure forwarding
    }    
    
    public IncomingMessage(App app, String from)
    {
        super(app);
        this.from = from;
    }
    
    public ProcessingState getProcessingState()
    {
        return state;
    }
    
    public void setProcessingState(ProcessingState status)
    {
        this.state = status;
    }    
   
    
    public abstract String getDisplayType();
       
    public boolean isForwardable()
    {
        if (app.isTestMode() && !app.isTestPhoneNumber(from))
        {
            return false;
        }
        
        /* 
         * Don't forward messages from shortcodes or users with 
         * addresses like 'Vodacom' because they're likely to be 
         * messages from network, or spam. At least for network 
         * messages we should let them go in to the Messaging inbox 
         * because the person managing  this phone needs to know 
         * when they're out of credit, etc.
         * 
         * The minimum length of normal subscriber numbers doesn't 
         * seem to  be specified, but in practice seems to be 
         * at least 7 digits everywhere.
         */        
        int fromDigits = 0;
        int fromLength = from.length();
        
        for (int i = 0; i < fromLength; i++)
        {
            if (Character.isDigit(from.charAt(i)))
            {
                fromDigits++;
            }
        }
        
        return fromDigits >= 7;
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
    
    public abstract void tryForwardToServer();
}
