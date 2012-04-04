package org.envaya.sms.ui;

import org.envaya.sms.ui.Prefs;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import org.envaya.sms.App;
import org.envaya.sms.ui.LogView;

public class Main extends Activity {   
	
    private App app;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {   
        super.onCreate(savedInstanceState);
        
        app = (App)getApplication();
                
        startActivity(new Intent(this, LogView.class));       
    }    
}