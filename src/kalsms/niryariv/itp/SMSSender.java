package kalsms.niryariv.itp;

import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

public class SMSSender extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		
		// acquiring the wake clock to prevent device from sleeping while request is processed
		final PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wake = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "http_request");
		wake.acquire();

		// get settings
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		String targetUrl =  settings.getString("pref_target_url", "");
		Log.d("KALSMS", "url:\"" + targetUrl);	
		TargetUrlRequest url = new TargetUrlRequest();
		// send the message to the URL
		String resp = url.openURL("","",targetUrl).toString();
		
		Log.d("KALSMS", "RESP:\"" + resp);
		
		// SMS back the response
		if (resp.trim().length() > 0) {
			ArrayList<ArrayList<String>> items = url.parseXML(resp);
			
			SmsManager smgr = SmsManager.getDefault();
			
			for (int j = 0; j < items.size(); j++) {
				String sendTo = items.get(j).get(0);
				String sendMsg = items.get(j).get(1);
				
				try {
					Log.d("KALSMS", "SEND MSG:\"" + sendMsg + "\" TO: " + sendTo);
					smgr.sendTextMessage(sendTo, null, sendMsg, null, null);
				} catch (Exception ex) {
					Log.d("KALSMS", "SMS FAILED");
				}
			}
		}
		wake.release();
	}
}
