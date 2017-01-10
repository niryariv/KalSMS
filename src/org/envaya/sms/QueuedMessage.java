package org.envaya.sms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import java.util.Date;

public abstract class QueuedMessage 
{
    protected long nextRetryTime = 0;
    protected int numRetries = 0;
    protected Date dateCreated = new Date();
    public App app;   
    
    protected long persistedId = 0; // _id of row in pending_incoming_messages or pending_outgoing_messages table (0 if not stored)

    public QueuedMessage(App app)
    {
        this.app = app;
    }
    
    public boolean isPersisted()
    {
        return persistedId != 0;
    }
    
    public long getPersistedId()
    {
        return persistedId;
    }
    
    public void setPersistedId(long id)
    {
        this.persistedId = id;
    }        
    
    public Date getDateCreated()
    {
        return dateCreated;
    }
    
    public int getNumRetries()
    {
        return numRetries;
    }
    
    public boolean canRetryNow() {
        return (nextRetryTime > 0 && nextRetryTime < SystemClock.elapsedRealtime());
    }

    public boolean scheduleRetry() {
        long now = SystemClock.elapsedRealtime();
        numRetries++;

        if (numRetries > 4) {
            app.log("5th failure: giving up");
            return false;
        }

        int second = 1000;
        int minute = second * 60;

        if (numRetries == 1) {
            app.log("1st failure; retry in 20 seconds");
            nextRetryTime = now + 20 * second;
        } else if (numRetries == 2) {
            app.log("2nd failure; retry in 5 minutes");
            nextRetryTime = now + 5 * minute;
        } else if (numRetries == 3) {
            app.log("3rd failure; retry in 1 hour");
            nextRetryTime = now + 60 * minute;
        } else {
            app.log("4th failure: retry in 1 day");
            nextRetryTime = now + 24 * 60 * minute;
        }

        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(app,
                0,
                getRetryIntent(),
                0);

        alarm.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                nextRetryTime,
                pendingIntent);

        return true;
    }
        
    public abstract String getDisplayType();    
    public abstract String getDescription();
    public abstract String getStatusText();
    
    public abstract Uri getUri();

    protected abstract Intent getRetryIntent();
}
