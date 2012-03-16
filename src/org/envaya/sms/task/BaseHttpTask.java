package org.envaya.sms.task;

import android.os.AsyncTask;
import android.os.Build;
import org.envaya.sms.App;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;

public class BaseHttpTask extends AsyncTask<String, Void, HttpResponse> {
       
    protected App app;
    protected String url;    
    protected List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();    

    private List<FormBodyPart> formParts;
    protected boolean useMultipartPost = false;    
    protected HttpPost post;
    protected Throwable requestException;
    
    public BaseHttpTask(App app, String url, BasicNameValuePair... paramsArr)
    {
        this.url = url;
        this.app = app;                
        params = new ArrayList<BasicNameValuePair>(Arrays.asList(paramsArr));
    }
    
    public void addParam(String name, String value)
    {
        params.add(new BasicNameValuePair(name, value));
    }    
    
    public void setFormParts(List<FormBodyPart> formParts)
    {
        useMultipartPost = true;
        this.formParts = formParts;
    }                     

    protected HttpPost makeHttpPost() throws Exception
    {
        HttpPost httpPost = new HttpPost(url);
                
        httpPost.setHeader("User-Agent", "EnvayaSMS/" + app.getPackageInfo().versionName + " (Android; SDK "+Build.VERSION.SDK_INT + "; " + Build.MANUFACTURER + "; " + Build.MODEL+")");

        if (useMultipartPost)
        {
            MultipartEntity entity = new MultipartEntity();//HttpMultipartMode.BROWSER_COMPATIBLE);

            Charset charset = Charset.forName("UTF-8");

            for (BasicNameValuePair param : params)
            {
                entity.addPart(param.getName(), new StringBody(param.getValue(), charset));
            }

            for (FormBodyPart formPart : formParts)
            {
                entity.addPart(formPart);
            }
            httpPost.setEntity(entity);                                                
        }
        else
        {
            httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
        }        
        
        return httpPost;
    }
    
    protected HttpResponse doInBackground(String... ignored) 
    {    
        try
        {
            post = makeHttpPost();
            HttpClient client = app.getHttpClient();
            return client.execute(post);            
        }     
        catch (Throwable ex) 
        {
            requestException = ex;
            
            try
            {
                String message = ex.getMessage();
                // workaround for https://issues.apache.org/jira/browse/HTTPCLIENT-881
                if ((ex instanceof IOException) 
                        && message != null && message.equals("Connection already shutdown"))
                {
                    //app.log("Retrying request");
                    post = makeHttpPost();
                    HttpClient client = app.getHttpClient();
                    return client.execute(post);  
                }
            }
            catch (Throwable ex2)
            {
                requestException = ex2;
            }            
        }   
        
        return null;
    }    
    
    public boolean isValidContentType(String contentType)
    {
        return true; // contentType.startsWith("text/xml");
    }
    
    @Override
    protected void onPostExecute(HttpResponse response) {
        if (response != null)
        {                
            try
            {
                int statusCode = response.getStatusLine().getStatusCode();
                Header contentTypeHeader = response.getFirstHeader("Content-Type");
                String contentType = (contentTypeHeader != null) ? contentTypeHeader.getValue() : "";

                boolean validContentType = isValidContentType(contentType);

                if (statusCode == 200) 
                {
                    if (validContentType)
                    {
                        handleResponse(response);
                    }
                    else
                    {
                        throw new Exception("Invalid response type " + contentType);
                    }
                } 
                else if (statusCode >= 400 && statusCode <= 499 && validContentType)
                {
                    handleErrorResponse(response);
                    handleFailure();
                }
                else
                {
                    throw new Exception("HTTP " + statusCode);
                }
            }
            catch (Throwable ex)
            {
                post.abort();
                handleResponseException(ex);
                handleFailure();
            }
            
            try
            {
                response.getEntity().consumeContent();
            }
            catch (IOException ex)
            {
            }
        }
        else
        {
            handleRequestException(requestException);
            handleFailure();
        }
    }
    
    protected void handleResponse(HttpResponse response) throws Exception
    {
        // if we get a valid server response after a connectivity error, then forward any pending messages
        if (app.hasConnectivityError())
        {
            app.onConnectivityRestored();
        }
    }
    
    protected void handleErrorResponse(HttpResponse response) throws Exception
    {
    }        
    
    protected void handleFailure()
    {
    }            

    protected void handleRequestException(Throwable ex)
    {       
    }

    protected void handleResponseException(Throwable ex)
    {       
    }    
        
}