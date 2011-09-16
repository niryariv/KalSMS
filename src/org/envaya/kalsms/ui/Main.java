package org.envaya.kalsms.ui;

import org.envaya.kalsms.task.HttpTask;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.kalsms.App;
import org.envaya.kalsms.R;

public class Main extends Activity {   
	
    private App app;
    
    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {  
            updateLogView();
        }
    };
    
    private class TestTask extends HttpTask
    {
        public TestTask() {
            super(Main.this.app, new BasicNameValuePair("action", App.ACTION_OUTGOING));   
        }
        
        @Override
        protected void handleResponse(HttpResponse response) throws Exception 
        {        
            parseResponseXML(response);            
            app.log("Server connection OK!");            
        }
    }
        
    private long lastLogTime = 0;

    public void updateLogView()
    {           
        final ScrollView scrollView = (ScrollView) this.findViewById(R.id.info_scroll);
        TextView info = (TextView) this.findViewById(R.id.info);
        
        info.setText(app.getDisplayedLog());
        
        scrollView.post(new Runnable() { public void run() { 
            scrollView.fullScroll(View.FOCUS_DOWN);
        } });
    }
            
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        app = (App) getApplication();
                
        setContentView(R.layout.main);
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);               
                        
        TextView info = (TextView) this.findViewById(R.id.info);        
        info.setMovementMethod(new ScrollingMovementMethod());        
        
        updateLogView();
        
        IntentFilter logReceiverFilter = new IntentFilter();        
        logReceiverFilter.addAction(App.LOG_INTENT);
        registerReceiver(logReceiver, logReceiverFilter);                        
                 
        if (savedInstanceState == null)
        {        
            app.log(Html.fromHtml(
                app.isEnabled() ? "<b>SMS gateway running.</b>" : "<b>SMS gateway disabled.</b>"));

            app.log("Server URL is: " + app.getDisplayString(app.getServerUrl()));
            app.log("Your phone number is: " + app.getDisplayString(app.getPhoneNumber()) );
            app.log(Html.fromHtml("<b>Press Menu to edit settings.</b>"));
            
            app.setOutgoingMessageAlarm();
        }
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.settings:
            startActivity(new Intent(this, Prefs.class));
            return true;
        case R.id.check_now:
            app.checkOutgoingMessages();
            return true;
        case R.id.retry_now:
            app.retryStuckMessages();
            return true; 
        case R.id.forward_inbox:
            startActivity(new Intent(this, ForwardInbox.class));
            return true;
        case R.id.help:
            startActivity(new Intent(this, Help.class));
            return true;
        case R.id.test: 
            app.log("Testing server connection...");
            new TestTask().execute();            
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }    
    
    // first time the Menu key is pressed
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        
        return(true);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.retry_now);
        int stuckMessages = app.getStuckMessageCount();
        item.setEnabled(stuckMessages > 0);
        item.setTitle("Retry Fwd (" + stuckMessages + ")");
        return true;
    }
    
}