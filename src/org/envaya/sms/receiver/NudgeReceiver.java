
package org.envaya.sms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.envaya.sms.App;

public class NudgeReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        // intentional side-effect: initialize App class to start outgoing message poll timer,
        // and send any pending incoming messages that were persisted to DB before reboot.
        
        //App app = (App)context.getApplicationContext();
        
        //app.debug("Nudged by " + intent.getAction());
        //app.log(".");
    }
}
