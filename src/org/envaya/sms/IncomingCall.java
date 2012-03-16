/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.sms;

import android.net.Uri;
import org.envaya.sms.task.ForwarderTask;

public class IncomingCall extends IncomingMessage {
    
    private long id;
    
    private static long nextId = 1;
    
    public IncomingCall(App app, String from, long timestampMillis) 
    {
        super(app, from, timestampMillis);
        this.id = getNextId();
    }         
    
    public static synchronized long getNextId()
    {
        long id = nextId;
        nextId++;
        return id;
    }
    
    public String getDisplayType()
    {
        return "call";
    }        
    
    public String getMessageType()
    {
        return App.MESSAGE_TYPE_CALL;
    }    
    
    @Override
    public boolean isForwardable()
    {
        return app.callNotificationsEnabled() && super.isForwardable();
    }
    
    public Uri getUri() 
    {
        return Uri.withAppendedPath(App.INCOMING_URI, "call/" + id);
    }               
}
