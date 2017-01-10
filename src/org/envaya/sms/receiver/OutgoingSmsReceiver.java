package org.envaya.sms.receiver;

import org.envaya.sms.App;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import java.util.ArrayList;

public class OutgoingSmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        Bundle extras = intent.getExtras();
        String to = extras.getString(App.OUTGOING_SMS_EXTRA_TO);
        ArrayList<String> bodyParts = extras.getStringArrayList(App.OUTGOING_SMS_EXTRA_BODY);
        boolean deliveryReport = extras.getBoolean(App.OUTGOING_SMS_EXTRA_DELIVERY_REPORT, false);
        
        SmsManager smgr = SmsManager.getDefault();
        
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
        ArrayList<PendingIntent> deliveryIntents = null;
        
        if (deliveryReport)
        {
            deliveryIntents = new ArrayList<PendingIntent>();
        }
        
        int numParts = bodyParts.size();
        
        for (int i = 0; i < numParts; i++)
        {
           Intent statusIntent = new Intent(App.MESSAGE_STATUS_INTENT, intent.getData());
           statusIntent.putExtra(App.STATUS_EXTRA_INDEX, i);
           statusIntent.putExtra(App.STATUS_EXTRA_NUM_PARTS, numParts);
            
           sentIntents.add(PendingIntent.getBroadcast(
                context,
                0,
                statusIntent,
                PendingIntent.FLAG_ONE_SHOT));

            if (deliveryReport)
            {
                Intent deliveryIntent = new Intent(App.MESSAGE_DELIVERY_INTENT, intent.getData());
                deliveryIntent.putExtra(App.STATUS_EXTRA_INDEX, i);
                deliveryIntent.putExtra(App.STATUS_EXTRA_NUM_PARTS, numParts);            

                deliveryIntents.add(PendingIntent.getBroadcast(
                    context,
                    0,
                    deliveryIntent,
                    PendingIntent.FLAG_ONE_SHOT));                   
            }
        }        

        smgr.sendMultipartTextMessage(to, null, bodyParts, sentIntents, deliveryIntents);
    }
}