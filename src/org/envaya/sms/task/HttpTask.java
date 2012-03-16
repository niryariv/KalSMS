/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.sms.task;

import org.envaya.sms.XmlUtils;
import org.envaya.sms.OutgoingSms;
import org.envaya.sms.OutgoingMessage;
import org.envaya.sms.App;
import org.envaya.sms.Base64Coder;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
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
        
        params.add(new BasicNameValuePair("version", "" + app.getPackageInfo().versionCode));
        params.add(new BasicNameValuePair("phone_number", app.getPhoneNumber()));
        params.add(new BasicNameValuePair("send_limit", "" + app.getOutgoingMessageLimit()));
        
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
        
    protected List<OutgoingMessage> parseResponseXML(HttpResponse response)
             throws IOException, ParserConfigurationException, SAXException
    {
        List<OutgoingMessage> messages = new ArrayList<OutgoingMessage>();
        Document xml = XmlUtils.parseResponse(response);     

        Element messagesElement = (Element) xml.getElementsByTagName("messages").item(0);
        if (messagesElement != null)
        {
            NodeList messageNodes = messagesElement.getChildNodes();
            int numNodes = messageNodes.getLength();
            for (int i = 0; i < numNodes; i++) 
            {
                Element messageElement = (Element) messageNodes.item(i);

                OutgoingMessage message = new OutgoingSms(app);

                message.setFrom(app.getPhoneNumber());
            
                String to = messageElement.getAttribute("to");
            
                message.setTo(to.equals("") ? getDefaultToAddress() : to);
            
                String serverId = messageElement.getAttribute("id");
            
                message.setServerId(serverId.equals("") ? null : serverId);
            
                String priorityStr = messageElement.getAttribute("priority");
            
                if (!priorityStr.equals(""))
                {
                    try
                    {
                        message.setPriority(Integer.parseInt(priorityStr));
                    }
                    catch (NumberFormatException ex)
                    {
                        app.log("Invalid message priority: " + priorityStr);
                    }
                }
            
                message.setMessageBody(XmlUtils.getElementText(messageElement));
            
                messages.add(message);
            }
        }
        return messages;
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
        Document xml = XmlUtils.parseResponse(response);
        String error = XmlUtils.getErrorText(xml);
        if (error != null)
        {
            app.log(error);
        }        
        else
        {
            app.log("HTTP " +response.getStatusLine().getStatusCode());
        }
    }
}
