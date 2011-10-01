package org.envaya.sms.ui;

import org.envaya.sms.task.HttpTask;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.App;
import org.envaya.sms.R;

public class Main extends Activity {   
	
    private App app;
    
    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {  
            updateLogView();
        }
    };
    
    private ScrollView scrollView;
    private TextView info;
    
    private class TestTask extends HttpTask
    {
        public TestTask() {
            super(Main.this.app, new BasicNameValuePair("action", App.ACTION_TEST));   
        }
        
        @Override
        protected void handleResponse(HttpResponse response) throws Exception 
        {        
            parseResponseXML(response);            
            app.log("Server connection OK!");            
        }
    }
    
    private int lastLogEpoch = -1;

    public void updateLogView()
    {                   
        int logEpoch = app.getLogEpoch();
        CharSequence displayedLog = app.getDisplayedLog();        
        
        if (lastLogEpoch == logEpoch)
        {
            int beforeLen = info.getText().length();
            int afterLen = displayedLog.length();
            
            if (beforeLen == afterLen)
            {                
                return;
            }
            
            info.append(displayedLog, beforeLen, afterLen);
        }
        else
        {
            info.setText(displayedLog);
            lastLogEpoch = logEpoch;
        }
                
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
                        
        scrollView = (ScrollView) this.findViewById(R.id.info_scroll);
        info = (TextView) this.findViewById(R.id.info);        
        
        info.setMovementMethod(new ScrollingMovementMethod());        
        
        updateLogView();
        
        IntentFilter logReceiverFilter = new IntentFilter();        
        logReceiverFilter.addAction(App.LOG_CHANGED_INTENT);
        registerReceiver(logReceiver, logReceiverFilter);
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
            startActivity(new Intent(this, MessagingInbox.class));
            return true;
        case R.id.pending:
            startActivity(new Intent(this, PendingMessages.class));
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
        MenuItem retryItem = menu.findItem(R.id.retry_now);
        int pendingMessages = app.getPendingMessageCount();
        retryItem.setEnabled(pendingMessages > 0);
        retryItem.setTitle("Retry All (" + pendingMessages + ")");
        
        return true;
    }
    
}