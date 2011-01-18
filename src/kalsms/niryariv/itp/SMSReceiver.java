package kalsms.niryariv.itp;

import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {
	
	@Override
	// source: http://www.devx.com/wireless/Article/39495/1954
	public void onReceive(Context context, Intent intent) {
		if (!intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
			return;
		}

		// get settings
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		TargetUrlRequest url = new TargetUrlRequest();

		String identifier = settings.getString("pref_identifier", "");
		String targetUrl =  settings.getString("pref_target_url", "");

		SmsMessage msgs[] = getMessagesFromIntent(intent);

		for (int i = 0; i < msgs.length; i++) {
			SmsMessage mesg = msgs[i];
			String message = mesg.getDisplayMessageBody();
			String sender = mesg.getDisplayOriginatingAddress();
			
			if (message != null && message.length() > 0 
					&& (message.toLowerCase().startsWith(identifier) || identifier.trim() == "")) {
				Log.d("KALSMS", "MSG RCVD:\"" + message + "\" from: " + sender);
				
				// send the message to the URL
				String resp = url.openURL(sender, message, targetUrl, false).toString();
				Log.d("KALSMS", "RESP:\"" + resp);
				
				// SMS back the response
				if (resp.trim().length() > 0) {
					ArrayList<ArrayList<String>> items = url.parseXML(resp);
					url.sendMessages(items);
				}
				// delete SMS from inbox, to prevent it from filling up
				DeleteSMSFromInbox(context, mesg);
			}
		}
	}

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

	
	// from http://github.com/dimagi/rapidandroid 
	// source: http://www.devx.com/wireless/Article/39495/1954
	private SmsMessage[] getMessagesFromIntent(Intent intent) {
		SmsMessage retMsgs[] = null;
		Bundle bdl = intent.getExtras();
		try {
			Object pdus[] = (Object[]) bdl.get("pdus");
			retMsgs = new SmsMessage[pdus.length];
			for (int n = 0; n < pdus.length; n++) {
				byte[] byteData = (byte[]) pdus[n];
				retMsgs[n] = SmsMessage.createFromPdu(byteData);
			}
		} catch (Exception e) {
			Log.e("KALSMS", "GetMessages ERROR\n" + e);
		}
		return retMsgs;
	}
}