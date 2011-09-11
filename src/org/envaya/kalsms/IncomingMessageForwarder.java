package org.envaya.kalsms;

import android.app.Activity;
import android.app.PendingIntent;
import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class IncomingMessageForwarder extends BroadcastReceiver {

    private App app;

    public List<OutgoingSmsMessage> sendMessageToServer(SmsMessage sms) {

        String message = sms.getDisplayMessageBody();
        String sender = sms.getDisplayOriginatingAddress();
        String recipient = app.getPhoneNumber();

        app.log("Received SMS from " + sender);

        if (message == null || message.length() == 0) {
            return new ArrayList<OutgoingSmsMessage>();
        }

        List<OutgoingSmsMessage> replies = new ArrayList<OutgoingSmsMessage>();

        try {

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("from", sender));
            params.add(new BasicNameValuePair("to", recipient));
            params.add(new BasicNameValuePair("message", message));
            params.add(new BasicNameValuePair("secret", app.getPassword()));

            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(app.getIncomingUrl());
            post.setEntity(new UrlEncodedFormEntity(params));

            app.log("Forwarding incoming SMS to server");

            HttpResponse response = client.execute(post);

            InputStream responseStream = response.getEntity().getContent();
            DocumentBuilder xmlBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document xml = xmlBuilder.parse(responseStream);

            NodeList smsNodes = xml.getElementsByTagName("Sms");
            for (int i = 0; i < smsNodes.getLength(); i++) {
                Element smsElement = (Element) smsNodes.item(i);

                OutgoingSmsMessage reply = new OutgoingSmsMessage();

                reply.setFrom(recipient);
                reply.setTo(sender);
                reply.setMessage(smsElement.getFirstChild().getNodeValue());

                replies.add(reply);
            }
        } catch (SAXException ex) {
            app.logError("Error parsing response from server while forwarding incoming message", ex);
        } catch (IOException ex) {
            app.logError("Error forwarding incoming message to server", ex);
        } catch (ParserConfigurationException ex) {
            app.logError("Error configuring XML parser", ex);
        }

        return replies;
    }

    public void smsReceived(Intent intent) {

        for (SmsMessage sms : getMessagesFromIntent(intent)) {
            List<OutgoingSmsMessage> replies = sendMessageToServer(sms);

            for (OutgoingSmsMessage reply : replies) 
            {                
                app.sendSMS(reply);
            }

            //DeleteSMSFromInbox(context, mesg);
        }

    }


    @Override
    // source: http://www.devx.com/wireless/Article/39495/1954
    public void onReceive(Context context, Intent intent) {
        try {
            this.app = new App(context);

            String action = intent.getAction();

            if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
                smsReceived(intent);
            } 
        } catch (Throwable ex) {
            app.logError("Unexpected error in IncomingMessageForwarder", ex, true);
        }
    }

    /*
    private void DeleteSMSFromInbox(Context context, SmsMessage mesg) {
        Log.d("KALSMS", "try to delete SMS");
        try {
            Uri uriSms = Uri.parse("content://sms/inbox");
            StringBuilder sb = new StringBuilder();
            sb.append("address='" + mesg.getOriginatingAddress() + "' AND ");
            sb.append("body='" + mesg.getMessageBody() + "'");
            Cursor c = context.getContentResolver().query(uriSms, null, sb.toString(), null, null);
            c.moveToFirst();
            int thread_id = c.getInt(1);
            context.getContentResolver().delete(Uri.parse("content://sms/conversations/" + thread_id), null, null);
            c.close();
        } catch (Exception ex) {
            // deletions don't work most of the time since the timing of the
            // receipt and saving to the inbox
            // makes it difficult to match up perfectly. the SMS might not be in
            // the inbox yet when this receiver triggers!
            Log.d("SmsReceiver", "Error deleting sms from inbox: " + ex.getMessage());
        }
    }
    */
    
    // from http://github.com/dimagi/rapidandroid 
    // source: http://www.devx.com/wireless/Article/39495/1954

    private SmsMessage[] getMessagesFromIntent(Intent intent) {
        SmsMessage retMsgs[] = null;
        Bundle bdl = intent.getExtras();
        Object pdus[] = (Object[]) bdl.get("pdus");
        retMsgs = new SmsMessage[pdus.length];
        for (int n = 0; n < pdus.length; n++) {
            byte[] byteData = (byte[]) pdus[n];
            retMsgs[n] = SmsMessage.createFromPdu(byteData);
        }
        return retMsgs;
    }
}