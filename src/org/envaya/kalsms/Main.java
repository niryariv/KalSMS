package org.envaya.kalsms;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.widget.TextView;

public class Main extends Activity {   
	
    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {  
            showLogMessage(intent.getExtras().getString("message"));
        }
    };

    public void showLogMessage(String message)
    {
        TextView info = (TextView) Main.this.findViewById(R.id.info);        
        if (message != null)
        {                                                
            info.append(message + "\n");
        }        
    }
    
    public void onResume() {
        App.debug("RESUME");
        super.onResume();                                		
    }	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        App.debug("STARTED");
        
        setContentView(R.layout.main);
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);               
        
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE); 
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this,
                0,
                new Intent(this, OutgoingMessagePoller.class), 
                0);
        
        alarm.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime(),                                
            App.OUTGOING_POLL_SECONDS * 1000, 
            pendingIntent);        
        
        App app = new App(this.getApplication());
        
        TextView info = (TextView) this.findViewById(R.id.info);
        
        info.setText(Html.fromHtml("<b>SMS Gateway running.</b><br />"));                        
        
        showLogMessage("Server URL is: " + app.getServerUrl());
        showLogMessage("Your phone number is: " + app.getPhoneNumber());
        showLogMessage("Checking for outgoing messages every " + App.OUTGOING_POLL_SECONDS + " sec");
        
        info.append(Html.fromHtml("<b>Press Menu to edit settings.</b><br />"));        
        
        info.setMovementMethod(new ScrollingMovementMethod());        
        
        IntentFilter logReceiverFilter = new IntentFilter();
        
        logReceiverFilter.addAction(App.LOG_INTENT);
        registerReceiver(logReceiver, logReceiverFilter);        
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
	    
    @Override
    protected void onStop(){
    	// dont do much with this, atm..
    	super.onStop();
    }
    
}