/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class App {
    
    public static final String ACTION_OUTGOING = "outgoing";
    public static final String ACTION_INCOMING = "incoming";
    public static final String ACTION_SEND_STATUS = "send_status";
    
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SENT = "sent";
    
    public static final String LOG_NAME = "KALSMS";    
    public static final String LOG_INTENT = "org.envaya.kalsms.LOG";
    public static final String SEND_STATUS_INTENT = "org.envaya.kalsms.SEND_STATUS";
    
    public Context context;
    public SharedPreferences settings;
    
    protected App(Context context)
    {
        this.context = context;
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    private static App app;
    
    public static App getInstance(Context context)
    {
        if (app == null)
        {
            app = new App(context);
        }
        return app;
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

    public void checkOutgoingMessages()
    {
        String serverUrl = getServerUrl();
        if (serverUrl.length() > 0) 
        {
            log("Checking for outgoing messages");
            new PollerTask().execute(                
                new BasicNameValuePair("action", App.ACTION_OUTGOING)
            );
        }
        else
        {
            log("Can't check outgoing messages; server URL not set");
        }
    }
    
    public void setOutgoingMessageAlarm()
    {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);        
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                0,
                new Intent(context, OutgoingMessagePoller.class), 
                0);     
        
        alarm.cancel(pendingIntent);        
        
        int pollSeconds = getOutgoingPollSeconds();
        
        if (pollSeconds > 0)
        {
            alarm.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime(),                                
                pollSeconds * 1000, 
                pendingIntent);        
            log("Checking for outgoing messages every " + pollSeconds + " sec");            
        }
        else
        {
            log("Not checking for outgoing messages.");
        }
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
        
    public String getDisplayString(String str)
    {
        if (str.length() == 0)
        {
            return "(not set)";
        }
        else
        {
            return str;
        }   
    }
    
    public String getServerUrl()
    {
        return settings.getString("server_url", "");
    }

    public String getPhoneNumber()
    {
        return settings.getString("phone_number", "");
    }
    
    public int getOutgoingPollSeconds()
    {                
        return Integer.parseInt(settings.getString("outgoing_interval", "0"));        
    }    
    
    public String getPassword()
    {
        return settings.getString("password", "");
    }           
    
    public SQLiteDatabase getWritableDatabase()
    {
        return new DBHelper(context).getWritableDatabase();        
    }
    
    public HttpClient getHttpClient()
    {
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, 8000);
        HttpConnectionParams.setSoTimeout(httpParameters, 8000);                    
        return new DefaultHttpClient(httpParameters);        
    }        
    
    public void sendSMS(OutgoingSmsMessage sms)
    {       
        String serverId = sms.getServerId();
        
        if (serverId != null)
        {
            SQLiteDatabase db = this.getWritableDatabase();
            Cursor cursor = 
                db.rawQuery("select 1 from sms_status where server_id=?", new String[] { serverId });
        
            boolean exists = (cursor.getCount() > 0);
            cursor.close();
            if (exists)
            {
                log(sms.getLogName() + " already sent, skipping");
                return;
            }
            
            ContentValues values = new ContentValues();
            values.put("server_id", serverId);
            values.put("status", App.STATUS_QUEUED);
            db.insert("sms_status", null, values);
            
            db.close();
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
    
    private class PollerTask extends HttpTask {

        public PollerTask()
        {
            super(app);
        }
        
        @Override
        protected void handleResponse(HttpResponse response) throws Exception {
            for (OutgoingSmsMessage reply : parseResponseXML(response)) {
                app.sendSMS(reply);
            }                                        
        }                
    }
    
}
