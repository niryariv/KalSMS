package org.envaya.kalsms;

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
import java.text.DateFormat;
import java.util.Date;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;

public class Main extends Activity {   
	
    private App app;
    
    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {  
            showLogMessage(intent.getExtras().getString("message"));
        }
    };
    
    private class TestTask extends HttpTask
    {
        public TestTask() {
            super(app);   
        }
        
        @Override
        protected void handleResponse(HttpResponse response) throws Exception 
        {        
            parseResponseXML(response);
            
            app.log("Server connection OK!");            
        }
    }
        
    private long lastLogTime = 0;

    public void showLogMessage(String message)
    {
        TextView info = (TextView) Main.this.findViewById(R.id.info);        
        if (message != null)
        {                                                
            int length = info.length();
            int maxLength = 20000;
            if (length > maxLength)
            {
                CharSequence text = info.getText();
                
                int startPos = length - maxLength / 2;
                
                for (int cur = startPos; cur < startPos + 100 && cur < length; cur++)
                {
                    if (text.charAt(cur) == '\n')
                    {
                        startPos = cur;
                        break;
                    }
                }
                
                CharSequence endSequence = text.subSequence(startPos, length);
                
                info.setText("[Older log messages not shown]");
                info.append(endSequence);
            }
            
            long logTime = System.currentTimeMillis();
            if (logTime - lastLogTime > 60000)
            {
                Date date = new Date(logTime);                
                info.append("[" + DateFormat.getTimeInstance().format(date) + "]\n");                
                lastLogTime = logTime;
            }            
            
            info.append(message + "\n");
            
            final ScrollView scrollView = (ScrollView) this.findViewById(R.id.info_scroll);
            scrollView.post(new Runnable() { public void run() { 
                scrollView.fullScroll(View.FOCUS_DOWN);
            } });
        }        
    }
            
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        this.app = App.getInstance(getApplicationContext());
                
        setContentView(R.layout.main);
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);               
        
        TextView info = (TextView) this.findViewById(R.id.info);
        
        info.setText(Html.fromHtml("<b>SMS Gateway running.</b><br />"));                        
        info.append(Html.fromHtml("<b>Press Menu to edit settings.</b><br />"));                
        
        showLogMessage("Server URL is: " + app.getDisplayString(app.getServerUrl()));
        showLogMessage("Your phone number is: " + app.getDisplayString(app.getPhoneNumber()));
        
        info.setMovementMethod(new ScrollingMovementMethod());        
        
        IntentFilter logReceiverFilter = new IntentFilter();        
        logReceiverFilter.addAction(App.LOG_INTENT);
        registerReceiver(logReceiver, logReceiverFilter);        
        
        app.setOutgoingMessageAlarm();
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
        case R.id.help:
            startActivity(new Intent(this, Help.class));
            return true;
        case R.id.test: 
            app.log("Testing server connection...");
            new TestTask().execute(
                new BasicNameValuePair("action", App.ACTION_OUTGOING)                    
            );            
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
	    
    @Override
    protected void onStop(){
    	// dont do much with this, atm..
    	super.onStop();
    }
    
}