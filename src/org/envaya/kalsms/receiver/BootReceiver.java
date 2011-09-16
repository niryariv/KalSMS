
package org.envaya.kalsms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.envaya.kalsms.App;
import org.envaya.kalsms.ui.Main;

public class BootReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        App app = (App)context.getApplicationContext();
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
