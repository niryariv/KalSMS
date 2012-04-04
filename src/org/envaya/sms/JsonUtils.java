
package org.envaya.sms;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JsonUtils {

    public static JSONObject parseResponse(HttpResponse response)
            throws IOException, JSONException
    {
        String responseBody = IOUtils.toString(response.getEntity().getContent(), "UTF-8");            
        return new JSONObject(responseBody);                 
    }

    public static String getErrorText(JSONObject json) 
    {
        JSONObject errorObject = json.optJSONObject("error");
        return errorObject != null ? errorObject.optString("message") : null;
    }
    
    public static void processEvents(JSONObject json, App app, String defaultTo)
            throws JSONException
    {        
        JSONArray events = json.optJSONArray("events");        
        if (events != null)
        {
            int numEvents = events.length();            
            for (int i = 0; i < numEvents; i++)
            {
                JsonUtils.processEvent(events.getJSONObject(i), app, defaultTo);
            }
        }
    }
    
    public static void processEvent(JSONObject json, App app, String defaultTo)
            throws JSONException
    {
        String event = json.getString("event");

        if (App.EVENT_SEND.equals(event))
        {
            for (OutgoingMessage message : JsonUtils.getMessagesList(json, app, defaultTo))
            {
                app.outbox.sendMessage(message);
            }
        }
        else if (App.EVENT_LOG.equals(event))
        {
            app.log(json.getString("message"));
        }
        else if (App.EVENT_SETTINGS.equals(event))
        {                    
            JsonUtils.updateSettings(json, app);
        }
        else if (App.EVENT_CANCEL.equals(event))
        {
            String id = json.getString("id");

            OutgoingMessage message = app.outbox.getMessage(OutgoingMessage.getUriForServerId(id));
            if (message != null && message.isCancelable())
            {
                app.outbox.deleteMessage(message);
                app.outbox.maybeDequeueMessage();
            }
        }
        else if (App.EVENT_CANCEL_ALL.equals(event))
        {
            for (OutgoingMessage message : app.outbox.getMessages())
            {
                if (message.isCancelable())
                {
                    app.outbox.deleteMessage(message);
                }
            }
        }
        else
        {
            app.log("Unknown event" + event);
        }        
    }
    
    public static void updateSettings(JSONObject json, App app)
                throws JSONException
    {
        JSONObject settingsObject = json.optJSONObject("settings");

        if (settingsObject != null && settingsObject.length() > 0)
        {
            SharedPreferences.Editor settings = PreferenceManager.getDefaultSharedPreferences(app).edit();
            
            Iterator it = settingsObject.keys();

            while (it.hasNext())
            {
                String name = (String)it.next();
                Object value = settingsObject.get(name);                    

                if (value instanceof String)
                {
                    settings.putString(name, (String)value);
                }
                else if (value instanceof Boolean)
                {                        
                    settings.putBoolean(name, (Boolean)value);
                }
                else if (value instanceof Integer)
                {
                    settings.putInt(name, (Integer)value);
                }
                else
                {
                    app.log("Unknown setting type " + value.getClass().getName() + " for name " + name);   
                }
            }
            
            settings.commit();
            app.log("Updated app settings");
            app.configuredChanged();
        }
    }
    
    static App app;
    
    // like JSONObject.optString but doesn't convert null to "null"
    private static String optString(JSONObject json, String key, String defaultValue)
    {        
        try
        {            
            Object value = json.get(key);
            
            if (value == null || JSONObject.NULL.equals(value))
            {
                return defaultValue;
            }
            else
            {
                return value.toString();
            }
        }
        catch (JSONException ex)
        {
            return defaultValue;
        }
    }
    
    public static List<OutgoingMessage> getMessagesList(JSONObject json, App app, String defaultTo)
            throws JSONException
    {
        List<OutgoingMessage> messages = new ArrayList<OutgoingMessage>();
        JSONArray messagesList = json.optJSONArray("messages");
        JsonUtils.app = app;
        if (messagesList != null)
        {                    
            int numMessages = messagesList.length();
            
            for (int i = 0; i < numMessages; i++)
            {
                JSONObject messageObject = messagesList.getJSONObject(i);                
                
                OutgoingMessage message = OutgoingMessage.newFromMessageType(app, 
                        optString(messageObject, "type", App.MESSAGE_TYPE_SMS));

                message.setFrom(app.getPhoneNumber());
                                
                String to = optString(messageObject, "to", defaultTo);
                
                if (to == null || "".equals(to) || "null".equals(to))
                {
                    app.log("Received invalid SMS from server (missing recipient)");
                    continue;
                }
                
                String body = optString(messageObject, "message","");
                
                message.setTo(to);
                message.setServerId(optString(messageObject, "id", null));            
                message.setPriority(messageObject.optInt("priority", 0));            
                message.setMessageBody(body);

                messages.add(message);
            }
        }
        
        return messages;
    }
}
