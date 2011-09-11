/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

/**
 *
 * @author Jesse
 */
public class App {
    
    public static final int OUTGOING_POLL_SECONDS = 30;
       
    public static final String LOG_NAME = "KALSMS";    
    public static final String LOG_INTENT = "org.envaya.kalsms.LOG";
    public static final String SEND_STATUS_INTENT = "org.envaya.kalsms.SEND_STATUS";
    
    public Context context;
    public SharedPreferences settings;
    
    public App(Context context)
    {
        this.context = context;
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    static void debug(String msg)
    {
        Log.d(LOG_NAME, msg);          
    }
    
    public void log(String msg) 
    {
        Log.d(LOG_NAME, msg);
        
        Intent broadcast = new Intent(App.LOG_INTENT);
        broadcast.putExtra("message", msg);
        context.sendBroadcast(broadcast);        
    }

    public void logError(Throwable ex)
    {
        logError("ERROR", ex);
    }    
    
    public void logError(String msg, Throwable ex)
    {
        logError(msg, ex, false);
    }
    
    public void logError(String msg, Throwable ex, boolean detail)
    {
        log(msg + ": " + ex.getClass().getName() + ": " + ex.getMessage());
        
        if (detail)
        {
            for (StackTraceElement elem : ex.getStackTrace())
            {
                log(elem.getClassName() + ":" + elem.getMethodName() + ":" + elem.getLineNumber());
            }
            Throwable innerEx = ex.getCause();
            if (innerEx != null)
            {
                logError("Inner exception:", innerEx, true);
            }
        }
    }        
    
    public String getIncomingUrl()
    {
        return getServerUrl() + "/pg/receive_sms";
    }
    
    public String getOutgoingUrl()
    {
        return getServerUrl() + "/pg/dequeue_sms";
    }    
    
    public String getSendStatusUrl()
    {
        return getServerUrl() + "/pg/sms_sent";
    }
    
    public String getServerUrl()
    {
        return settings.getString("server_url", "");
    }

    public String getPhoneNumber()
    {
        return settings.getString("phone_number", "");
    }
    
    public String getPassword()
    {
        return settings.getString("password", "");
    }       
    
    private SQLiteDatabase db;
    public SQLiteDatabase getWritableDatabase()
    {
        if (db == null)
        {
            db = new DBHelper(context).getWritableDatabase();
        }
        return db;
    }
    
    public void sendSMS(OutgoingSmsMessage sms)
    {       
        String serverId = sms.getServerId();
        
        if (serverId != null)
        {
            SQLiteDatabase db = this.getWritableDatabase();
            Cursor cursor = 
                db.rawQuery("select 1 from sent_sms where server_id=?", new String[] { serverId });
        
            boolean exists = (cursor.getCount() > 0);
            cursor.close();
            if (exists)
            {
                log(sms.getLogName() + " already sent, skipping");
                return;
            }
            
            ContentValues values = new ContentValues();
            values.put("server_id", serverId);
            db.insert("sent_sms", null, values);
        }         

        SmsManager smgr = SmsManager.getDefault();
                
        Intent intent = new Intent(App.SEND_STATUS_INTENT);
        intent.putExtra("serverId", serverId);
        
        PendingIntent sentIntent = PendingIntent.getBroadcast(
                this.context,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT);
        
        log("Sending " +sms.getLogName() + " to " + sms.getTo());
        smgr.sendTextMessage(sms.getTo(), null, sms.getMessage(), sentIntent, null);
    }
}
