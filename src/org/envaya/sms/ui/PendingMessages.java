
package org.envaya.sms.ui;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.envaya.sms.App;
import org.envaya.sms.IncomingMessage;
import org.envaya.sms.OutgoingMessage;
import org.envaya.sms.QueuedMessage;
import org.envaya.sms.R;


public class PendingMessages extends ListActivity {

    private App app;    
    
    private List<QueuedMessage> displayedMessages;
    
    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {  
            refreshMessages();
        }
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        app = (App) getApplication();
                
        setContentView(R.layout.pending_messages);
        
        IntentFilter refreshReceiverFilter = new IntentFilter();        
        refreshReceiverFilter.addAction(App.INBOX_CHANGED_INTENT);
        refreshReceiverFilter.addAction(App.OUTBOX_CHANGED_INTENT);
        registerReceiver(refreshReceiver, refreshReceiverFilter);        
        
        ListView listView = getListView();        
        
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                int position, long id) 
            {                
                final QueuedMessage message = displayedMessages.get(position);
                final CharSequence[] options = {"Retry", "Delete", "Cancel"};
                
                new AlertDialog.Builder(PendingMessages.this)
                    .setTitle(message.getDescription())
                    .setItems(options, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            if (which == 0)
                            {
                                retryMessage(message);
                            }
                            else if (which == 1)
                            {
                                deleteMessage(message);                                
                            }
                            dialog.dismiss();
                        }
                    })
                    .show();
            }
        });                                        
        
        refreshMessages();
    }
    
    @Override
    public void onDestroy()
    {
        this.unregisterReceiver(refreshReceiver);        
        super.onDestroy();
    }    

    public void refreshMessages()
    {
        final ArrayList<QueuedMessage> messages = new ArrayList<QueuedMessage>();
        
        synchronized(app.outbox)
        {
            for (OutgoingMessage message : app.outbox.getMessages())
            {
                messages.add(message);
            }
        }
        
        synchronized(app.inbox)
        {
            for (IncomingMessage message : app.inbox.getMessages())
            {
                messages.add(message);
            }
        }
        
        Collections.sort(messages, new Comparator<QueuedMessage>(){
            public int compare(QueuedMessage t1, QueuedMessage t2)
            {
                return t1.getDateCreated().compareTo(t2.getDateCreated());
            }        
        });
        
        displayedMessages = messages;
        
        this.setTitle(getText(R.string.pending_messages_title) + " ("+messages.size()+")");
        
        final LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final DateFormat longFormat = new SimpleDateFormat("dd MMM hh:mm:ss");
        final DateFormat shortFormat = new SimpleDateFormat("hh:mm:ss");        
        final Date now = new Date();        
        
        ArrayAdapter<QueuedMessage> arrayAdapter = new ArrayAdapter<QueuedMessage>(this, 
                R.layout.pending_message, 
                messages.toArray(new QueuedMessage[]{})) {
            @Override
             public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = inflater.inflate(R.layout.pending_message, null);
                }
                QueuedMessage message = messages.get(position);
                if (message == null) 
                {
                    return null;
                }
                    
                TextView addr = (TextView) v.findViewById(R.id.pending_address);
                TextView time = (TextView) v.findViewById(R.id.pending_time);
                TextView status = (TextView) v.findViewById(R.id.pending_status);
                    
                addr.setText(message.getDescription());
                
                String statusText = message.getStatusText();
                int numRetries = message.getNumRetries();
                if (numRetries > 0)
                {
                    statusText = statusText + " (tries=" + numRetries + ")";
                }
                
                status.setText(statusText);
                
                Date date = message.getDateCreated();
                DateFormat format = 
                    (date.getDate() == now.getDate() && date.getMonth() == now.getMonth())
                        ? shortFormat : longFormat;
                
                time.setText(format.format(date));
                return v;
             }                    
        };
        
        setListAdapter(arrayAdapter);                  
        
        
    }
    
    public void deleteMessage(QueuedMessage message)
    {
        if (message instanceof IncomingMessage)
        {
            app.inbox.deleteMessage((IncomingMessage)message);
        }
        else
        {
            app.outbox.deleteMessage((OutgoingMessage)message);
        }        
    }
    
    public void deleteAll()
    {
        for (QueuedMessage message : displayedMessages)
        {
            deleteMessage(message);
        }
    }
    
    public void deleteAllClicked() {
        
        new AlertDialog.Builder(this)
            .setTitle("Confirm Action")
            .setMessage("Do you want to delete all "+displayedMessages.size()+" pending messages?")
            .setPositiveButton("OK", 
                new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {       
                        dialog.dismiss();
                        deleteAll();
                    }
                }
            )
            .setNegativeButton("Cancel", 
                new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }
                }
            )
            .show();                                
     }
    
    public void retryMessage(QueuedMessage message)
    {
        if (message instanceof IncomingMessage)
        {
            app.inbox.enqueueMessage((IncomingMessage)message);
        }
        else
        {
            app.outbox.enqueueMessage((OutgoingMessage)message);
        }        
    }
    
    public void retryAllClicked()
    {
        for (QueuedMessage message : displayedMessages)
        {
            retryMessage(message);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.retry_all:
            retryAllClicked();
            return true;
        case R.id.delete_all:
            deleteAllClicked();
            return true;            
        default:
            return super.onOptionsItemSelected(item);
        }
    }    
    
    // first time the Menu key is pressed
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pending_messages, menu);        
        return(true);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem retryItem = menu.findItem(R.id.retry_all);
        MenuItem deleteItem = menu.findItem(R.id.delete_all);
        
        int numMessages = displayedMessages.size();
        retryItem.setEnabled(numMessages > 0);        
        deleteItem.setEnabled(numMessages > 0);        
        
        return true;
    }    
}
