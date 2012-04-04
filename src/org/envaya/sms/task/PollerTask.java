
package org.envaya.sms.task;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.App;

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
    protected void handleUnknownContentType(String contentType)
            throws Exception
    {
        throw new Exception("Invalid response type " + contentType);
    }
}