package org.envaya.sms.task;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.SystemClock;
import org.envaya.sms.App;
import org.envaya.sms.receiver.ReenableWifiReceiver;
import java.io.IOException;
import java.net.InetAddress;


public class CheckConnectivityTask extends AsyncTask<String, Void, Boolean> {
       
    protected App app;
    protected String hostName;
    protected int networkType;
    
    public CheckConnectivityTask(App app, String hostName, int networkType)
    {
        this.app = app;                
        this.hostName = hostName;
        this.networkType = networkType;
    }
    
    protected Boolean doInBackground(String... ignored) 
    {   
        try
        {
            Thread.sleep(1000);
            
            InetAddress addr = InetAddress.getByName(hostName);
            if (addr.isReachable(App.HTTP_CONNECTION_TIMEOUT))
            {
                return true;
            }
        }
        catch (InterruptedException ex)
        {
            
        }
        catch (IOException ex)
        {
            // just what we suspected... 
            // server not reachable on this interface
        }
        
        return false;
    }    
    
        
    @Override
    protected void onPostExecute(Boolean reachable) 
    {
        if (reachable.booleanValue())
        {
            app.log("OK");
            app.onConnectivityRestored();
        }
        else
        {
            app.log("Can't connect to "+hostName+".");
                
            WifiManager wmgr = (WifiManager)app.getSystemService(Context.WIFI_SERVICE);
                
            if (!app.isNetworkFailoverEnabled())
            {
                app.debug("Network failover disabled.");
            }
            else if (networkType == ConnectivityManager.TYPE_WIFI)
            {
                app.log("Switching from WIFI to MOBILE");                

                PendingIntent pendingIntent = PendingIntent.getBroadcast(app,
                    0,
                    new Intent(app, ReenableWifiReceiver.class),
                    0);

                // set an alarm to try restoring Wi-Fi in a little while
                AlarmManager alarm = 
                    (AlarmManager)app.getSystemService(Context.ALARM_SERVICE);

                alarm.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,                        
                    SystemClock.elapsedRealtime() + App.DISABLE_WIFI_INTERVAL,
                    pendingIntent);   

                wmgr.setWifiEnabled(false);
            }
            else if (networkType == ConnectivityManager.TYPE_MOBILE 
                    && !wmgr.isWifiEnabled())
            {
                app.log("Switching from MOBILE to WIFI");
                wmgr.setWifiEnabled(true);                    
            }
            else
            {
                app.log("Can't automatically fix connectivity.");
            }     
        }
    }

}