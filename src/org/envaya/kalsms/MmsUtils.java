
package org.envaya.kalsms;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Utilities for parsing IncomingMms from the MMS content provider tables,
 * as defined by android.provider.Telephony
 * 
 * Analogous to com.google.android.mms.pdu.PduPersister from 
 * core/java/com/google/android/mms/pdu in the base Android framework
 * (https://github.com/android/platform_frameworks_base)
 */
public class MmsUtils
{
    // constants from android.provider.Telephony
    public static final Uri OBSERVER_URI = Uri.parse("content://mms-sms/");        
    public static final Uri INBOX_URI = Uri.parse("content://mms/inbox");        
    public static final Uri PART_URI = Uri.parse("content://mms/part");    
    
    // constants from com.google.android.mms.pdu.PduHeaders  
    private static final int PDU_HEADER_FROM = 0x89;    
    private static final int MESSAGE_TYPE_RETRIEVE_CONF = 0x84;
    
    // todo -- prevent unbounded growth?
    private final Set<Long> seenMmsIds = new HashSet<Long>();
    
    private App app;
    private ContentResolver contentResolver;
    
    public MmsUtils(App app)
    {
        this.app = app;
        this.contentResolver = app.getContentResolver();
    }
    
    private List<MmsPart> getMmsParts(long id)
    {        
        Cursor cur = contentResolver.query(PART_URI, new String[] {
            "_id", "ct", "name", "text", "cid", "_data"
        }, "mid = ?", new String[] { "" + id }, null);

        // assume that if there is at least one part saved in database
        // then MMS is fully delivered (this seems to be true in practice)

        List<MmsPart> parts = new ArrayList<MmsPart>();
        
        while (cur.moveToNext())
        {
            long partId = cur.getLong(0);

            MmsPart part = new MmsPart(app, partId);
            part.setContentType(cur.getString(1));
            part.setName(cur.getString(2));

            part.setDataFile(cur.getString(5));
                
            // todo interpret charset like com.google.android.mms.pdu.EncodedStringValue
            part.setText(cur.getString(3));
            
            part.setContentId(cur.getString(4));
            
            parts.add(part);
        }

        cur.close();
               
        return parts;
    }
    
    /*
     * see com.google.android.mms.pdu.PduPersister.loadAddress
     */
    private String getSenderNumber(long mmsId) {
        
        Uri uri = Uri.parse("content://mms/"+mmsId+"/addr");

        Cursor cur = contentResolver.query(uri, 
                new String[] { "address", "charset", "type" }, 
                null, null, null);

        String address = null;
        while (cur.moveToNext())
        {
            int addrType = cur.getInt(2);
            if (addrType == PDU_HEADER_FROM)
            {
                // todo interpret charset like com.google.android.mms.pdu.EncodedStringValue
                address = cur.getString(0);
            }
        }
        cur.close();

        return address;
    }

    public List<IncomingMms> getMessagesInInbox()
    {
        // the M-Retrieve.conf messages are the 'actual' MMS messages        
        String m_type = "" + MESSAGE_TYPE_RETRIEVE_CONF;

        Cursor c = contentResolver.query(INBOX_URI, 
                new String[] {"_id", "ct_l"}, 
                "m_type = ? ", new String[] { m_type }, null);
        
        List<IncomingMms> messages = new ArrayList<IncomingMms>();        
        
        while (c.moveToNext())
        {         
            long id = c.getLong(0);                               
                        
            IncomingMms mms = new IncomingMms(app, getSenderNumber(id), id);
            
            mms.setContentLocation(c.getString(1));
                        
            for (MmsPart part : getMmsParts(id))
            {
                mms.addPart(part);
            }

            messages.add(mms);
        }
        c.close();
        
        return messages;
    }
    
    public synchronized boolean deleteFromInbox(IncomingMms mms)
    {        
        long id = mms.getId();
                
        Uri uri = Uri.parse("content://mms/inbox/" + id);            
        int res = contentResolver.delete(uri, null, null);
                
        if (res > 0)
        {
            app.log("MMS id="+id+" deleted from inbox");
            
            // remove id from set because Messaging app reuses ids 
            // of deleted messages.             
            // TODO: handle reuse of IDs deleted directly through Messaging
            // app while KalSMS is running
            seenMmsIds.remove(id);
        }
        else
        {
            app.log("MMS id="+id+" could not be deleted from inbox");
        }
        return res  > 0;
    }
    
    public synchronized void markOldMms(IncomingMms mms)
    {
        long id = mms.getId();        
        seenMmsIds.add(id);
    }
    
    public synchronized boolean isNewMms(IncomingMms mms)
    {
        long id = mms.getId();        
        return !seenMmsIds.contains(id);
    }
}        
