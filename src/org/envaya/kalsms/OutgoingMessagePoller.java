package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;

public class OutgoingMessagePoller extends BroadcastReceiver {

    private App app;

    private class PollerTask extends HttpTask {

        public PollerTask()
        {
            super(app);
        }
        
        @Override
        protected void handleResponse(HttpResponse response) throws Exception {
            for (OutgoingSmsMessage reply : parseResponseXML(response)) {
                app.sendSMS(reply);
            }                                        
        }                
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        app = App.getInstance(context);

        String serverUrl = app.getServerUrl();
        if (serverUrl.length() > 0) 
        {
            app.log("Checking for outgoing messages");
            new PollerTask().execute(
                new BasicNameValuePair("from", app.getPhoneNumber()),
                new BasicNameValuePair("action", App.ACTION_OUTGOING)
            );
        }            
    }
}
