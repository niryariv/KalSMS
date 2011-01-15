package kalsms.niryariv.itp;

import kalsms.niryariv.itp.R;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Menu;


public class Prefs extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs);
	}
	
	protected void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    
    }

	
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
	    Preference pref = findPreference(key);
	    if (pref instanceof EditTextPreference) {
	    	EditTextPreference textPref = (EditTextPreference) pref;
	        pref.setSummary(textPref.getSummary());
	        Log.d("KALSMS", "textPref.getSummary(): " + textPref.getSummary());
	    }
	    if(pref instanceof CheckBoxPreference) {
	    	CheckBoxPreference checkbox = (CheckBoxPreference) pref;
	    	AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE); 
	    	Intent pintent = new Intent(this, SMSSender.class);
	    	PendingIntent pIntent = PendingIntent.getBroadcast(this,0,pintent, 0);
	    	if(checkbox.isChecked()) {
	    		alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), AlarmManager.INTERVAL_FIFTEEN_MINUTES, pIntent);
	    		Log.d("KALSMS", "alarm manager turned on");
	    	} else {
	    		alarm.cancel(pIntent);
	    		Log.d("SMS_GATEWAY", "alarm manager turned off");
	    	}
	    }
	}
	
	 // first time the Menu key is pressed
	public boolean onCreateOptionsMenu(Menu menu) {
		startActivity(new Intent(this, Prefs.class));
		return(true);
	}

	// any other time the Menu key is pressed
	public boolean onPrepareOptionsMenu(Menu menu) {
		startActivity(new Intent(this, Prefs.class));
		return(true);
	}
}

