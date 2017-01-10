
package org.envaya.sms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.receiver.DequeueOutgoingMessageReceiver;
import org.envaya.sms.task.HttpTask;

public class Outbox {
    private Map<Uri, OutgoingMessage> outgoingMessages = new HashMap<Uri, OutgoingMessage>();    
    
    private App app;   

    // keep track of some recent message URIs to avoid sending duplicate messages
    // (e.g. if send_status HTTP request fails for some reason)
    public static final int MAX_RECENT_URIS = 100;    
    private Set<Uri> recentSentMessageUris = new HashSet<Uri>();
    private Queue<Uri> recentSentMessageUriOrder = new LinkedList<Uri>();
        
    // number of outgoing messages that are currently being sent and waiting for
    // messageSent or messageFailed to be called
    private int numSendingOutgoingMessages = 0;
     
    // cache of next time we can send the first message in queue without
    // exceeding android sending limit
    private long nextValidOutgoingTime;   
    
    // enqueue outgoing messages in descending order by priority, ascending by local id
    // (order in which message was received)
    private PriorityQueue<OutgoingMessage> outgoingQueue = new PriorityQueue<OutgoingMessage>(10, 
        new Comparator<OutgoingMessage>() { 
            public int compare(OutgoingMessage t1, OutgoingMessage t2)
            {
                int pri2 = t2.getPriority();
                int pri1 = t1.getPriority();
                
                if (pri1 != pri2)
                {
                    return pri2 - pri1;
                }
                
                int order2 = t2.getLocalId();
                int order1 = t1.getLocalId();
                
                return order1 - order2;
            }
        }            
    );
    
    public Outbox(App app)
    {
        this.app = app;
    }    
    
    private void notifyMessageStatus(OutgoingMessage sms, final String status, final String errorMessage) {
        final String serverId = sms.getServerId();
               
        String logMessage;
        if (status.equals(App.STATUS_SENT)) {
            logMessage = "sent successfully";
        } else if (status.equals(App.STATUS_FAILED)) {
            logMessage = "could not be sent (" + errorMessage + ")";
        } else if (status.equals(App.STATUS_CANCELLED)) {
            logMessage = "cancelled";
        }
        else {
            logMessage = "queued";
        }
        String smsDesc = sms.getDisplayType();

        if (serverId != null) {
            app.log("Notifying server " + smsDesc + " " + logMessage);

            HttpTask task = new HttpTask(app,
                new BasicNameValuePair("id", serverId),
                new BasicNameValuePair("status", status),
                new BasicNameValuePair("error", errorMessage),
                new BasicNameValuePair("action", App.ACTION_SEND_STATUS)                    
            );
            task.setRetryOnConnectivityError(true);
            task.execute();
            
        } else {
            app.log(smsDesc + " " + logMessage);
        }
    }
    
    public synchronized void retryAll() 
    {
        nextValidOutgoingTime = 0;        

        for (OutgoingMessage sms : outgoingMessages.values()) {
            enqueueMessage(sms);
        }
        maybeDequeueMessage();
    }  

    public OutgoingMessage getMessage(Uri uri)
    {
        return outgoingMessages.get(uri);
    }        
    
    public synchronized void messageSent(OutgoingMessage message)
    {
        message.setProcessingState(OutgoingMessage.ProcessingState.Sent);
        
        message.clearSendTimeout();
        
        notifyMessageStatus(message, App.STATUS_SENT, "");
        
        Uri uri = message.getUri();
        
        outgoingMessages.remove(uri);
        
        addRecentSentMessage(message);
        
        app.getDatabaseHelper().deletePendingMessage(message);
        
        notifyChanged();        
        
        numSendingOutgoingMessages--;
        maybeDequeueMessage();
    }
    
    private synchronized void addRecentSentMessage(OutgoingMessage message)
    {        
        if (message.getServerId() != null)
        {
            Uri uri = message.getUri();
            
            recentSentMessageUris.add(uri);            
            recentSentMessageUriOrder.add(uri);
            
            if (recentSentMessageUriOrder.size() > MAX_RECENT_URIS)
            {
                Uri oldUri = recentSentMessageUriOrder.remove();
                recentSentMessageUris.remove(oldUri);
            }
        }
    }
        
