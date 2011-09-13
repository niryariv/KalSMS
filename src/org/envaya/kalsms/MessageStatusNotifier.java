/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import org.apache.http.message.BasicNameValuePair;

public class MessageStatusNotifier extends BroadcastReceiver {

    private App app;
        
    public void notifyStatus(String serverId, String status, String errorMessage)
    {
        String logMessage;
        if (status.equals(App.STATUS_SENT))
        {
            logMessage = "sent successfully"; 
        }
        else if (status.equals(App.STATUS_FAILED))
        {
            logMessage = "could not be sent (" + errorMessage + ")";
        }
        else
        {
            logMessage = "queued";
        }
        String smsDesc = serverId == null ? "SMS reply" : ("SMS id=" + serverId);
        
        if (serverId != null)
        {        
            app.log("Notifying server " + smsDesc + " " + logMessage);            

            new HttpTask(app).execute(
                new BasicNameValuePair("id", serverId),
                new BasicNameValuePair("status", status),
                new BasicNameValuePair("error", errorMessage),
                new BasicNameValuePair("action", App.ACTION_SEND_STATUS)
            );
        }
        else
        {
            app.log(smsDesc + " " + logMessage);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        app = App.getInstance(context);

        String serverId = intent.getExtras().getString("serverId");
        
        switch (getResultCode()) {
            case Activity.RESULT_OK:                
                this.notifyStatus(serverId, App.STATUS_SENT, "");
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                this.notifyStatus(serverId, App.STATUS_FAILED, "generic failure");
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                this.notifyStatus(serverId, App.STATUS_FAILED, "radio off");
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                this.notifyStatus(serverId, App.STATUS_FAILED, "no service");
                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:
                this.notifyStatus(serverId, App.STATUS_FAILED, "null PDU");
                break;
            default:
                this.notifyStatus(serverId, App.STATUS_FAILED, "unknown error");
                break;
        }
    }
}
