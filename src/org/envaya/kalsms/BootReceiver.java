
package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        App app = App.getInstance(context.getApplicationContext());
        if (!app.isEnabled())
        {
            return;
        }        
                
        app.setOutgoingMessageAlarm();
        
        if (app.getLaunchOnBoot())
        {
            Intent i = new Intent(context, Main.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}
