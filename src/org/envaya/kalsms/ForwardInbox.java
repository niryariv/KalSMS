
package org.envaya.kalsms;

import android.app.ListActivity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;


public class ForwardInbox extends ListActivity {

    private App app;    
    
    private Cursor cur;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        app = App.getInstance(getApplicationContext());
                
        setContentView(R.layout.inbox);
                
        // undocumented API; see
        // core/java/android/provider/Telephony.java
        
        Uri inboxUri = Uri.parse("content://sms/inbox");
        
        cur = getContentResolver().query(inboxUri, null, null, null, null);
        
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
            R.layout.inbox_item,
            cur, 
            new String[] {"address","body"}, 
            new int[] {R.id.inbox_address, R.id.inbox_body}); 
        
        setListAdapter(adapter);                                
        
        ListView listView = getListView();
        
        listView.setItemsCanFocus(false);        
    }
           
    public void forwardSelected(View view) {
        
        ListView listView = getListView();        
        
        SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
        
        int checkedItemsCount = checkedItems.size();
        
        int addressIndex = cur.getColumnIndex("address");
        int bodyIndex = cur.getColumnIndex("body");
        int dateIndex = cur.getColumnIndex("date");
        
        for (int i = 0; i < checkedItemsCount; ++i) 
        {
            int position = checkedItems.keyAt(i);
            boolean isChecked = checkedItems.valueAt(i);

            if (isChecked)
            {                
                cur.moveToPosition(position);
                
                String address = cur.getString(addressIndex);
                String body = cur.getString(bodyIndex);
                long date = cur.getLong(dateIndex);
                
                IncomingMessage sms = new IncomingMessage(app, address, body, date);
                
                app.forwardToServer(sms);
            }
        }
        
        this.finish();
     }              
}