    public synchronized void messageFailed(OutgoingMessage message, String error)
    {
        message.clearSendTimeout();
        
        if (message.scheduleRetry()) 
        {
            message.setProcessingState(OutgoingMessage.ProcessingState.Scheduled);
        }
        else
        {
            message.setProcessingState(OutgoingMessage.ProcessingState.None);   
        }
        notifyChanged();
        notifyMessageStatus(message, App.STATUS_FAILED, error);

        app.getDatabaseHelper().deletePendingMessage(message);
        
        numSendingOutgoingMessages--;
        maybeDequeueMessage();        
    }

    public synchronized void sendMessage(OutgoingMessage message) {
                                
        try
        {
            message.validate();                
        }
        catch (ValidationException ex)
        {
            notifyMessageStatus(message, App.STATUS_FAILED, ex.getMessage());                        
            return;                
        }
        
        Uri uri = message.getUri();
        if (outgoingMessages.containsKey(uri)) {
            app.debug("Duplicate outgoing " + message.getDisplayType() + ", skipping");
            return;
        }
        
        if (recentSentMessageUris.contains(uri))
        {
            app.debug("Outgoing " + message.getDisplayType() + " already sent, re-notifying server");   
            notifyMessageStatus(message, App.STATUS_SENT, "");
            return;
        }

        outgoingMessages.put(uri, message);  
        
        app.getDatabaseHelper().insertPendingMessage(message);
        
        enqueueMessage(message);
    }
    
    public synchronized void deleteMessage(OutgoingMessage message)
    {
        outgoingMessages.remove(message.getUri());
        
        if (message.getProcessingState() == OutgoingMessage.ProcessingState.Queued)
        {
            outgoingQueue.remove(message);
        }
        else if (message.getProcessingState() == OutgoingMessage.ProcessingState.Sending)
        {
            numSendingOutgoingMessages--;
        }        
        
        app.getDatabaseHelper().deletePendingMessage(message);
        
        notifyMessageStatus(message, App.STATUS_CANCELLED, 
                "deleted by user");
        app.log(message.getDescription() + " deleted");
        notifyChanged();
    }    
    
    public synchronized void maybeDequeueMessage()
    {
        long now = System.currentTimeMillis();        
        if (nextValidOutgoingTime <= now && numSendingOutgoingMessages < 2)
        {
            OutgoingMessage message = outgoingQueue.peek();
            
            if (message == null)
            {
                return;
            }
            
            OutgoingMessage.ScheduleInfo schedule = message.scheduleSend();
            
            if (!schedule.now)
            {
                nextValidOutgoingTime = schedule.time;
            
                if (nextValidOutgoingTime <= now) // should never happen
                {
                    nextValidOutgoingTime = now + 2000;
                }

                long diff = nextValidOutgoingTime - now;

                app.log("Waiting for " + (diff/1000) + " seconds");

                AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);

                Intent intent = new Intent(app, DequeueOutgoingMessageReceiver.class);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(app,
                    0,
                    intent,
                    0);

                alarm.set(
                    AlarmManager.RTC_WAKEUP,
                    nextValidOutgoingTime,
                    pendingIntent);

                return;
            }

            outgoingQueue.poll();            
            numSendingOutgoingMessages++;

            message.setProcessingState(OutgoingMessage.ProcessingState.Sending);
            message.send(schedule);
            
            message.setSendTimeout();
            
            notifyChanged();
        }          
    }
    
    public synchronized void enqueueMessage(OutgoingMessage message) 
    {
        OutgoingMessage.ProcessingState state = message.getProcessingState();
        
        if (state == OutgoingMessage.ProcessingState.Scheduled
            || state == OutgoingMessage.ProcessingState.None) 
        {        
            outgoingQueue.add(message);
            message.setProcessingState(OutgoingMessage.ProcessingState.Queued);
            notifyChanged();
            maybeDequeueMessage();                       
        }
    }
    
    private void notifyChanged()
    {
        app.sendBroadcast(new Intent(App.OUTBOX_CHANGED_INTENT));
    }    
    
    public synchronized int size() {        
        return outgoingMessages.size();
    }    
    
    public synchronized Collection<OutgoingMessage> getMessages()
    {
        return outgoingMessages.values();
    }
}
