package org.envaya.sms;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class DatabaseHelper extends SQLiteOpenHelper {
    
    public static final String DATABASE_NAME = "envayasms.db";
    public static final int DATABASE_VERSION = 4;        
    
    private App app;
    
    public DatabaseHelper(App app)
    {
        super(app, DATABASE_NAME, null, DATABASE_VERSION);
        
        this.app = app;
    }
    
    public void onCreate(SQLiteDatabase db)
    {        
        // Persisted backup of incoming messages have not been forwarded to server.
        // allows us to restore pending messages after restart if phone runs out of batteries
        // or app crashes.
                        
        db.execSQL("CREATE TABLE pending_incoming_messages ("
                + "`_id` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`message_type` VARCHAR,"
                + "`messaging_id` INTEGER," // id in Messaging app database (if applicable)
                + "`from_number` VARCHAR,"
                + "`to_number` VARCHAR,"
                + "`message` TEXT,"
                + "`direction` INTEGER,"
                + "`timestamp` INTEGER"
                + ")");
        
        db.execSQL("CREATE TABLE pending_outgoing_messages ("
                + "`_id` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`message_type` VARCHAR,"
                + "`from_number` VARCHAR,"
                + "`to_number` VARCHAR,"
                + "`message` TEXT,"
                + "`priority` INTEGER,"
                + "`server_id` TEXT"
                + ")");
    }
    
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        db.execSQL("DROP TABLE IF EXISTS pending_incoming_messages");
        db.execSQL("DROP TABLE IF EXISTS pending_outgoing_messages");
        onCreate(db);
    }    
    
    public synchronized void restorePendingMessages()
    {        
        restorePendingIncomingMessages();
        restorePendingOutgoingMessages();
    }
    
    public synchronized void restorePendingIncomingMessages()
    {
        SQLiteDatabase db = getReadableDatabase();   
        Cursor c = db.query("pending_incoming_messages", null, null, null, null, null, null);
        
        int idIndex = c.getColumnIndex("_id");
        int messageTypeIndex = c.getColumnIndex("message_type");
        int messagingIdIndex = c.getColumnIndex("messaging_id");
        int fromIndex = c.getColumnIndex("from_number");
        int toIndex = c.getColumnIndex("to_number");
        int messageIndex = c.getColumnIndex("message");
        int directionIndex = c.getColumnIndex("direction");
        int timestampIndex = c.getColumnIndex("timestamp");
        
        while (c.moveToNext())
        {
            long id = c.getLong(idIndex);
            String messageType = c.getString(messageTypeIndex);   
            long messagingId = c.getLong(messagingIdIndex);
            
            IncomingMessage message;
            
            if (App.MESSAGE_TYPE_SMS.equals(messageType))
            {
                message = new IncomingSms(app);
            }
            else if (App.MESSAGE_TYPE_MMS.equals(messageType))
            {
                message = new IncomingMms(app);                
            }
            else if (App.MESSAGE_TYPE_CALL.equals(messageType))
            {
                message = new IncomingCall(app);
            }
            else
            {
                app.log("Unknown message type " + messageType);
                continue;
            }
            
            message.setMessagingId(messagingId);
            message.setPersistedId(id);
            message.setMessageBody(c.getString(messageIndex));
            message.setFrom(c.getString(fromIndex));
            message.setTo(c.getString(toIndex));
            
            int directionInt = c.getInt(directionIndex);
            IncomingMessage.Direction direction = (directionInt == IncomingMessage.Direction.Sent.ordinal()) ? 
                    IncomingMessage.Direction.Sent : IncomingMessage.Direction.Incoming;
            
            message.setDirection(direction);
            message.setTimestamp(c.getLong(timestampIndex));
            
            app.inbox.forwardMessage(message);
        }
        c.close();
    }    
        
    public synchronized void restorePendingOutgoingMessages()
    {
        SQLiteDatabase db = getReadableDatabase();   
        Cursor c = db.query("pending_outgoing_messages", null, null, null, null, null, null);
        
        int idIndex = c.getColumnIndex("_id");
        int messageTypeIndex = c.getColumnIndex("message_type");
        int fromIndex = c.getColumnIndex("from_number");
        int toIndex = c.getColumnIndex("to_number");
        int messageIndex = c.getColumnIndex("message");
        int priorityIndex = c.getColumnIndex("priority");
        int serverIdIndex = c.getColumnIndex("server_id");
        
        while (c.moveToNext())
        {
            long id = c.getLong(idIndex);
            String messageType = c.getString(messageTypeIndex);   
            
            OutgoingMessage message;
            
            if (App.MESSAGE_TYPE_SMS.equals(messageType))
            {
                message = new OutgoingSms(app);
            }
            else
            {
                app.log("Unknown message type " + messageType);
                continue;
            }
            
            message.setPersistedId(id);
            message.setMessageBody(c.getString(messageIndex));
            message.setFrom(c.getString(fromIndex));
            
            String to = c.getString(toIndex);
            
            if (to == null || "null".equals(to))
            {
                continue;
            }
            
            message.setTo(to);
            
            message.setPriority(c.getInt(priorityIndex));
            message.setServerId(c.getString(serverIdIndex));
            
            app.outbox.sendMessage(message);
        }
        c.close();
    }        
    
    public synchronized void insertPendingMessage(IncomingMessage message)
    {
        if (message.isPersisted())
        {
            return;
        }
        
        SQLiteDatabase db = getWritableDatabase();   
        
        ContentValues values = new ContentValues();
        values.put("message_type", message.getMessageType());
        values.put("messaging_id", message.getMessagingId());
        values.put("from_number", message.getFrom());
        values.put("to_number", message.getTo());
        values.put("message", message.getMessageBody());
        values.put("direction", message.getDirection().ordinal());
        values.put("timestamp", message.getTimestamp());
                                
        try
        {
            long messageId = db.insertOrThrow("pending_incoming_messages", null, values);        
            message.setPersistedId(messageId);
        }
        catch (SQLException ex)
        {
            app.logError("Error saving message to database", ex);
        }        
    }
    
    public synchronized void insertPendingMessage(OutgoingMessage message)
    {
        if (message.isPersisted())
        {
            return;
        }
        
        SQLiteDatabase db = getWritableDatabase();   
        
        ContentValues values = new ContentValues();
        values.put("message_type", message.getMessageType());
        values.put("from_number", message.getFrom());
        values.put("to_number", message.getTo());
        values.put("priority", message.getPriority());
        values.put("message", message.getMessageBody());
        values.put("server_id", message.getServerId());
                                
        try
        {
            long messageId = db.insertOrThrow("pending_outgoing_messages", null, values);        
            message.setPersistedId(messageId);
        }
        catch (SQLException ex)
        {
            app.logError("Error saving message to database", ex);
        }        
    }    
    
    public synchronized void deletePendingMessage(IncomingMessage message)
    {
        if (!message.isPersisted())
        {
            return;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        
        int rows = db.delete("pending_incoming_messages", "_id = ?", new String[] {"" + message.getPersistedId() });
        if (rows == 0)
        {
            app.log("Error deleting pending message from database");
        }
        else
        {
            message.setPersistedId(0);
        }
    }

    public synchronized void deletePendingMessage(OutgoingMessage message)
    {
        if (!message.isPersisted())
        {
            return;
        }
        
        SQLiteDatabase db = getWritableDatabase();
        
        int rows = db.delete("pending_outgoing_messages", "_id = ?", new String[] {"" + message.getPersistedId() });
        if (rows == 0)
        {
            app.log("Error deleting pending message from database");
        }
        else
        {
            message.setPersistedId(0);
        }
    }    
}