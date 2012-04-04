/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.sms.task;

import org.envaya.sms.OutgoingMessage;
import org.envaya.sms.JsonUtils;
import org.envaya.sms.Base64Coder;
import org.envaya.sms.App;
import org.envaya.sms.XmlUtils;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class HttpTask extends BaseHttpTask {

    private String logEntries;    
    
    private boolean retryOnConnectivityError;
    
    private BasicNameValuePair[] ctorParams;
        
    public HttpTask(App app, BasicNameValuePair... paramsArr)
    {
        super(app, app.getServerUrl(), paramsArr);
        this.ctorParams = paramsArr;
    }
    
    public void setRetryOnConnectivityError(boolean retry)
    {
        this.retryOnConnectivityError = retry;  // doesn't work with addParam!
    }
    
    protected HttpTask getCopy()
    {
        return new HttpTask(app, ctorParams); // doesn't work with addParam!
    }        
    
    private String getSignature()
            throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        Collections.sort(params, new Comparator() {
            public int compare(Object o1, Object o2)
            {
                return ((BasicNameValuePair)o1).getName().compareTo(((BasicNameValuePair)o2).getName());
            }
        });

        StringBuilder builder = new StringBuilder();
        builder.append(url);
        for (BasicNameValuePair param : params)
        {
            builder.append(",");
            builder.append(param.getName());
            builder.append("=");
            builder.append(param.getValue());                
        }
        builder.append(",");
        builder.append(app.getPassword());

        String value = builder.toString();

        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(value.getBytes("utf-8"));

        byte[] digest = md.digest(); 

        return new String(Base64Coder.encode(digest));            
    }    
    
    @Override
    protected HttpResponse doInBackground(String... ignored) {        
        url = app.getServerUrl();        
        
        if (url.length() == 0) {
            app.log("Can't contact server; Server URL not set");                        
            return null;
        }

        logEntries = app.getNewLogEntries();        
        
        params.add(new BasicNameValuePair("phone_number", app.getPhoneNumber()));
        params.add(new BasicNameValuePair("phone_id", app.getPhoneID()));
        params.add(new BasicNameValuePair("phone_token", app.getPhoneToken()));
        params.add(new BasicNameValuePair("send_limit", "" + app.getOutgoingMessageLimit()));
        params.add(new BasicNameValuePair("now", "" + System.currentTimeMillis()));
        params.add(new BasicNameValuePair("settings_version", "" + app.getSettingsVersion()));
        
        Intent lastBatteryIntent = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        
        if (lastBatteryIntent != null)
        {
            // BatteryManager.EXTRA_* constants introduced in API level 5 (2.0)
            int rawLevel = lastBatteryIntent.getIntExtra("level", -1);
            int scale = lastBatteryIntent.getIntExtra("scale", -1);
            
            int pctLevel = (rawLevel > 0 && scale > 0) ? (rawLevel * 100 / scale) : rawLevel;
            
            if (pctLevel >= 0)
            {
                params.add(new BasicNameValuePair("battery", "" + pctLevel));
            }
            
            int plugged = lastBatteryIntent.getIntExtra("plugged", -1);            
            
            if (plugged >= 0)
            {
                params.add(new BasicNameValuePair("power", "" + plugged));
            }
        }
        
        ConnectivityManager cm = 
            (ConnectivityManager)app.getSystemService(App.CONNECTIVITY_SERVICE);
        
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();        
        if (activeNetwork != null)
        {
            params.add(new BasicNameValuePair("network", "" + activeNetwork.getTypeName()));
        }
        
        params.add(new BasicNameValuePair("log", logEntries));
                
        return super.doInBackground();        
    }
    
    @Override
    protected HttpPost makeHttpPost()
            throws Exception
    {
        HttpPost httpPost = super.makeHttpPost();

        String signature = getSignature();
            
        httpPost.setHeader("X-Request-Signature", signature);
        
        return httpPost;   
    }
    
    protected String getDefaultToAddress()
    {
        return "";
    }    
    
    protected void handleResponseJSON(JSONObject json)
            throws JSONException
    {
        JsonUtils.processEvents(json, app, getDefaultToAddress());
    }
        
    protected void handleResponseXML(Document xml)
             throws IOException, ParserConfigurationException, SAXException
    {
        for (OutgoingMessage message : XmlUtils.getMessagesList(xml, app, getDefaultToAddress()))
        {
            app.outbox.sendMessage(message);                    
        }                        
    }    
    
    protected void handleUnknownContentType(String contentType)
            throws Exception
    {
        // old server API only mandated valid content type for action=outgoing
        app.log("Warning: Unknown response type " + contentType);
    }
    
    @Override
    protected void handleFailure()
    {
        app.ungetNewLogEntries(logEntries);   
    }            
    
    @Override
    protected void handleResponseException(Throwable ex)
    {
        app.logError("Error in server response", ex);
    }                
        
    @Override
    protected void handleRequestException(Throwable ex)
    {    
        if (ex instanceof IOException)
        {
            app.logError("Error while contacting server", ex);
            
            if (ex instanceof UnknownHostException || ex instanceof SocketTimeoutException)
            {                
                if (retryOnConnectivityError)
                {
                    app.addQueuedTask(getCopy());
                }
                
                app.onConnectivityError();
            }
        }
        else
        {
            app.logError("Unexpected error while contacting server", ex, true);           
        }        
    }    
    
    @Override
    public void handleErrorResponse(HttpResponse response) throws Exception
    {            
        app.log(getErrorText(response));       
    }
    
    @Override
    protected void handleResponse(HttpResponse response) throws Exception {

        String contentType = getContentType(response);
        
        if (contentType.startsWith("application/json"))
        {
            String responseBody = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
        
            JSONObject json = new JSONObject(responseBody);
            
            handleResponseJSON(json);
        }
        else if (contentType.startsWith("text/xml"))
        {
            Document xml = XmlUtils.parseResponse(response);     
           
            handleResponseXML(xml);
        }
        else
        {
            handleUnknownContentType(contentType);
        }
        
        // if we get a valid server response after a connectivity error, then forward any pending messages
        if (app.hasConnectivityError())
        {
            app.onConnectivityRestored();
        }        
    }
}
