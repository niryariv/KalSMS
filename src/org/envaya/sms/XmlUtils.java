package org.envaya.sms;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
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
}
