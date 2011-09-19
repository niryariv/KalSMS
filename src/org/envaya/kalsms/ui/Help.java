package org.envaya.kalsms.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.widget.TextView;
import org.envaya.kalsms.R;

public class Help extends Activity {

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        setContentView(R.layout.help);
        
        TextView help = (TextView) this.findViewById(R.id.help);
        
        String html = "<b>KalSMS</b> is a SMS gateway.<br /><br /> "
            + "It forwards all incoming SMS messages received by this phone to a server on the internet, "
            + "and also sends outgoing SMS messages from that server to other phones.<br /><br />"
            + "(See https://kalsms.net for more information.)<br /><br />"
            + "The Settings screen allows you configure KalSMS to work with a particular server, "
            + "by entering the server URL, your phone number, "
            + "and the password assigned to your phone on the server.<br /><br />"
            + "Menu icons cc/by www.androidicons.com<br /><br />";
        
        help.setText(Html.fromHtml(html));                        
        
    }
}
