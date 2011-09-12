/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 *
 * @author Jesse
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "org.envaya.kalsms.db";
    
    private static final String SMS_STATUS_TABLE_DROP = 
       " DROP TABLE sms_status;";
    
    private static final String SMS_STATUS_TABLE_CREATE = 
        "CREATE TABLE sms_status (server_id text, status int);"
        + "CREATE INDEX server_id_index ON sent_sms (server_id);";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SMS_STATUS_TABLE_CREATE);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2)
        {
            db.execSQL(SMS_STATUS_TABLE_CREATE);
        }        
    }    
}    
