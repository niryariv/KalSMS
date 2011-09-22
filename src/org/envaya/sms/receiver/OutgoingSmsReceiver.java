package org.envaya.sms.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import org.envaya.sms.App;

public class OutgoingSmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        Bundle extras = intent.getExtras();
        String to = extras.getString(App.OUTGOING_SMS_EXTRA_TO);
        String body = extras.getString(App.OUTGOING_SMS_EXTRA_BODY);
        
        SmsManager smgr = SmsManager.getDefault();

        Intent statusIntent = new Intent(App.MESSAGE_STATUS_INTENT, intent.getData());

        PendingIntent sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                statusIntent,
                PendingIntent.FLAG_ONE_SHOT);

        smgr.sendTextMessage(to, null, body, sentIntent, null);                         
    }
}
