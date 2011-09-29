package org.envaya.sms.task;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.App;
import org.envaya.sms.IncomingMessage;
import org.envaya.sms.OutgoingMessage;

public class ForwarderTask extends HttpTask {

    private IncomingMessage message;

    public ForwarderTask(IncomingMessage message, BasicNameValuePair... paramsArr) {
        super(message.app, paramsArr);
        this.message = message;
                
        params.add(new BasicNameValuePair("action", App.ACTION_INCOMING));
        params.add(new BasicNameValuePair("from", message.getFrom()));
        params.add(new BasicNameValuePair("timestamp", "" + message.getTimestamp()));
    }
    
    @Override
    protected String getDefaultToAddress() {
        return message.getFrom();
    }

    @Override
    protected void handleResponse(HttpResponse response) throws Exception {

        for (OutgoingMessage reply : parseResponseXML(response)) {
            app.sendOutgoingMessage(reply);
        }

        app.setIncomingMessageStatus(message, true);
    }

    @Override
    protected void handleFailure() {
        app.setIncomingMessageStatus(message, false);
    }
}
