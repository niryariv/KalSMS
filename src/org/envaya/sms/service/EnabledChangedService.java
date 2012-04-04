package org.envaya.sms.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import org.envaya.sms.App;
import org.envaya.sms.receiver.NudgeReceiver;

public class EnabledChangedService extends IntentService {
    
    private App app;
    
    public EnabledChangedService(String name)
    {
        super(name);        
    }
    
    public EnabledChangedService()
    {
        this("EnabledChangedService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        app = (App)this.getApplicationContext();
        
    }      
    
    @Override
    protected void onHandleIntent(Intent intent)
    {  
        TelephonyManager telephony = (TelephonyManager)   
            getSystemService(Context.TELEPHONY_SERVICE);                  

        startService(new Intent(app, ForegroundService.class));
        
        app.setOutgoingMessageAlarm();

        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(PendingIntent.getBroadcast(app, 0, new Intent(app, NudgeReceiver.class), 0));
        
        if (app.isEnabled())
        {
            app.getMessagingObserver().register(); 

            telephony.listen(app.getCallListener(), PhoneStateListener.LISTEN_CALL_STATE);          

            app.getDatabaseHelper().restorePendingMessages();
            
            app.getAmqpConsumer().startAsync();
        }
        else
        {
            app.getMessagingObserver().unregister();
            telephony.listen(app.getCallListener(), PhoneStateListener.LISTEN_NONE);

            app.getAmqpConsumer().stopAsync();
        }                   
    }
}
