package org.envaya.kalsms;

import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import java.util.List;

final class MmsObserver extends ContentObserver {

    private App app;
        
    public MmsObserver(App app) {
        super(new Handler()); 
        this.app = app;        
    }
    
    public void register()
    {
        app.getContentResolver().registerContentObserver(
            MmsUtils.OBSERVER_URI, true, this);
        
        MmsUtils mmsUtils = app.getMmsUtils();
        
        List<IncomingMms> messages = mmsUtils.getMessagesInInbox();
        for (IncomingMms mms : messages)
        {
            mmsUtils.markOldMms(mms);
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
            app.startService(new Intent(app, CheckMmsInboxService.class));
        }
    }
}
