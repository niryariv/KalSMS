package org.envaya.sms.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;
import org.envaya.sms.IncomingMessage;
import org.envaya.sms.IncomingSms;
import org.envaya.sms.R;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MessagingSmsInbox extends MessagingForwarder {

    private Cursor cur;

    public IncomingMessage getMessageAtPosition(int position) 
    {
        int addressIndex = cur.getColumnIndex("address");
        int bodyIndex = cur.getColumnIndex("body");
        int dateIndex = cur.getColumnIndex("date");

        cur.moveToPosition(position);

        IncomingSms sms = new IncomingSms(app);
        
        sms.setFrom(cur.getString(addressIndex));
        sms.setTimestamp(cur.getLong(dateIndex));
        sms.setMessageBody(cur.getString(bodyIndex));
        
        return sms;        
    }

    public int getMessageCount()
    {
        return cur.getCount();
    }
    
    public void initListAdapter() {
        // undocumented API; see
        // core/java/android/provider/Telephony.java

        Uri inboxUri = Uri.parse("content://sms/inbox");

        cur = getContentResolver().query(inboxUri,
                new String[]{"_id", "address", "body", "date"}, null, null,
                "_id desc limit 50");

        final LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final DateFormat dateFormat = new SimpleDateFormat("dd MMM HH:mm:ss");
        
        CursorAdapter adapter = new CursorAdapter(this, cur) {                    
             public View newView(Context context, Cursor cursor, ViewGroup parent) {
                return inflater.inflate(R.layout.inbox_item, null);
             }
             
             public void bindView(View view, Context context, Cursor cursor)
             {
                TextView addrText = (TextView) view.findViewById(R.id.inbox_address);
                TextView bodyText = (TextView) view.findViewById(R.id.inbox_body);
                    
                String address = cursor.getString(1);
                String body = cursor.getString(2);
                long date = cursor.getLong(3);
                
                addrText.setText(address + " (" + dateFormat.format(new Date(date)) + ")");
                bodyText.setText(body);
             }
        };

        setListAdapter(adapter);
    }
}
