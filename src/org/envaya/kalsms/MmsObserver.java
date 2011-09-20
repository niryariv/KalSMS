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
        /*
         * Content observers can watch the MMS inbox URI for changes; 
         * This is the URL passed to PduPersister.persist by
         * com.android.mms.transaction.RetrieveTransaction.run
         */                
        app.getContentResolver().registerContentObserver(
            MmsUtils.OBSERVER_URI, true, this);
        
        MmsUtils mmsUtils = app.getMmsUtils();
        
        List<IncomingMms> messages = mmsUtils.getMessagesInInbox();
        for (IncomingMms mms : messages)
        {
            mmsUtils.markOldMms(mms);
        }
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
