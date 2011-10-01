package org.envaya.sms;

import android.content.Intent;
import android.net.Uri;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Inbox {
    private Map<Uri, IncomingMessage> incomingMessages = new HashMap<Uri, IncomingMessage>();
    private App app;
    
    public Inbox(App app)
    {
        this.app = app;
    }
    
    public IncomingMessage getMessage(Uri uri)
    {
        return incomingMessages.get(uri);
    }        
    
    public synchronized void forwardMessage(IncomingMessage message) {
        Uri uri = message.getUri();
        
        if (incomingMessages.containsKey(uri)) {
            app.log("Duplicate incoming "+message.getDisplayType()+", skipping");
            return;
        }

        incomingMessages.put(uri, message);

        app.log("Received "+message.getDisplayType()+" from " + message.getFrom());
        
        message.setProcessingState(IncomingMessage.ProcessingState.Forwarding);
        message.tryForwardToServer();        
        
        notifyChanged();
    }                       
    
    public synchronized void retryForwardMessage(IncomingMessage message)
    {
        IncomingMessage.ProcessingState state = message.getProcessingState();
        
        if (state == IncomingMessage.ProcessingState.Scheduled
            || state == IncomingMessage.ProcessingState.None)
        {                              
            message.setProcessingState(IncomingMessage.ProcessingState.Forwarding);
            message.tryForwardToServer();
            
            notifyChanged();
        }
    }
    
    public synchronized void deleteMessage(IncomingMessage message)
    {
        incomingMessages.remove(message.getUri());
        app.log("SMS from " + message.getFrom() + " deleted");
        notifyChanged();
    }    
    
    public synchronized void messageFailed(IncomingMessage message) 
    {        
        message.setProcessingState(IncomingMessage.ProcessingState.None);
        
        if (message.scheduleRetry())
        {
            message.setProcessingState(IncomingMessage.ProcessingState.Scheduled);
        }
        notifyChanged();
    }        
    
    public synchronized void messageForwarded(IncomingMessage message) {
        
        message.setProcessingState(IncomingMessage.ProcessingState.Forwarded);
        
        Uri uri = message.getUri();
        incomingMessages.remove(uri);
            
        notifyChanged();
        
        if (message instanceof IncomingMms)
        {
            IncomingMms mms = (IncomingMms)message;
            if (!app.getKeepInInbox())
            {
                app.log("Deleting MMS " + mms.getId() + " from inbox...");
                app.getMmsUtils().deleteFromInbox(mms);
            }            
        }        
    }        
    
    private void notifyChanged()
    {
        app.sendBroadcast(new Intent(App.INBOX_CHANGED_INTENT));
    }
    
    public synchronized void retryAll() {
        for (IncomingMessage message : incomingMessages.values()) {            
            retryForwardMessage(message);
        }
    }
    
    public synchronized int size() {        
        return incomingMessages.size();
    }  
    
    public synchronized Collection<IncomingMessage> getMessages()
    {
        return incomingMessages.values();
    }    
}