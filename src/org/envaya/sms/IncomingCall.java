/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.sms;

import android.net.Uri;

public class IncomingCall extends IncomingMessage {
        
    public IncomingCall(App app, String from, long timestampMillis) 
    {
        super(app, from, timestampMillis);
    }         
    
    public IncomingCall(App app)
    {
        super(app);
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
        return Uri.withAppendedPath(App.INCOMING_URI, "call/" + Uri.encode(from) + "/" + timestamp);
    }               
}
