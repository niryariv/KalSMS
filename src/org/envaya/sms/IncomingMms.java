
package org.envaya.sms;

import android.net.Uri;
import java.io.IOException;
import java.util.ArrayList;

import org.json.*;

import java.util.List;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.task.ForwarderTask;

public class IncomingMms extends IncomingMessage {
    List<MmsPart> parts;
    long id;
    String contentLocation;
    
    public IncomingMms(App app, String from, long id)
    {
        super(app, from);
        this.parts = new ArrayList<MmsPart>();
        this.id = id;
    }        
    
    public String getDisplayType()
    {
        return "MMS";
    }
    
    public List<MmsPart> getParts()
    {
        return parts;
    }   
    
    public void addPart(MmsPart part)
    {
        parts.add(part);
    }
    
    public long getId()
    {
        return id;
    }
    
    public String getContentLocation()
    {
        return contentLocation;
    }
    
    public void setContentLocation(String contentLocation)
    {
        this.contentLocation = contentLocation;
    }
            
    
    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("MMS id=");
        builder.append(id);
        builder.append(" from=");
        builder.append(from);
        builder.append(":\n");
        
        for (MmsPart part : parts)
        {
            builder.append(" ");
            builder.append(part.toString());
            builder.append("\n");
        }
        return builder.toString();
    }
    
    public void tryForwardToServer()
    {        
        if (numRetries > 0)
        {
            app.log("Retrying forwarding MMS from " + from);
        }
        else
        {
            app.log("Forwarding MMS to server...");
        }
        
        List<FormBodyPart> formParts = new ArrayList<FormBodyPart>();        
        
        int i = 0;
        
        String message = "";
        JSONArray partsMetadata = new JSONArray();
        
        for (MmsPart part : parts)
        {
            String formFieldName = "part" + i;
            String text = part.getText();
            String contentType = part.getContentType();
            String partName = part.getName();
            
            if ("text/plain".equals(contentType))
            {
                message = text;
            }
            
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
        
        ForwarderTask task = new ForwarderTask(this,
            new BasicNameValuePair("from", getFrom()),
            new BasicNameValuePair("message", message),
            new BasicNameValuePair("message_type", App.MESSAGE_TYPE_MMS),
            new BasicNameValuePair("mms_parts", partsMetadata.toString())
        );
        
        task.setFormParts(formParts);        
        task.execute();
    }
    
    public Uri getUri() 
    {
        return Uri.withAppendedPath(App.INCOMING_URI, "mms/" + id);
    }       
}
