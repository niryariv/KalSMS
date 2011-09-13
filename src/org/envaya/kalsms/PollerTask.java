
package org.envaya.kalsms;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;

public class PollerTask extends HttpTask {

    public PollerTask(App app) {
        super(app, new BasicNameValuePair("action", App.ACTION_OUTGOING));
    }

    @Override
    protected void handleResponse(HttpResponse response) throws Exception {
        for (OutgoingMessage reply : parseResponseXML(response)) {
            app.sendOutgoingMessage(reply);
        }
    }
}