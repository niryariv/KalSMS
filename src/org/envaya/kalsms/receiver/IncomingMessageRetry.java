
package org.envaya.kalsms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.envaya.kalsms.App;

public class IncomingMessageRetry extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent) 
    {        
        App app = (App) context.getApplicationContext();
        app.retryIncomingMessage(intent.getData().getLastPathSegment());        
    }        
}    
