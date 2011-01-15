package kalsms.niryariv.itp;

import kalsms.niryariv.itp.R;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class Main extends Activity {
	
//	public static final String PREFS_NAME = "KalPrefsFile";
	
	public String identifier = "";
	public String targetUrl = "";
	
    
	public void onResume() {
		Log.d("KALSMS", "RESUME");
		super.onResume();
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		
		this.identifier = settings.getString("pref_identifier", "");
		this.targetUrl =  settings.getString("pref_target_url", "");

		Log.d("KALSMS", "onResume ident:" + this.identifier +"\ntarget:" + this.targetUrl);
		
		String infoText = new String();
		
		infoText = "All SMS messages";
		
		if (this.identifier.trim() != "") {
			infoText += " starting with <b>" + this.identifier + "</b>";
		}
		
		infoText += " are now sent to <b>" + this.targetUrl +"</b> in the following format:";
		infoText += "<p><tt>GET " + this.targetUrl + "?sender=&lt;phone#&gt;&msg=&lt;message&gt;</tt></p>";
		infoText += "If the response body contains text, it will SMSed back to the sender.";

		infoText += "<br /><br /><b>Press Menu to set SMS identifier or target URL.</b>";
		
		infoText += "<br /><br /><br />Questions/feedback: niryariv@gmail.com";
		
		TextView info = (TextView) this.findViewById(R.id.info);
        info.setText(Html.fromHtml(infoText));

	}	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
        
        Log.d("KALSMS", "STARTED");
    }
    

    // first time the Menu key is pressed
	public boolean onCreateOptionsMenu(Menu menu) {
		startActivity(new Intent(this, Prefs.class));
		return(true);
	}

	// any other time the Menu key is pressed
	public boolean onPrepareOptionsMenu(Menu menu) {
		startActivity(new Intent(this, Prefs.class));
		return(true);
	}
	
    
    @Override
    protected void onStop(){
    	// dont do much with this, atm..
    	super.onStop();
    }
    
}
