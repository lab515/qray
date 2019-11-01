package io.github.lab515.qray.utils;

import io.github.lab515.qray.conf.Config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * utils for sfapi framework
 * 
 * @author mpeng Success Factors
 * 
 */
public class FileUtils {
	/**
	 * list all the files (recursive)
	 * @param path path
	 * @param fileExt file extension
	 * @return file list
	 * @throws Exception
	 */
	public static List<String> listAllFiles(String path, String fileExt) throws Exception{
		return listAllFiles(path, null, fileExt, true);
	}
	/**
	 * list all the files (recursive)
	 * @param path path
	 * @param result result
	 * @param fileExt file extension
	 * @return append all to result
	 * @throws Exception Exception
	 */
	public static List<String> listAllFiles(String path, List<String> result, String fileExt) throws Exception{
		return listAllFiles(path, result, fileExt, true);
	}
	
	/**
	 * list all the files
	 * @param path path
	 * @param fileExt file extension
	 * @param subFolders include sub folders or not
	 * @return file list
	 * @throws Exception exception
	 */
	public static List<String> listAllFiles(String path, String fileExt, boolean subFolders) throws Exception{
		return listAllFiles(path, null, fileExt, 0, subFolders ? -1 : 1);
	}
	
	/**
	 * list all the files
	 * @param path path
	 * @param result result
	 * @param fileExt file extension
	 * @param subFolders include sub folders or not
	 * @return append all to result
	 * @throws Exception exception
	 */
	public static List<String> listAllFiles(String path, List<String> result, String fileExt, boolean subFolders) throws Exception{
		return listAllFiles(path, result, fileExt, 0, subFolders ? -1 : 1);
	}
	
