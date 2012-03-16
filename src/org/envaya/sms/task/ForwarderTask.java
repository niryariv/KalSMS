package org.envaya.sms.task;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.IncomingMessage;
import org.envaya.sms.OutgoingMessage;

public class ForwarderTask extends HttpTask {

    private IncomingMessage message;

    public ForwarderTask(IncomingMessage message, BasicNameValuePair... paramsArr) {
        super(message.app, paramsArr);
        this.message = message;                
    }
    
    @Override
    public boolean isValidContentType(String contentType)
    {
        return contentType.startsWith("text/xml");
    }
    
    @Override
    protected String getDefaultToAddress() {
        return message.getFrom();
    }

    @Override
    protected void handleResponse(HttpResponse response) throws Exception {

        for (OutgoingMessage reply : parseResponseXML(response)) {
            app.outbox.sendMessage(reply);
        }
        app.inbox.messageForwarded(message);
        
        super.handleResponse(response);
    }

    @Override
    protected void handleFailure() {        
        app.inbox.messageFailed(message);
    }
}
