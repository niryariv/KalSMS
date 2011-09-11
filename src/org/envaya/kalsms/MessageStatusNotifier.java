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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class MessageStatusNotifier extends BroadcastReceiver {

    private App app;
    
    public void notifySuccess(String serverId)
    {
        if (serverId != null)
        {        
            try {
                app.log("Notifying server of sent SMS id=" + serverId);
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("from", app.getPhoneNumber()));
                params.add(new BasicNameValuePair("secret", app.getPassword()));
                params.add(new BasicNameValuePair("id", serverId));

                HttpClient client = new DefaultHttpClient();
                HttpPost post = new HttpPost(app.getSendStatusUrl());
                post.setEntity(new UrlEncodedFormEntity(params));            

                client.execute(post);                
            }
            catch (IOException ex) 
            {
                app.logError("Error while notifying server of outgoing message", ex);
            }        
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        app = new App(context);

        String serverId = intent.getExtras().getString("serverId");
        
        String desc = serverId == null ? "SMS reply" : ("SMS id=" + serverId);
        
        switch (getResultCode()) {
            case Activity.RESULT_OK:                
                app.log(desc + " sent successfully");
                this.notifySuccess(serverId);
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                app.log(desc + " could not be sent (generic failure)");
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                app.log(desc + " could not be sent (radio off)");
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                app.log(desc + " could not be sent (no service)");
                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:
                app.log(desc + " could not be sent (null PDU");
                break;
            default:
                app.log("SMS could not be sent (unknown error)");
                break;
        }
    }
}
