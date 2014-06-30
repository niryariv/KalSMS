package kalsms.niryariv.itp;
//package txtgate.niryariv.itp;
//
//import java.io.BufferedInputStream;
//import java.io.InputStream;
//import java.net.URL;
//import java.net.URLConnection;
//
//import org.apache.http.util.ByteArrayBuffer;
//
//public class URLopen {
//	private Thread checkUpdate = new Thread() {
//	    public void run() {
//	    	try {
//	    	    URL updateURL = new URL("http://iconic.4feets.com/update");
//	    	    URLConnection conn = updateURL.openConnection();
//	    	    InputStream is = conn.getInputStream();
//	    	    BufferedInputStream bis = new BufferedInputStream(is);
//	    	    ByteArrayBuffer baf = new ByteArrayBuffer(50);
//
//	    	    int current = 0;
//	    	    while((current = bis.read()) != -1){
//	    	        baf.append((byte)current);
//	    	    }
//
//	    	    /* Convert the Bytes read to a String. */
//	    	    final String s = new String(baf.toByteArray());
////	    	    mHandler.post(showUpdate);
//	    	} catch (Exception e) {
//	    		//
//	    	}
//	    }
//	};
//}
//
////public class Iconic extends Activity {
////    private String html = "";
////	private Handler mHandler;
////
////    public void onCreate(Bundle savedInstanceState) {
////        super.onCreate(savedInstanceState);
////        setContentView(R.layout.main);
////    	mHandler = new Handler();
////    	checkUpdate.start();
////    }
////
////    private Thread checkUpdate = new Thread() {
////        public void run() {
////            try {
////                URL updateURL = new URL("http://iconic.4feets.com/update");
////                URLConnection conn = updateURL.openConnection();
////                InputStream is = conn.getInputStream();
////                BufferedInputStream bis = new BufferedInputStream(is);
////                ByteArrayBuffer baf = new ByteArrayBuffer(50);
////
////                int current = 0;
////                while((current = bis.read()) != -1){
////                    baf.append((byte)current);
////                }
////
////                /* Convert the Bytes read to a String. */
////                html = new String(baf.toByteArray());
////                mHandler.post(showUpdate);
////            } catch (Exception e) {
////            }
////        }
////    };
////
////    private Runnable showUpdate = new Runnable(){
////       	public void run(){
////    	    Toast.makeText(Iconic.this, "HTML Code: " + html, Toast.LENGTH_SHORT).show();
////        }
////    };
////}