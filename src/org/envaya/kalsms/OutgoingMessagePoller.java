package org.envaya.kalsms;

import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class OutgoingMessagePoller extends BroadcastReceiver {

        private App app;
    
	@Override
	public void onReceive(Context context, Intent intent) {
            try
            {
                app = new App(context);

                app.log("Checking for outgoing messages");

                for (OutgoingSmsMessage sms : getOutgoingMessages())
                {
                    app.sendSMS(sms);
                }
            }
            catch (Throwable ex)
            {
                app.logError("Unexpected error in OutgoingMessagePoller", ex, true);
            }	
	}
        
    public List<OutgoingSmsMessage> getOutgoingMessages() {
        List<OutgoingSmsMessage> messages = new ArrayList<OutgoingSmsMessage>();
               
        try {
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("from", app.getPhoneNumber()));
            params.add(new BasicNameValuePair("secret", app.getPassword()));

            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(app.getOutgoingUrl());
            post.setEntity(new UrlEncodedFormEntity(params));            
            
            HttpResponse response = client.execute(post);

            InputStream responseStream = response.getEntity().getContent();
            DocumentBuilder xmlBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document xml = xmlBuilder.parse(responseStream);
            
            NodeList smsNodes = xml.getElementsByTagName("Sms");
            for (int i = 0; i < smsNodes.getLength(); i++) {
                Element smsElement = (Element) smsNodes.item(i);
                OutgoingSmsMessage sms = new OutgoingSmsMessage();
                
                sms.setFrom(app.getPhoneNumber());
                sms.setTo(smsElement.getAttribute("to"));
                sms.setMessage(smsElement.getFirstChild().getNodeValue());
                sms.setServerId(smsElement.getAttribute("id"));
                
                messages.add(sms);
            }
        } catch (SAXException ex) {
            app.logError("Error parsing response from server while retreiving outgoing messages", ex);
        } catch (IOException ex) {
            app.logError("Error retreiving outgoing messages from server", ex);
        } catch (ParserConfigurationException ex) {
            app.logError("Error configuring XML parser", ex);
        }
        
        return messages;
    }
        
}
