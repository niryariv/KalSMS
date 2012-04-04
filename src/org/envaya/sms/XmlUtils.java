package org.envaya.sms;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class XmlUtils {
    
    public static Document parseResponse(HttpResponse response)
            throws IOException, ParserConfigurationException, SAXException {
        InputStream responseStream = response.getEntity().getContent();
        DocumentBuilder xmlBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return xmlBuilder.parse(responseStream);
    }

    public static String getElementText(Element element) {
        StringBuilder text = new StringBuilder();
        NodeList childNodes = element.getChildNodes();
        int numChildren = childNodes.getLength();
        for (int j = 0; j < numChildren; j++) {
            text.append(childNodes.item(j).getNodeValue());
        }
        return text.toString();
    }

    public static String getErrorText(Document xml) {
        NodeList errorNodes = xml.getElementsByTagName("error");
        if (errorNodes.getLength() > 0) {
            Element errorElement = (Element) errorNodes.item(0);
            return getElementText(errorElement);
        }
        return null;
    }
    
    public static List<OutgoingMessage> getMessagesList(Document xml, App app, String defaultTo)
    {
        List<OutgoingMessage> messages = new ArrayList<OutgoingMessage>();
        
        Element messagesElement = (Element) xml.getElementsByTagName("messages").item(0);
        if (messagesElement != null)
        {
            NodeList messageNodes = messagesElement.getChildNodes();
            int numNodes = messageNodes.getLength();
            for (int i = 0; i < numNodes; i++) 
            {
                Element messageElement = (Element) messageNodes.item(i);

                String nodeName = messageElement.getNodeName();
                
                OutgoingMessage message = OutgoingMessage.newFromMessageType(app, nodeName);

                message.setFrom(app.getPhoneNumber());
            
                String to = messageElement.getAttribute("to");
            
                message.setTo("".equals(to) ? defaultTo : to);
            
                String serverId = messageElement.getAttribute("id");
            
                message.setServerId("".equals(serverId) ? null : serverId);
            
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
}
