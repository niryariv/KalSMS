package org.envaya.sms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.envaya.sms.App;

public class ExpansionPackInstallReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent) 
    {        
        App app = (App) context.getApplicationContext();        
        
        String packageName = intent.getData().getSchemeSpecificPart();
        
        if (packageName != null && packageName.startsWith(context.getPackageName() + ".pack"))
        {
            app.updateExpansionPacks();
        }
    }        
}    