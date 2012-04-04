package org.envaya.sms.ui;

import org.envaya.sms.IncomingMms;
import org.envaya.sms.IncomingMessage;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import org.envaya.sms.R;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MessagingMmsInbox extends MessagingForwarder 
{
    private List<IncomingMms> messages;
        
    public IncomingMessage getMessageAtPosition(int position) 
    {
        return messages.get(position);
    }

    public int getMessageCount()
    {
        return messages.size();
    }    
    
    public void initListAdapter() {
        // undocumented API; see
        // core/java/android/provider/Telephony.java

        messages = app.getMessagingUtils().getMessagesInMmsInbox();
        
        final LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final DateFormat dateFormat = new SimpleDateFormat("dd MMM HH:mm:ss");

        ArrayAdapter<IncomingMms> arrayAdapter = new ArrayAdapter<IncomingMms>(this, 
                R.layout.inbox_item, 
                messages.toArray(new IncomingMms[]{})) {
            @Override
             public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = inflater.inflate(R.layout.inbox_item, null);
                }
                IncomingMms mms = messages.get(position);
                if (mms == null) 
                {
                    return null;
                }
                    
                TextView addrText = (TextView) v.findViewById(R.id.inbox_address);
                TextView bodyText = (TextView) v.findViewById(R.id.inbox_body);
                    
                addrText.setText(mms.getFrom() + " (" + dateFormat.format(new Date(mms.getTimestamp())) + ")");
                bodyText.setText(mms.getMessageBody());

                return v;
             }
        };
        
        setListAdapter(arrayAdapter);
    }
}
