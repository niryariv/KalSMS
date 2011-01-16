package kalsms.niryariv.itp;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.util.Log;

public class TargetUrlRequest {
	
	public String openURL(String sender, String message, String targetUrl, Boolean isPollRequest) {
		
		List<NameValuePair> qparams = new ArrayList<NameValuePair>();
        
		if(sender.trim().length() > 0 && message.trim().length() > 0) {
	        qparams.add(new BasicNameValuePair("sender", sender));
	        qparams.add(new BasicNameValuePair("msg", message));	        
		} else if (isPollRequest) {
        	qparams.add(new BasicNameValuePair("poll", "true"));
        }

		String url = targetUrl + "?" + URLEncodedUtils.format(qparams, "UTF-8");

        try {
	        HttpClient client = new DefaultHttpClient();  
	        HttpGet get = new HttpGet(url);
	        
	        HttpResponse responseGet = client.execute(get);  
	        HttpEntity resEntityGet = responseGet.getEntity();  
	        if (resEntityGet != null) {  
	        	String resp = EntityUtils.toString(resEntityGet);
	        	Log.e("KALSMS", "HTTP RESP" + resp);
	            return resp;
	        }
		} catch (Exception e) {
			Log.e("KALSMS", "HTTP REQ FAILED:" + url);
			e.printStackTrace();
		}
		return "";
	}

	public ArrayList<ArrayList<String>> parseXML(String xml) {
    	ArrayList<ArrayList<String>> output = new ArrayList<ArrayList<String>>();
    	
    	try {
    		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        	Document doc = dBuilder.parse(new InputSource(new StringReader(xml)));
        	
        	NodeList rnodes = doc.getElementsByTagName("reply");
        	
            NodeList nodes = rnodes.item(0).getChildNodes(); 
            
             for (int i=0; i < nodes.getLength(); i++) {
            	 try {
	            	 List<String> item = new ArrayList<String>();
	             	
	            	 Node node = nodes.item(i);
	            	 if (node.getNodeType() != Node.ELEMENT_NODE) continue;
	            	 
	            	 Element e = (Element) node;
	            	 String nodeName = e.getNodeName();
	            	 
	            	 if (nodeName.equalsIgnoreCase("sms")) {
	            		 if (!e.getAttribute("phone").equals("")) {
	            			 item.add(e.getAttribute("phone"));
	            			 item.add(e.getFirstChild().getNodeValue());
	            			 output.add((ArrayList<String>) item);
	            		 }
	            	 } else if (nodeName.equalsIgnoreCase("sms-to-sender")) {
	        			 item.add("sender");
	        			 item.add(e.getFirstChild().getNodeValue());
	        			 output.add((ArrayList<String>) item);
	            	 } else {
	            		 continue;
	            	 }
            	 } catch (Exception e){
            		 Log.e("KALSMS", "FAILED PARSING XML NODE# " + i  );
            	 }
             }
             Log.e("KALSMS", "PARSING XML RETURNS " + output );
             return (output);

        } catch (Exception e) {
        	Log.e("KALSMS", "PARSING XML FAILED: " + xml );
            e.printStackTrace();
            return (output);
        }
    }	
}
