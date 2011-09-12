/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms;

import android.os.AsyncTask;
import android.util.Base64;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HttpTask extends AsyncTask<BasicNameValuePair, Void, HttpResponse> {

    private App app;
    
    public HttpTask(App app)
    {
        super();
        this.app = app;
    }
        
    private String getSignature(String url, BasicNameValuePair... params)
    {
        try {
            Arrays.sort(params, new Comparator() {
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
            
            return Base64.encodeToString(digest, Base64.NO_WRAP);            
            
        } catch (Exception ex) {
            app.logError("Error computing signature", ex);
        }
        return "";
    }    
    
    protected HttpResponse doInBackground(BasicNameValuePair... params) {
        try
        {  
            String url = app.getServerUrl();
            HttpPost post = new HttpPost(url);
                       
            post.setEntity(new UrlEncodedFormEntity(Arrays.asList(params)));            
                        
            String signature = this.getSignature(url, params);
            
            post.setHeader("X-Kalsms-PhoneNumber", app.getPhoneNumber());
            post.setHeader("X-Kalsms-Signature", signature);
            
            HttpClient client = app.getHttpClient();
            HttpResponse response = client.execute(post);            
            
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) 
            {
                return response;
            } 
            else if (statusCode == 403)
            {
                app.log("Failed to authenticate to server");
                app.log("(Phone number or password may be incorrect)");                
                return null;
            }
            else 
            {
                app.log("Received HTTP " + statusCode + " from server");
                return null;
            }            
        } 
        catch (Throwable ex) 
        {
            app.logError("Error while contacting server", ex);
            return null;
        }
    }
        
    protected String getDefaultToAddress()
    {
        return "";
    }
    
    protected List<OutgoingSmsMessage> parseResponseXML(HttpResponse response)
             throws IOException, ParserConfigurationException, SAXException
    {
        List<OutgoingSmsMessage> messages = new ArrayList<OutgoingSmsMessage>();
        InputStream responseStream = response.getEntity().getContent();
        DocumentBuilder xmlBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document xml = xmlBuilder.parse(responseStream);

        NodeList smsNodes = xml.getElementsByTagName("Sms");
        for (int i = 0; i < smsNodes.getLength(); i++) {
            Element smsElement = (Element) smsNodes.item(i);
            OutgoingSmsMessage sms = new OutgoingSmsMessage();

            sms.setFrom(app.getPhoneNumber());
            
            String to = smsElement.getAttribute("to");
            
            sms.setTo(to.equals("") ? getDefaultToAddress() : to);
            
            String serverId = smsElement.getAttribute("id");
            
            sms.setServerId(serverId.equals("") ? null : serverId);

            Node firstChild = smsElement.getFirstChild();                
            sms.setMessage(firstChild != null ? firstChild.getNodeValue(): "");            
            
            messages.add(sms);
        }
        return messages;
    }            
    
    @Override
    protected void onPostExecute(HttpResponse response) {
        if (response != null)
        {
            try
            {
                handleResponse(response);            
            }
            catch (Throwable ex)
            {
                app.logError("Error processing server response", ex);
                handleFailure();
            }
        }
        else
        {
            handleFailure();
        }
    }
    
    protected void handleResponse(HttpResponse response) throws Exception
    {
    }    
    
    protected void handleFailure()
    {
    }        
}
