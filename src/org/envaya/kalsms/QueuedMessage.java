package org.envaya.kalsms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public abstract class QueuedMessage 
{
    protected long nextRetryTime = 0;
    protected int numRetries = 0;
    
    public App app;   

    public QueuedMessage(App app)
    {
        this.app = app;
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
            app.log("1st failure; retry in 1 minute");
            nextRetryTime = now + 1 * minute;
        } else if (numRetries == 2) {
            app.log("2nd failure; retry in 10 minutes");
            nextRetryTime = now + 10 * minute;
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

    public abstract void retryNow();

    protected abstract Intent getRetryIntent();
}
