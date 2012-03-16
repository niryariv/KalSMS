package org.envaya.sms;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class CallListener extends PhoneStateListener {
    
    private App app;
    
    public CallListener(App app)
    {
        this.app = app;
    }
    
    @Override
    public void onCallStateChanged(int state,String incomingNumber) {  
        if (state == TelephonyManager.CALL_STATE_RINGING)
        {
            IncomingCall call = new IncomingCall(app, incomingNumber, System.currentTimeMillis());

            if (call.isForwardable())
            {
                app.inbox.forwardMessage(call);                                    
            }
            else
            {
                app.log("Ignoring incoming call from " + call.getFrom());
            }
        }
    }
}