	/**
	 * list all the files
	 * @param path path
	 * @param result result
	 * @param fileExt file extension
	 * @param folderDepth folder depth
	 * @param maxDepth -1 means all
	 * @return append all to result
	 * @throws Exception
	 */
	private static List<String> listAllFiles(String path, List<String> result, String fileExt, int folderDepth, int maxDepth) throws Exception{
		if(result == null)result = new ArrayList<String>();
		File f = new File(path);
		if(f.isFile()){
			if(f.getName().endsWith(fileExt)){
				result.add(f.getAbsolutePath());
			}
			
		}else if(f.isDirectory() && (maxDepth < 0 || folderDepth < maxDepth)){
			path = f.getCanonicalPath();
			if(!(path.endsWith("/") || path.endsWith("\\")))path += "/";
			for(String s : f.list()){
				listAllFiles(path + s,result, fileExt,folderDepth+1,maxDepth);
			}
		}
		return result;
	}
    /**
     * read a file from web
     * 
     * @param Url
     * @return
     */
    public static byte[] getWebFile(String Url) {
        InputStream is = null;
        ByteArrayOutputStream bos = null;
        HttpURLConnection conn = null;
        byte[] ret = null;
        try {
            URL url = new URL(Url);
            conn = (HttpURLConnection) url.openConnection();
            is = conn.getInputStream();
            byte[] bts = new byte[1024];
            int t = 0;
            bos = new ByteArrayOutputStream();
            while ((t = is.read(bts, 0, 1024)) > 0)
                bos.write(bts, 0, t);

            if (bos.size() > 0)
                ret = bos.toByteArray();
        } catch (Exception e) {
            ret = null;
        } finally {
            try {
                bos.close();
                is.close();
            } catch (Exception e) {
            }
            conn.disconnect();
        }
        return ret;
    }
    public static void deleteFile(String filePath){
        try{
            new File(filePath).delete();
        }catch(Exception e){}
    }
    /**
     * Read a file from local
     * 
     * @param filePath
     * @param enc
     * @return
     */
    public static String readAllText(String filePath, String enc) {
        if (enc == null)
            enc = "utf-8";
        InputStream is = getFileStream(filePath);
        if (is == null)
            return null;
        BufferedReader ins = null;
        try {
            ins = new BufferedReader(new InputStreamReader(is, enc));
            char[] chars = new char[4096];
            int len = 0;
            StringBuilder ret = new StringBuilder();
            while ((len = ins.read(chars)) > 0) {
                if (chars[0] == (char) 65279 || chars[0] == (char) 65534)
                    ret.append(chars, 1, len - 1);
                else
                    ret.append(chars, 0, len);
            }

            if (ret.length() > 0)
                return ret.toString();
            else
                return null;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (ins != null) {
                    ins.close();
                }
                is.close();
            } catch (Exception e) {

            }
        }

    }

    /**
     * get file stream from local folder
     * 
     * @param fileName
     * @return
     */
    public static InputStream getFileStream(String fileName) {
    	InputStream is = null;
    	// for online 
    	/*if(Profiles.USE_ONLINE_CONFIG){
	    	 * 
    		try{
    		    File f = new File(fileName + ".cache");
	            if(f.exists()){
	                if((new Date().getTime() - 
	                f.lastModified()) < 3600 * 1000){
	                    is = new FileInputStream(f);
	                }
	            }
	            if(is == null){
                	ActionData ad = new ActionData(fileName);
                	ad = SimWebClient.InvokeAction(Profiles.SIMWEB_URL, "dbquery2", ad, "loadconfigdesc");
                	if(ad.err == null && ad.strs != null){
                    	is = new ByteArrayInputStream(ad.strs[0].getBytes("utf-8"));
                    	FileOutputStream fos = null;
		                try{
		                    fos = new FileOutputStream(fileName + ".cache");
		                    defProfiles.save(fos, "Saved from cache " + new Date());
		                }catch(Exception e){
		                    if(fos != null){
		                        try{
		                        fos.close();
		                        }catch(Exception e2){}
		                    }
		                }
                    }
                }
            }catch(Exception e){
                is = null;
            }
    	}
    	
        if(is == null){
	        try {
	            is = FileUtils.class.getClassLoader().getResourceAsStream(fileName);
	        } catch (Exception e) {
	            is = null;
	        }
        }
        if(is != null)return is;*/  // Change: classloader resource lower priority

        
        // try to load from current dir/config/
        File file = new File(".");
        String basePath = "";
        try {
            basePath = file.getCanonicalPath();
        } catch (Exception e) {

        }
        file = new File(fileName);
        if (is == null && file.exists()) {
            try {
                is = new FileInputStream(file);
                Config.getRemoteHandler().log("loading file from " + file.getAbsolutePath());
            } catch (Exception e) {
                is = null;
            }
        }
        file = new File(basePath + File.separator + "config" + File.separator
                + fileName);
        if (is == null && file.exists()) {
            try {
                is = new FileInputStream(file);
                Config.getRemoteHandler().log("loading file from " + file.getAbsolutePath());
            } catch (Exception e) {
                is = null;
            }
        }
        file = new File(basePath + File.separator + "resources" + File.separator
                + fileName);
        if (is == null && file.exists()) {
            try {
                is = new FileInputStream(file);
                Config.getRemoteHandler().log("loading file from " + file.getAbsolutePath());
            } catch (Exception e) {
                is = null;
            }
        }
        if(is == null){
            try {
                is = FileUtils.class.getClassLoader().getResourceAsStream(fileName);
                if(is != null)Config.getRemoteHandler().log("loading file from classloader resource: " + FileUtils.class.getClassLoader().getResource(fileName));
            } catch (Exception e) {
                is = null;
            }
        }
        return is;
    }

    /**
     * Read all file content into lines
     * 
     * @param filePath
     * @param enc
     * @return
     */
    public static List<String> readAllLines(String filePath, String enc) {
        if (enc == null)
            enc = "utf-8";
        try {
            BufferedReader ins = new BufferedReader(new InputStreamReader(
                    new FileInputStream(filePath), enc));
            List<String> ret = new ArrayList<String>();
            String line = null;
            while ((line = ins.readLine()) != null) {
                if (line.length() > 0 && line.codePointAt(0) == 65279
                        || line.codePointAt(0) == 65534)
                    line = line.substring(1);
                if (line.length() > 0)
                    ret.add(line);
            }
            ins.close();
            if (ret.size() > 0)
                return ret;
            else
                return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    public static boolean writeAllBytes(String filePath, byte[] data){
        if(filePath == null || data == null){
            return false;
        }
        try{
            FileOutputStream file = new FileOutputStream(filePath);
            file.write(data);
            file.flush();
            file.close();
            return true;
        }catch(IOException e){
            return false;
        }
    }
    
    public static String getStackTrace(Throwable t){
        if(t == null)return "";
        try{
	        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
	        PrintStream ps = new PrintStream(baos);
	        t.printStackTrace(ps);
	        String stackTrace = baos.toString("utf-8");
	        ps.close();
	        baos.close();
	        return stackTrace;
        }
        catch(Exception e){
            return "error getting stacktrace";
        }
    }
}
