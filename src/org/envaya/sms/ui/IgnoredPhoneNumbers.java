package org.envaya.sms.ui;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import org.envaya.sms.App;
import org.envaya.sms.R;

public class IgnoredPhoneNumbers extends ListActivity {
    
    private App app;
    
    private CheckBox ignoreNonNumeric;
    private CheckBox ignoreShortcodes;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);        
        setContentView(R.layout.ignored_phone_numbers);           
                
        app = (App)getApplication();        
     
        ignoreNonNumeric = (CheckBox)findViewById(R.id.ignore_non_numeric);
        ignoreNonNumeric.setChecked(app.ignoreNonNumeric());
        
        ignoreShortcodes = (CheckBox)findViewById(R.id.ignore_shortcodes);
        ignoreShortcodes.setChecked(app.ignoreShortcodes());        
        
        ListView lv = getListView();
         lv.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                int position, long id) 
            {                
                final String phoneNumber = ((TextView) view).getText().toString();
                
                new AlertDialog.Builder(IgnoredPhoneNumbers.this)
                    .setTitle("Remove Ignored Phone")
                    .setMessage("Do you want to remove "+phoneNumber
                        +" from the list of ignored phone numbers?")
                    .setPositiveButton("OK", 
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                app.removeIgnoredPhoneNumber(phoneNumber);
                                updateIgnoredPhoneNumbers();
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
                
        updateIgnoredPhoneNumbers();
    }
    
    public void updateIgnoredPhoneNumbers()
    {                
        String[] ignoredNumbers = app.getIgnoredPhoneNumbers().toArray(new String[]{});
        
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, 
                R.layout.phone_number, 
                ignoredNumbers);
        
        setListAdapter(arrayAdapter);                
    }
    
    public void ignoreShortcodesClicked(View v)
    {   
        boolean checked = ignoreShortcodes.isChecked();
        app.log("Ignore all shortcodes set to " + (checked ? "ON" : "OFF"));
        app.saveBooleanSetting("ignore_shortcodes", checked);
    }

    public void ignoreNonNumericClicked(View v)
    {   
        boolean checked = ignoreNonNumeric.isChecked();
        app.log("Ignore all non-numeric senders set to " + (checked ? "ON" : "OFF"));
        app.saveBooleanSetting("ignore_non_numeric", checked);
    }
        
    public void addIgnoredPhoneNumber(View v)
    {   
        LayoutInflater factory = LayoutInflater.from(this);
        final EditText textEntryView = 
                (EditText)factory.inflate(R.layout.add_phone_number, null);
        
        new AlertDialog.Builder(this)
            .setTitle("Add Ignored Phone")
            .setMessage("Enter the phone number that you want to ignore:")
            .setView(textEntryView)
            .setPositiveButton("OK", 
                new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        app.addIgnoredPhoneNumber(textEntryView.getText().toString());
                        updateIgnoredPhoneNumbers();
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
