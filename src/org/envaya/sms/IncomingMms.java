
package org.envaya.sms;

import android.net.Uri;
import java.io.IOException;
import java.util.ArrayList;

import org.json.*;

import java.util.List;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.envaya.sms.task.ForwarderTask;

public class IncomingMms extends IncomingMessage {
    private List<MmsPart> parts;
    
    public IncomingMms(App app, long timestamp, long messagingId)
    {
        super(app, null, timestamp);
        this.messagingId = messagingId;
    }

    public IncomingMms(App app)
    {
        super(app);
    }
    
    @Override 
    public String getFrom()
    {
        if (from == null || from.length() == 0)
        {
            // lazy-load sender number from Messaging database as needed
            from = app.getMessagingUtils().getMmsSenderNumber(messagingId);   
        }
        return from;
    }
    
    public List<MmsPart> getParts()
    {
        if (parts == null)
        {
            // lazy-load mms parts from Messaging database as needed
            this.parts = new ArrayList<MmsPart>();
            for (MmsPart part : app.getMessagingUtils().getMmsParts(messagingId))
            {
                parts.add(part);
            }
        }
        return parts;
    }
    
    public String getDisplayType()
    {
        return "MMS";
    }    
    
    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("MMS id=");
        builder.append(messagingId);
        builder.append(" from=");
        builder.append(from);
        builder.append(":\n");
        
        for (MmsPart part : getParts())
        {
            builder.append(" ");
            builder.append(part.toString());
            builder.append("\n");
        }
        return builder.toString();
    }
    
    @Override
    protected ForwarderTask getForwarderTask()
    {        
        List<FormBodyPart> formParts = new ArrayList<FormBodyPart>();        
        
        int i = 0;
                
        JSONArray partsMetadata = new JSONArray();
        
        for (MmsPart part : getParts())
        {
            String formFieldName = "part" + i;
            String text = part.getText();
            String contentType = part.getContentType();
            String partName = part.getName();            
            
            ContentBody body;
            
            if (text != null)
            {                
                if (contentType != null)
                {
                    contentType += "; charset=UTF-8";
                }                
                
                body = new ByteArrayBody(text.getBytes(), contentType, partName);
            }
            else
            {
                // avoid using InputStreamBody because it forces the HTTP request
                // to be sent using Transfer-Encoding: chunked, which is not
                // supported by some web servers (including nginx)
                
                try
                {
                    body = new ByteArrayBody(part.getData(), contentType, partName);
                }
                catch (IOException ex)
                {
                    app.logError("Error reading data for " + part.toString(), ex);
                    continue;
                }
            }

            try
            {
                JSONObject partMetadata = new JSONObject();
                partMetadata.put("name", formFieldName);
                partMetadata.put("cid", part.getContentId());
                partMetadata.put("type", part.getContentType());
                partMetadata.put("filename", part.getName());               
                partsMetadata.put(partMetadata);
            }
            catch (JSONException ex)
            {
                app.logError("Error encoding MMS part metadata for " + part.toString(), ex);
                continue;
            }
            
            
            formParts.add(new FormBodyPart(formFieldName, body));                            
            i++;
        }
        
        ForwarderTask task = super.getForwarderTask();        
        task.addParam("mms_parts", partsMetadata.toString());
        task.setFormParts(formParts);        
        return task;
    }    

    @Override
    public String getMessageBody()
    {
        for (MmsPart part : getParts())
        {
            if ("text/plain".equals(part.getContentType()))
            {
                return part.getText();
            }
        }
        
        return "";
    }        
    
    public Uri getUri() 
    {
        return Uri.withAppendedPath(App.INCOMING_URI, "mms/" + messagingId);
    }       
    
    public String getMessageType()
    {
        return App.MESSAGE_TYPE_MMS;
    }    
    
    @Override 
    public void onForwardComplete()
    {
        if (!app.getKeepInInbox())
        {
            app.log("Deleting MMS " + getMessagingId() + " from inbox...");
            app.getMessagingUtils().deleteFromMmsInbox(this);
        }
    }
}
