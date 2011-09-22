/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms.task;

import android.os.AsyncTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
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
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.envaya.kalsms.App;
import org.envaya.kalsms.Base64Coder;
import org.envaya.kalsms.OutgoingMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HttpTask extends AsyncTask<String, Void, HttpResponse> {

    protected App app;
    
    protected String url;    
    protected List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();

    private List<FormBodyPart> formParts;
    private boolean useMultipartPost = false;    
    
    private HttpPost post;
    
    public HttpTask(App app, BasicNameValuePair... paramsArr)
    {
        super();
        this.app = app;        
        this.url = app.getServerUrl();
        params = new ArrayList<BasicNameValuePair>(Arrays.asList(paramsArr));            
        params.add(new BasicNameValuePair("version", "" + app.getPackageInfo().versionCode));
        params.add(new BasicNameValuePair("phone_number", app.getPhoneNumber()));                       
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
        if (url.length() == 0) {
            app.log("Can't contact server; Server URL not set");                        
            return null;
        }

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
                post.setEntity(new UrlEncodedFormEntity(params));            
            }
                        
            HttpClient client = app.getHttpClient();
            
            String signature = getSignature();            
            
            post.setHeader("X-Kalsms-Signature", signature);
            
            HttpResponse response = client.execute(post);            

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) 
            {
                return response;
            } 
            else if (statusCode == 403)
            {
                response.getEntity().consumeContent();
                app.log("Failed to authenticate to server");
                app.log("(Phone number or password may be incorrect)");                
                return null;
            }
            else 
            {
                response.getEntity().consumeContent();
                app.log("Received HTTP " + statusCode + " from server");
                return null;
            }            
        } 
        catch (IOException ex) 
        {
            post.abort();
            app.logError("Error while contacting server", ex);
            return null;
        }
        catch (Throwable ex) 
        {
            post.abort();
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

            Node firstChild = smsElement.getFirstChild();                
            sms.setMessageBody(firstChild != null ? firstChild.getNodeValue(): "");            
            
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
                response.getEntity().consumeContent();
            }
            catch (Throwable ex)
            {
                post.abort();
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
