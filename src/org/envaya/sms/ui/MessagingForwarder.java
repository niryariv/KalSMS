
package org.envaya.sms.ui;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import org.envaya.sms.App;
import org.envaya.sms.IncomingMessage;
import org.envaya.sms.R;
import java.util.Arrays;


public abstract class MessagingForwarder extends ListActivity {

    protected App app;    
    
    abstract int getMessageCount();
    abstract IncomingMessage getMessageAtPosition(int position);
    abstract void initListAdapter();
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        app = (App) getApplication();
                
        setContentView(R.layout.inbox);
        
        final String[] inboxTypeClasses = new String[] {
                "org.envaya.sms.ui.MessagingSmsInbox", 
                "org.envaya.sms.ui.MessagingMmsInbox",
                "org.envaya.sms.ui.MessagingSentSms",
        };
        
        final String[] inboxTypeNames = new String[] {
                "SMS Inbox", 
                "MMS Inbox",
                "Sent SMS"
        };
                
        Spinner spinner = (Spinner) findViewById(R.id.inbox_selector);        
        
        ArrayAdapter<String> inboxTypeAdapter = new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_item, inboxTypeNames);
        inboxTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(inboxTypeAdapter);    
        
        final String className = this.getClass().getCanonicalName();
        int classIndex = Arrays.asList(inboxTypeClasses).indexOf(className);
        if (classIndex != -1)
        {
            spinner.setSelection(classIndex);
        }
        
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                View view, int pos, long id) {                
                String cls = inboxTypeClasses[pos];
                
                if (!className.equals(cls))
                {             
                    try
                    {
                        finish();
                        startActivity(new Intent(app, Class.forName(cls)));                        
                    }
                    catch (ClassNotFoundException ex)
                    {
                        app.logError(ex);
                    }
                }                
            }

            public void onNothingSelected(AdapterView parent) {            
            }
        });
        
        
        initListAdapter();
                        
        ListView listView = getListView();
        
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                int position, long id) 
            {                
                final IncomingMessage message = getMessageAtPosition(position);
                
                final CharSequence[] options = {"Forward to server", "Cancel"};
                
                new AlertDialog.Builder(MessagingForwarder.this)
                    .setTitle(message.getDescription())
                    .setItems(options, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            if (which == 0)
                            {
                                app.inbox.forwardMessage(message);
                                showToast("Forwarding " + message.getDescription() + " to server");
                            }
                            dialog.dismiss();
                        }
                    })
                    .show();
            }
        });
    }
               
    public void showToast(String text)
    {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }        
    
    public void forwardAllClicked() {            
        final int count = getMessageCount();        
        
        for (int i = 0; i < count; ++i) 
        {
            app.inbox.forwardMessage(getMessageAtPosition(i));        
        }
        finish();
     }              
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.forward_all:
            forwardAllClicked();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }    
    
    // first time the Menu key is pressed
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inbox, menu);        
        return(true);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem forwardItem = menu.findItem(R.id.forward_all);
        
        int numMessages = getMessageCount();
        forwardItem.setEnabled(numMessages > 0);
        forwardItem.setTitle("Forward all to server (" + numMessages + ")");
        
        return true;
    }
}
