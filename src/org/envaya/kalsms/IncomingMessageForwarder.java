package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import org.apache.http.HttpResponse;

import org.apache.http.message.BasicNameValuePair;

public class IncomingMessageForwarder extends BroadcastReceiver {

    private App app;   

    @Override
    // source: http://www.devx.com/wireless/Article/39495/1954
    public void onReceive(Context context, Intent intent) {
        try {
            this.app = App.getInstance(context.getApplicationContext());

            String action = intent.getAction();

            if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
                
                for (SmsMessage sms : getMessagesFromIntent(intent)) {
                    app.sendMessageToServer(sms);

                    //DeleteSMSFromInbox(context, mesg);
                }
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