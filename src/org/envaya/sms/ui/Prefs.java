package org.envaya.sms.ui;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.Menu;
import org.envaya.sms.App;
import org.envaya.sms.R;

public class Prefs extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    private App app;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
        
        app = (App) getApplication();
        
        PreferenceScreen screen = this.getPreferenceScreen();
        int numPrefs = screen.getPreferenceCount();
        
        for(int i=0; i < numPrefs;i++)
        {
            updatePrefSummary(screen.getPreference(i));
        }
    }    

    @Override 
    protected void onResume(){
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
        

        if (key.equals("outgoing_interval"))
        {            
            app.setOutgoingMessageAlarm();
        }
        else if (key.equals("wifi_sleep_policy"))
        {
            int value;
            String valueStr = sharedPreferences.getString("wifi_sleep_policy", "screen");
            if ("screen".equals(valueStr))
            {
                value = Settings.System.WIFI_SLEEP_POLICY_DEFAULT;
            }
            else if ("plugged".equals(valueStr))
            {
                value = Settings.System.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED;
            }
            else 
            {
                value = Settings.System.WIFI_SLEEP_POLICY_NEVER;
            }
            
            Settings.System.putInt(getContentResolver(), 
                Settings.System.WIFI_SLEEP_POLICY, value);
        }
        else if (key.equals("server_url"))
        {
            String serverUrl = sharedPreferences.getString("server_url", "");
            
            // assume http:// scheme if none entered
            if (serverUrl.length() > 0 && !serverUrl.contains("://"))
            {
                sharedPreferences.edit()
                    .putString("server_url", "http://" + serverUrl)
                    .commit();
            }
            
            app.log("Server URL changed to: " + app.getDisplayString(app.getServerUrl()));
        }
        else if (key.equals("call_notifications"))
        {
            app.log("Call notifications changed to: " + (app.callNotificationsEnabled() ? "ON": "OFF"));
        }
        else if (key.equals("phone_number"))
        {
            app.log("Phone number changed to: " + app.getDisplayString(app.getPhoneNumber()));
        }
        else if (key.equals("test_mode"))
        {
            app.log("Test mode changed to: " + (app.isTestMode() ? "ON": "OFF"));
        }        
        else if (key.equals("password"))
        {
            app.log("Password changed");
        }
        else if (key.equals("enabled"))
        {
            app.log(app.isEnabled() ? "SMS Gateway started." : "SMS Gateway stopped.");
            app.enabledChanged();
        }
        
        updatePrefSummary(findPreference(key));
    }    

    private void updatePrefSummary(Preference p)
    {
        String key = p.getKey();
        
        if ("wifi_sleep_policy".equals(key))
        {       
            int sleepPolicy;
            
            try
            {
                sleepPolicy = Settings.System.getInt(this.getContentResolver(), 
                    Settings.System.WIFI_SLEEP_POLICY);                
            }
            catch (SettingNotFoundException ex)
            {
                sleepPolicy = Settings.System.WIFI_SLEEP_POLICY_DEFAULT;
            }               
            
            switch (sleepPolicy)
            {
                case Settings.System.WIFI_SLEEP_POLICY_DEFAULT:
                    p.setSummary("Wi-Fi will disconnect when the phone sleeps");
                    break;
                case Settings.System.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED:
                    p.setSummary("Wi-Fi will disconnect when the phone sleeps unless it is plugged in");
                    break;
                case Settings.System.WIFI_SLEEP_POLICY_NEVER:
                    p.setSummary("Wi-Fi will stay connected when the phone sleeps");
                    break;
            }
        }
        else if ("help".equals(key))
        {
            p.setSummary(app.getPackageInfo().versionName);
        }
        else if (p instanceof ListPreference) {
            p.setSummary(((ListPreference)p).getEntry()); 
        }
        else if (p instanceof EditTextPreference) {
            
            EditTextPreference textPref = (EditTextPreference)p;
            String text = textPref.getText();
            if (text == null || text.equals(""))
            {            
                p.setSummary("(not set)"); 
            }            
            else if (p.getKey().equals("password"))
            {
                p.setSummary("********");
            }
            else
            {
                p.setSummary(text);
            }
        }
    }    

    // any other time the Menu key is pressed
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        this.finish();
        return (true);
    }
}
