package io.github.lab515.qray.runtime;
import io.github.lab515.qray.conf.Config;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread;
/**
 * solve the unexpected 127.0.0.1:4446 issue
 * @author mpeng
 *
 */
public class SocketProxy {
    private static class ProxyWriter extends Thread{
        InputStream ins ;
        OutputStream outs;
        int port = 0;
        private boolean stopping = false;
        public ProxyWriter(InputStream i, OutputStream o, int p){
            ins = i;
            outs = o;
            port =p;
        }
        @Override
        public void run(){
            // keep check the buffer for all
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            
            try{
                byte[] buf = new byte[4096];
                int size = 0;
                while(!stopping){
                    size = ins.read(buf,0,4096);
                    if(size > 0){
                        baos.write(buf,0,size);
                        outs.write(buf,0,size);
                    }else{
                        Thread.sleep(1000);
                    }
                }
            }catch(Exception e){
                if(!stopping)
                e.printStackTrace();
            }
            try{
                ins.close();
            }catch(Exception e){
                e.printStackTrace();
            }
            try{
                outs.close();
            }catch(Exception e){
                e.printStackTrace();
            }
            try{
            java.io.FileOutputStream fos = new java.io.FileOutputStream(port + ".dat"); 
            fos.write(baos.toByteArray());
            fos.close();
            baos.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        
        public void stopMe(){
            stopping = true;
        }
    }
    // majorrity ports: 1098, 4444,4445,4446
    private static class ProxyRunner extends Thread{
        private int port = 0;
        private String ipAddr = null;
        private boolean stopping = false;
        private ArrayList<ProxyWriter> ths = null;
        private ArrayList<Socket> sks = null;
        private ServerSocket sock = null;
        private String bindIp = null;
        private String routeIp = null;
        public ProxyRunner(String ip, int port){
            this(ip,port,null,null);
        }
        public ProxyRunner(String ip, int port,String bip, String rIp){
            this.port = port;
            ipAddr = ip;
            bindIp = bip;
            if(bindIp == null)bindIp = "127.0.0.1";
            routeIp = rIp;
            //if(routeIp == null)routeIp = bindIp;
        }
        private boolean isUp = false;
        public boolean isServerUp(){return isUp;};
        @Override
        public void run(){
            // create the server socket and listening
            try{
                Config.getRemoteHandler().log("Listening on port " + port + " for " + bindIp);
                sock = new ServerSocket(port,10,InetAddress.getByName(bindIp));
                ths = new ArrayList<ProxyWriter>();
                sks = new ArrayList<Socket>();
                Socket client = null;
                Socket remote = null;
                ProxyWriter pw1 = null;
                ProxyWriter pw2 = null;
                isUp =true;
                while(!stopping){
                    client = sock.accept();
                    if(client == null){
                        break;
                    }
                    try{
                        pw1 = pw2 = null;
                        remote = new Socket();
                        if(routeIp != null)
                            remote.bind(new InetSocketAddress(InetAddress.getByName(routeIp),0));
                        remote.connect(new InetSocketAddress(ipAddr,port));
                        sks.add(client);
                        sks.add(remote);
                        pw1 = new ProxyWriter(client.getInputStream(),remote.getOutputStream(),port);
                        
                        pw1.start();
                        pw2 = new ProxyWriter(remote.getInputStream(),client.getOutputStream(),10 * port);
                        pw2.start();
                        ths.add(pw1);
                        ths.add(pw2);
                    }catch(Exception e){
                        Config.getRemoteHandler().log("error while process request:" + ipAddr + ":" + port);
                        e.printStackTrace();
                        try{
                            client.close();
                            if(remote != null)remote.close();
                            if(pw1 != null)pw1.stopMe();
                            if(pw2 != null)pw2.stopMe();
                        }catch(Exception e2){
                            e2.printStackTrace();
                        }
                        sks.remove(client);
                        if(pw1!=null)ths.remove(pw1);
                        if(pw2!=null)ths.remove(pw2);
                        Config.getRemoteHandler().log("removed current thread");
                    }
                    
                }
            }catch(Exception e){
                if(!stopping){
                    e.printStackTrace();
                    stopMe();
                }
            }
            
        }
        public void stopMe(){
            for(ProxyWriter t : ths){
                t.stopMe();
            }
            stopping = true;
            try{
            sock.close();
            }catch(Exception e){e.printStackTrace();}
            // close all socket
            for(Socket s : sks){
                try{
                    s.close();
                    }catch(Exception e){e.printStackTrace();}
            }
            
            
            for(ProxyWriter t : ths){
                try{
                t.join();
                }catch(Exception e){e.printStackTrace();}
            }
        }
    }

    private ProxyRunner[] runners = null;
    public SocketProxy(String ip, int[] ports, String localIP, String routeIp){
        runners = new ProxyRunner[ports.length];
        for(int i = 0; i < ports.length;i++){
            runners[i] = new ProxyRunner(ip,ports[i],localIP,routeIp);
            runners[i].start();
        }
    }
    public void stop(){
        for(ProxyRunner r : runners){
            r.stopMe();
        }
    }
    
    public boolean isAllUp(){
        boolean isUp = true;
        for(ProxyRunner r : runners){
            if(!r.isServerUp()){
                isUp = false;
                break;
            }
        }
        return isUp;
    }
}
