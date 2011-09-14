/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.widget.TextView;

/**
 *
 * @author Jesse
 */
public class Help extends Activity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        setContentView(R.layout.help);
        
        TextView help = (TextView) this.findViewById(R.id.help);
        
        String html = "<b>KalSMS</b> is a SMS gateway.<br /><br /> "
            + "It forwards all incoming SMS messages received by this phone to a server on the internet, "
            + "and also sends outgoing SMS messages from that server to other phones.<br /><br />"
            + "(See https://github.com/youngj/KalSMS/wiki "
            + "for information about setting up a server.)<br /><br />"
            + "The Settings screen allows you configure KalSMS to work with a particular server, "
            + "by entering the server URL, your phone number, "
            + "and the password assigned to your phone on the server.<br /><br />"
            + "Menu icons cc/by www.androidicons.com<br /><br />";
        
        help.setText(Html.fromHtml(html));                        
        
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.finish();
        
        return(true);
    }    
}
