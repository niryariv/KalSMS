/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.sms.task;

import android.os.AsyncTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.App;
import org.envaya.sms.Base64Coder;
import org.envaya.sms.OutgoingMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HttpTask extends AsyncTask<String, Void, HttpResponse> {

    protected App app;
    
    protected String url;    
    protected List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
    
    protected BasicNameValuePair[] paramsArr;

    private List<FormBodyPart> formParts;
    private boolean useMultipartPost = false;    
    
    private HttpPost post;
    private String logEntries;    
    
    private boolean retryOnConnectivityError;
        
    public HttpTask(App app, BasicNameValuePair... paramsArr)
    {
        super();
        this.app = app;                
        this.paramsArr = paramsArr;        
        params = new ArrayList<BasicNameValuePair>(Arrays.asList(paramsArr));
    }
    
    public void setRetryOnConnectivityError(boolean retry)
    {
        this.retryOnConnectivityError = retry;
    }
    
    protected HttpTask getCopy()
    {
        return new HttpTask(app, paramsArr);
    }    
    
    public void setFormParts(List<FormBodyPart> formParts)
    {
        useMultipartPost = true;
        this.formParts = formParts;
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
    
    protected HttpResponse doInBackground(String... ignored) {        
        url = app.getServerUrl();        
        
        if (url.length() == 0) {
            app.log("Can't contact server; Server URL not set");                        
            return null;
        }

        logEntries = app.getNewLogEntries();
        
        params.add(new BasicNameValuePair("version", "" + app.getPackageInfo().versionCode));
        params.add(new BasicNameValuePair("phone_number", app.getPhoneNumber()));
        params.add(new BasicNameValuePair("log", logEntries));        
                
        post = new HttpPost(url);        
        
        try
        {              
            if (useMultipartPost)
            {
                MultipartEntity entity = new MultipartEntity();//HttpMultipartMode.BROWSER_COMPATIBLE);

                Charset charset = Charset.forName("UTF-8");
                
                for (BasicNameValuePair param : params)
                {
                    entity.addPart(param.getName(), new StringBody(param.getValue(), charset));
                }
                
                for (FormBodyPart formPart : formParts)
                {
                    entity.addPart(formPart);
                }
                post.setEntity(entity);                                                
            }
            else
            {
                post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));            
            }
                        
            HttpClient client = app.getHttpClient();
            
            String signature = getSignature();            
            
            post.setHeader("X-Request-Signature", signature);
            
            HttpResponse response = client.execute(post);            

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) 
            {
                return response;
            } 
            else if (statusCode == 403)
            {
                response.getEntity().consumeContent();
                app.ungetNewLogEntries(logEntries);
                app.log("Failed to authenticate to server");
                app.log("(Phone number or password may be incorrect)");                
                return null;
            }
            else 
            {
                response.getEntity().consumeContent();
                app.ungetNewLogEntries(logEntries);
                app.log("Received HTTP " + statusCode + " from server");
                return null;
            }
        } 
        catch (IOException ex) 
        {
            post.abort();
            app.ungetNewLogEntries(logEntries);
            app.logError("Error while contacting server", ex);
            
            if (ex instanceof UnknownHostException || ex instanceof SocketTimeoutException)
            {
                if (retryOnConnectivityError)
                {
                    app.addQueuedTask(getCopy());
                }
                
                app.asyncCheckConnectivity();
            }
            return null;
        }
        catch (Throwable ex) 
        {
            post.abort();
            app.ungetNewLogEntries(logEntries);
            app.logError("Unexpected error while contacting server", ex, true);
            return null;
        }        
    }
    
    protected String getDefaultToAddress()
    {
        return "";
    }
    
    protected List<OutgoingMessage> parseResponseXML(HttpResponse response)
             throws IOException, ParserConfigurationException, SAXException
    {
        List<OutgoingMessage> messages = new ArrayList<OutgoingMessage>();
        InputStream responseStream = response.getEntity().getContent();
        DocumentBuilder xmlBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document xml = xmlBuilder.parse(responseStream);

        NodeList smsNodes = xml.getElementsByTagName("sms");
        for (int i = 0; i < smsNodes.getLength(); i++) {
            Element smsElement = (Element) smsNodes.item(i);
            
            OutgoingMessage sms = new OutgoingMessage(app);

            sms.setFrom(app.getPhoneNumber());
            
            String to = smsElement.getAttribute("to");
            
            sms.setTo(to.equals("") ? getDefaultToAddress() : to);
            
            String serverId = smsElement.getAttribute("id");
            
            sms.setServerId(serverId.equals("") ? null : serverId);
            
            String priorityStr = smsElement.getAttribute("priority");
            
            if (!priorityStr.equals(""))
            {
                try
                {
                    sms.setPriority(Integer.parseInt(priorityStr));
                }
                catch (NumberFormatException ex)
                {
                    app.log("Invalid message priority: " + priorityStr);
                }
            }

            StringBuilder messageBody = new StringBuilder();
            NodeList childNodes = smsElement.getChildNodes();
            int numChildren = childNodes.getLength();
            for (int j = 0; j < numChildren; j++)
            {
                messageBody.append(childNodes.item(j).getNodeValue());
            }
            
            sms.setMessageBody(messageBody.toString());            
            
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
                post.abort();
                app.logError("Error processing server response", ex);
                handleFailure();
            }
            try
            {
                response.getEntity().consumeContent();
            }
            catch (IOException ex)
            {
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
