
package org.envaya.sms.task;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.App;
import org.envaya.sms.OutgoingMessage;

public class PollerTask extends HttpTask {

    public PollerTask(App app) {
        super(app, new BasicNameValuePair("action", App.ACTION_OUTGOING));
    }

    @Override
    protected void onPostExecute(HttpResponse response) {
        super.onPostExecute(response);
        app.markPollComplete();
    }
    
    @Override
    protected void handleResponse(HttpResponse response) throws Exception {
        for (OutgoingMessage reply : parseResponseXML(response)) {
            app.outbox.sendMessage(reply);
        }
    }
}