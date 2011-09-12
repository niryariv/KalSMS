package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpResponse;

import org.apache.http.message.BasicNameValuePair;

public class IncomingMessageForwarder extends BroadcastReceiver {

    private App app;
    
    private List<SmsStatus> retryList = new ArrayList<SmsStatus>();
    
    private class SmsStatus
    {
        public SmsMessage smsMessage;
        public long nextAttemptTime;
        public int numAttempts = 0;
        
    }

    private class ForwarderTask extends HttpTask {

        private SmsMessage originalSms;

        public ForwarderTask(SmsMessage originalSms) {
            super(app);
            this.originalSms = originalSms;
        }

        @Override
        protected String getDefaultToAddress()
        {
            return originalSms.getOriginatingAddress();
        }        
                
        @Override
        protected void handleResponse(HttpResponse response) throws Exception {
            for (OutgoingSmsMessage reply : parseResponseXML(response)) {
                app.sendSMS(reply);
            }                                        
        }
    }        
    
    public void sendMessageToServer(SmsMessage sms) 
    {
        String serverUrl = app.getServerUrl();
        String message = sms.getMessageBody();
        String sender = sms.getOriginatingAddress();
        String recipient = app.getPhoneNumber();

        app.log("Received SMS from " + sender);

        if (serverUrl.length() == 0) {
            app.log("Can't forward SMS to server; Server URL not set");                        
        } else {
            app.log("Forwarding incoming SMS to server");

            new ForwarderTask(sms).execute(
                new BasicNameValuePair("from", sender),
                new BasicNameValuePair("to", recipient),
                new BasicNameValuePair("message", message),
                new BasicNameValuePair("action", App.ACTION_INCOMING)
            );
        }
    }

    public void smsReceived(Intent intent) {

        for (SmsMessage sms : getMessagesFromIntent(intent)) {
            sendMessageToServer(sms);

            //DeleteSMSFromInbox(context, mesg);
        }

    }

    @Override
    // source: http://www.devx.com/wireless/Article/39495/1954
    public void onReceive(Context context, Intent intent) {
        try {
            this.app = App.getInstance(context);

            String action = intent.getAction();

            if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
                smsReceived(intent);
            }
        } catch (Throwable ex) {
            app.logError("Unexpected error in IncomingMessageForwarder", ex, true);
        }
    }

    /*
    private void DeleteSMSFromInbox(Context context, SmsMessage mesg) {
    Log.d("KALSMS", "try to delete SMS");
    try {
    Uri uriSms = Uri.parse("content://sms/inbox");
    StringBuilder sb = new StringBuilder();
    sb.append("address='" + mesg.getOriginatingAddress() + "' AND ");
    sb.append("body='" + mesg.getMessageBody() + "'");
    Cursor c = context.getContentResolver().query(uriSms, null, sb.toString(), null, null);
    c.moveToFirst();
    int thread_id = c.getInt(1);
    context.getContentResolver().delete(Uri.parse("content://sms/conversations/" + thread_id), null, null);
    c.close();
    } catch (Exception ex) {
    // deletions don't work most of the time since the timing of the
    // receipt and saving to the inbox
    // makes it difficult to match up perfectly. the SMS might not be in
    // the inbox yet when this receiver triggers!
    Log.d("SmsReceiver", "Error deleting sms from inbox: " + ex.getMessage());
    }
    }
     */
    // from http://github.com/dimagi/rapidandroid 
    // source: http://www.devx.com/wireless/Article/39495/1954
    private SmsMessage[] getMessagesFromIntent(Intent intent) {
        SmsMessage retMsgs[] = null;
        Bundle bdl = intent.getExtras();
        Object pdus[] = (Object[]) bdl.get("pdus");
        retMsgs = new SmsMessage[pdus.length];
        for (int n = 0; n < pdus.length; n++) {
            byte[] byteData = (byte[]) pdus[n];
            retMsgs[n] = SmsMessage.createFromPdu(byteData);
        }
        return retMsgs;
    }
}