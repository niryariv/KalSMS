package org.envaya.sms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.App;
import org.envaya.sms.task.HttpTask;

public class DeviceStatusReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent) 
    {        
        App app = (App) context.getApplicationContext();        
        if (!app.isEnabled())
        {
            return;
        }
        
        String action = intent.getAction();
        
        String status = "";
        
        if (Intent.ACTION_POWER_CONNECTED.equals(action))
        {
            status = App.DEVICE_STATUS_POWER_CONNECTED;
            app.log("Power connected");
        }
        else if (Intent.ACTION_POWER_DISCONNECTED.equals(action))
        {
            status = App.DEVICE_STATUS_POWER_DISCONNECTED;
            app.log("Power disconnected");
        }
        else if (Intent.ACTION_BATTERY_LOW.equals(action))
        {
            status = App.DEVICE_STATUS_BATTERY_LOW;
            app.log("Battery low");
        }
        else if (Intent.ACTION_BATTERY_OKAY.equals(action))
        {
            status = App.DEVICE_STATUS_BATTERY_OKAY;
            app.log("Battery okay");
        }

        HttpTask task = new HttpTask(app, 
            new BasicNameValuePair("action", App.ACTION_DEVICE_STATUS),
            new BasicNameValuePair("status", status)
        );
        task.setRetryOnConnectivityError(true);
        task.execute();
    }
}    