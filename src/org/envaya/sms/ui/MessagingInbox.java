
package org.envaya.sms.ui;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import org.envaya.sms.App;
import org.envaya.sms.IncomingMessage;
import org.envaya.sms.IncomingSms;
import org.envaya.sms.R;


public class MessagingInbox extends ListActivity {

    private App app;    
    
    private Cursor cur;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        app = (App) getApplication();
                
        setContentView(R.layout.inbox);
                
        // undocumented API; see
        // core/java/android/provider/Telephony.java
        
        Uri inboxUri = Uri.parse("content://sms/inbox");
        
        cur = getContentResolver().query(inboxUri, 
            new String[] { "_id", "address", "body", "date" }, null, null, 
            "_id desc limit 50");
        
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
            R.layout.inbox_item,
            cur, 
            new String[] {"address","body"}, 
            new int[] {R.id.inbox_address, R.id.inbox_body}); 
        
        setListAdapter(adapter);
        
        ListView listView = getListView();
        
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                int position, long id) 
            {                
                final IncomingMessage message = getMessageAtPosition(position);
                
                final CharSequence[] options = {"Forward", "Cancel"};
                
                new AlertDialog.Builder(MessagingInbox.this)
                    .setTitle(message.getDescription())
                    .setItems(options, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            if (which == 0)
                            {
                                app.inbox.forwardMessage(message);
                                showToast("Forwarding " + message.getDescription());
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
    
    public IncomingMessage getMessageAtPosition(int position)
    {
        int addressIndex = cur.getColumnIndex("address");
        int bodyIndex = cur.getColumnIndex("body");
        int dateIndex = cur.getColumnIndex("date");

        cur.moveToPosition(position);

        String address = cur.getString(addressIndex);
        String body = cur.getString(bodyIndex);
        long date = cur.getLong(dateIndex);

        return new IncomingSms(app, address, body, date);
    }
    
    public void forwardAllClicked() {            
        final int count = cur.getCount();        
        
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
        
        int numMessages = cur.getCount();
        forwardItem.setEnabled(numMessages > 0);
        forwardItem.setTitle("Forward All (" + numMessages + ")");
        
        return true;
    }
}
