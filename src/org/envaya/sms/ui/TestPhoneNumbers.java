package org.envaya.sms.ui;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import org.envaya.sms.App;
import org.envaya.sms.R;

public class TestPhoneNumbers extends ListActivity {
    
    private App app;
    
    private CheckBox autoAddOutgoing;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);        
        setContentView(R.layout.test_phone_numbers);           
        
        app = (App)getApplication();        
     
        autoAddOutgoing = (CheckBox)findViewById(R.id.auto_add_outgoing);
        autoAddOutgoing.setChecked(app.autoAddTestNumber());
        
        ListView lv = getListView();
         lv.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                int position, long id) 
            {                
                final String phoneNumber = ((TextView) view).getText().toString();
                
                new AlertDialog.Builder(TestPhoneNumbers.this)
                    .setTitle("Remove Test Phone")
                    .setMessage("Do you want to remove "+phoneNumber
                        +" from the list of test phone numbers?")
                    .setPositiveButton("OK", 
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                app.removeTestPhoneNumber(phoneNumber);
                                updateTestPhoneNumbers();
                                dialog.dismiss();
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
          });        
                
        updateTestPhoneNumbers();
    }
    
    public void autoAddOutgoingClicked(View v)
    {   
        boolean checked = autoAddOutgoing.isChecked();
        app.log("Test Mode: automatically add outgoing message recipients set to " + (checked ? "YES" : "NO"));
        app.saveBooleanSetting("auto_add_test_number", checked);
    }
    
    public void updateTestPhoneNumbers()
    {                
        String[] senders = app.getTestPhoneNumbers().toArray(new String[]{});
        
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, 
                R.layout.phone_number, 
                senders);
        
        setListAdapter(arrayAdapter);                
    }
    
    public void addTestPhoneNumber(View v)
    {   
        LayoutInflater factory = LayoutInflater.from(this);
        final EditText textEntryView = 
                (EditText)factory.inflate(R.layout.add_phone_number, null);
        
        new AlertDialog.Builder(this)
            .setTitle("Add Test Phone")
            .setMessage("Enter the phone number that you will be testing with:")
            .setView(textEntryView)
            .setPositiveButton("OK", 
                new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        app.addTestPhoneNumber(textEntryView.getText().toString());
                        updateTestPhoneNumbers();
                        dialog.dismiss();
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
}
