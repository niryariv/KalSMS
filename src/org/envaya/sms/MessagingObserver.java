package org.envaya.sms;

import org.envaya.sms.service.CheckMessagingService;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import java.util.List;

public final class MessagingObserver extends ContentObserver {

    // constants from android.provider.Telephony
    public static final Uri OBSERVER_URI = Uri.parse("content://mms-sms/");        
    
    private App app;
        
    public MessagingObserver(App app) {
        super(new Handler()); 
        this.app = app;        
    }
    
    public void register()
    {
        app.getContentResolver().registerContentObserver(OBSERVER_URI, true, this);
        
        MessagingUtils messagingUtils = app.getMessagingUtils();
        
        for (IncomingMms mms : messagingUtils.getMessagesInMmsInbox())
        {
            messagingUtils.markSeenMms(mms);
        }
        
        for (IncomingSms sms : messagingUtils.getSentSmsMessages())
        {
            messagingUtils.markSeenSentSms(sms);
        }
    }
    
    public void unregister()
    {
        app.getContentResolver().unregisterContentObserver(this);
    }
             
    @Override
    public void onChange(final boolean selfChange) {
        super.onChange(selfChange);
        if (!selfChange)
        {
            // check MMS inbox in an IntentService since it may be slow
            // and we only want to do one check at a time
            app.startService(new Intent(app, CheckMessagingService.class));
        }
    }
}
