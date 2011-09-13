
package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OutgoingMessageRetry extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent) 
    {        
        App app = App.getInstance(context.getApplicationContext());        
        app.retryOutgoingMessage(intent.getData().getLastPathSegment());        
    }
}    
