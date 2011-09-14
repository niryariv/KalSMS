
package org.envaya.kalsms;

import android.app.ListActivity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;


public class ForwardInbox extends ListActivity {

    private App app;    
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        app = App.getInstance(getApplicationContext());
                
        setContentView(R.layout.inbox);
                
        // undocumented API; see
        // core/java/android/provider/Telephony.java
        
        Uri inboxUri = Uri.parse("content://sms/inbox");
        
        Cursor cur = getContentResolver().query(inboxUri, null, null, null, null);
        
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
            R.layout.inbox_item,
            cur, 
            new String[] {"address","body"}, 
            new int[] {R.id.inbox_address, R.id.inbox_body}); 
        
        setListAdapter(adapter);                                
    }
           
    public void forwardSelected(View view) {
        
        ListView listView = getListView();        
        
        // there is probably a less hacky way to do this...
        int childCount = listView.getChildCount();                
        for (int i = 0; i < childCount; i++)
        {
            View entry = listView.getChildAt(i);            
            CheckBox checkbox = (CheckBox) entry.findViewById(R.id.inbox_checkbox);

            if (checkbox.isChecked())
            {
                TextView addressView = (TextView) entry.findViewById(R.id.inbox_address);
                TextView bodyView = (TextView) entry.findViewById(R.id.inbox_body);
                IncomingMessage sms = new IncomingMessage(app, 
                        addressView.getText().toString(), 
                        bodyView.getText().toString());                
                
                app.forwardToServer(sms);
            }
        }
        
        this.finish();
     }              
}
