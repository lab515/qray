package io.github.lab515.qray.simweb;

import io.github.lab515.qray.conf.Config;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SimWebClient
 *
 * @author mpeng
 *
 */
public class SimWebClient {
    private static Pattern sysNonProxyHosts = null;
    private static Proxy sysProxy = null;
    private static ThreadLocal<Integer> readTimeout = new ThreadLocal<Integer>();
    private static final TrustManager[] bypassTMs = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
    };
    public static boolean hasDefaultProxy(){
        return sysProxy != null;
    }
    public static void setDefaultProxy(String proxyHost, String proxyPort, String nonProxy) {
        if(proxyHost == null || proxyHost.length() < 1)return;
        int port = 8080;
        if(proxyPort != null){
            port = Integer.parseInt(proxyPort);
        }
        sysProxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress(proxyHost, port));
        if(nonProxy != null){
            String nonProxyHosts = nonProxy.replaceAll("\\.", "\\\\.")
                        .replaceAll("\\*", ".*?");

            nonProxyHosts = "(" + nonProxyHosts.replaceAll("\\|", ")|(") + ")";
            sysNonProxyHosts = Pattern.compile(nonProxyHosts);
        }
    }

    public static String postHttpData(String url, String postData) throws Exception{
        return postHttpData(url, postData,false, null,null,null);
    }

    private static Proxy getRequestProxy(String hostName, String proxyHost, String proxyPort, String nonProxyHosts) throws IOException{
        if(proxyHost == null || proxyHost.length() < 1){
            // we check default
            if(sysProxy != null){
                if(sysNonProxyHosts != null){
                    Matcher m = sysNonProxyHosts.matcher(hostName);
                    if(m != null && m.matches())return Proxy.NO_PROXY;
                }
            }
            return sysProxy;
        }
        int port = 8080;
        if(proxyPort != null){
            port = Integer.parseInt(proxyPort);
        }
        if(nonProxyHosts != null){
            nonProxyHosts = nonProxyHosts.replaceAll("\\.", "\\\\.")
                        .replaceAll("\\*", ".*?");

            nonProxyHosts = "(" + nonProxyHosts.replaceAll("\\|", ")|(") + ")";
            Pattern p = Pattern.compile(nonProxyHosts);
            Matcher m = p.matcher(hostName);
            if(m != null && m.matches())return Proxy.NO_PROXY;
        }
        return new Proxy(Proxy.Type.HTTP,new InetSocketAddress(proxyHost, port));
    }

    public static String postHttpData(String url, String postData, boolean fastMode) throws IOException{
        return postHttpData(url, postData,fastMode, null,null,null);
    }
    
    public static void setReadTimeoutMs(int ms){
        Integer t = ms == 0 ? null : ms;
        readTimeout.set(t);
    }

    /**
     * send post http request
     * @param url URL
     * @param postData post data
     * @return http response
     * @throws IOException Exception
     */
    public static String postHttpData(String url, String postData,boolean fastMode, String proxyHost, String proxyPort, String nonProxyHosts) throws IOException{
        if(url == null)throw new IOException("invalid parameters");
        URL uri = new URL(url);
        Proxy prx = getRequestProxy(uri.getHost(),proxyHost, proxyPort, nonProxyHosts);
        HttpURLConnection conn = null;
        if(prx != null){
            conn = (HttpURLConnection) uri.openConnection(prx);
        }else
            conn = (HttpURLConnection) uri.openConnection();
        if(conn instanceof HttpsURLConnection){
            HttpsURLConnection co = (HttpsURLConnection)conn;
            try{
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, bypassTMs, new SecureRandom());
                co.setSSLSocketFactory(sslContext.getSocketFactory());
            }catch(Exception e){

            }
        }
        java.io.ByteArrayOutputStream bos = null;
        java.io.OutputStream os = null;
        java.io.InputStream is = null;
        try{
            if(fastMode){
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(5000);
            }else{
                conn.setConnectTimeout(5000);
                int tm = 300000;
                Integer tt = readTimeout.get();
                if(tt != null && tt != 0){
                    tm = tt;
                    if(tm < 0){
                        tm = -tm;
                        readTimeout.set(null);
                    }
                }
                conn.setReadTimeout(tm);
            }
            if(postData != null){
                conn.setRequestMethod("POST");
                conn.addRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                os = conn.getOutputStream();
                os.write(postData.getBytes("utf-8"));
                os.close();
                os = null;
            }
            if (conn.getResponseCode() != 200) {
                //Config.getRemoteHandler().log("http call failed due to http " + conn.getResponseCode());
                // make sure print out the request
                //Config.getRemoteHandler().log("http url: " + url);
                throw new IOException("http post error code: " + conn.getResponseCode());
            }
            is = conn.getInputStream();
            byte[] bts = new byte[1024];
            int t = 0;
            bos = new java.io.ByteArrayOutputStream();
            while ((t = is.read(bts, 0, 1024)) > 0)
                bos.write(bts, 0, t);
            String response = null;
            if (bos.size() > 0)
                response = new String(bos.toByteArray(), "utf-8");
            bos.close();
            bos = null;
            is.close();
            is = null;
            conn.disconnect();
            conn = null;
            return response;
        }finally{
            if(os != null){
                try{
                    os.close();
                }catch(Exception e){

                }

            }
            if(bos != null){
                try{
                    bos.close();
                }catch(Exception e){

                }
            }
            if(is != null){
                try{
                    is.close();
                }catch(Exception e){

                }

            }
            if(conn != null){
                try{
                    conn.disconnect();
                }catch(Exception e){

                }

            }
        }
    }

    /**
     * Invoke a SimWeb Action
     * @param url URL
     * @param action action
     * @param callData class data
     * @param exParas extend parameters
     * @return unpack response to ActionData object
     * @throws IOException IOException
     */
    public static ActionData InvokeAction(String url, String action,
            ActionData callData, String exParas) throws IOException {
        return InvokeAction(url,action,callData,exParas,null);
    }


    /**
     * Invoke a SimWeb Action
     * @param url URL
     * @param action action
     * @param callData class data
     * @param exParas extend parameters
     * @param exUrlParams extend URL parameters
     * @return unpack response to ActionData object
     * @throws IOException Exception
     */
    public static ActionData InvokeAction(String url, String action,
            ActionData callData, String exParas, String exUrlParams) throws IOException {
        if (callData == null || action == null || url == null) {
            return null;
        }
        if (exParas == null) {
            exParas = "";
        }
        if(exUrlParams == null){
            exUrlParams = "";
        }else
            exUrlParams = "&" + exUrlParams;
        long tt = new Date().getTime();
        String postData = "r=false&a=" + URLEncoder.encode(action,"utf-8") + "&i="
                + URLEncoder.encode(callData.pack(), "utf-8") + "&p="
                + URLEncoder.encode(exParas, "utf-8") + exUrlParams;

        String response = postHttpData(url, postData,false);
        ActionData ret = ActionData.unpack(response);
        if(ret == null || ret.err != null){
            Config.getRemoteHandler().log("error from sw: " + (ret == null ? "system error" : ret.err));
        }
        //Config.getRemoteHandler().log("simweb call for action: "+action+" took " + (new Date().getTime() - tt) + " ms");
        return ret;
    }
}