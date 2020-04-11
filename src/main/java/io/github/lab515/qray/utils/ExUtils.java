package io.github.lab515.qray.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;


/**
 * QAUtils.class for groovy embedding
 * 
 * @author mpeng Success Factors
 * 
 */
public class ExUtils {
    /**
     * default write size
     */
    private static final int BUFFER_SIZE = 256;
    /**
     * web buffer size
     */
    private static final int WEB_BUFFER_SIZE = 4096;
    /**
     * private consturctor
     */
    private ExUtils() {

    }

    /**
     * Simple format of data packing
     * 
     * @param value vlaue of string
     * @return string array
     */
    public static String[] unpackData(String value) {
        if (value == null || value.length() < 1){
            return null;
        }
            
        int len = value.length();
        if (len < 2){
            return null;
        }
        int pos = 0;
        List<String> ret = new ArrayList<String>();
        int segLen = 0;
        int startPos = 0;
        while (pos < len - 1) {
            // try to get the next index
            segLen = value.indexOf(":", pos);
            if (segLen <= pos){
                break;
            }
            startPos = segLen + 1;
            segLen = Integer.parseInt(value.substring(pos, segLen));
            if (segLen >= 0 && startPos + segLen <= len) {
                ret.add(value.substring(startPos, startPos + segLen));
                pos = startPos + segLen;
            } else if (segLen == -1) {
                ret.add(null);
                pos = startPos;
            } else{
                break;
            }
        }

        if (pos != len || ret.size() < 1){
            return null;
        }
        String[] rets = new String[ret.size()];
        ret.toArray(rets);
        ret.clear();
        return rets;
    }

    /**
     * Simple format of data packing
     * 
     * @param arr array of string
     * @return string
     */
    public static String packData(String[] arr) {
        return packData(arr,-1);
    }
    
    /**
     * Simple format of data packing
     * 
     * @param arr array of string
     * @param max the max items to pack
     * @return string
     */
    public static String packData(String[] arr, int max) {
        if (arr == null || arr.length < 1){
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length && (max < 1 || i < max); i++) {
            if (arr[i] == null){
                sb.append("-1:");
            }else {
                sb.append(arr[i].length());
                sb.append(":");
                sb.append(arr[i]);
            }
        }
        String s = sb.toString();
        sb.setLength(0);
        return s;
    }

    /**
     * save properties as a string
     * 
     * @param props rops
     * @return string
     */
    public static String packProperties(Properties props) {
        String ret = null;
        java.io.StringWriter sw = new java.io.StringWriter(BUFFER_SIZE);
        try {
            props.store(sw, null);
            ret = sw.toString();
            sw.close();
            return ret;
        } catch (IOException e) {
            return null;
        }
    }
    /**
     * Get a web file
     * 
     * @param strUrl the url para
     * @return byte[] array
     * @throws IOException error
     */
    public static byte[] getWebFile(String strUrl) throws IOException {
        URL url = new URL(strUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url
                .openConnection();
        java.io.InputStream is = conn.getInputStream();
        byte[] bts = new byte[WEB_BUFFER_SIZE];
        int t = 0;
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        while ((t = is.read(bts, 0, WEB_BUFFER_SIZE)) > 0){
            bos.write(bts, 0, t);
        }
        byte[] ret = null;
        if (bos.size() > 0){
            ret = bos.toByteArray();
        }
        bos.close();
        is.close();
        conn.disconnect();
        return ret;
    }
    /**
     * reload properties from a string
     * 
     * @param propsString unpacl props
     * @return properties
     */
    public static Properties unpackProperties(String propsString) {
        if (propsString == null){
            return null;
        }
        Properties ret = new Properties();
        try {
            ret.load(new java.io.StringReader(propsString));
        } catch (IOException e) {
            e = null;
        }
        return ret;
    }
    /**
     * get a hash of a string
     * @param data the data to compute md5 hash
     * @return string as the hashvalue in 32 bit
     */
    public static String getHashOfString(String data){
       if(data == null || data.length() < 1){
           return null;
       }
       try{
           return getHashOfString(data.getBytes("utf-8"));
       }catch (UnsupportedEncodingException e) {
           return null;
       }
    }

    /**
     * get a hash of a string
     * @param data the data of byte array to compute md5 hash
     * @return string as the hashvalue in 32 bit
     */
    public static String getHashOfString(byte[] data){
       if(data == null || data.length < 1){
           return null;
       }
       try{
           MessageDigest m = MessageDigest.getInstance("MD5");
           m.update(data);
           String retVal = new BigInteger(1,m.digest()).toString(16);
                    
           if(retVal.length() < 32){
               retVal = "000000".substring(0,32 - retVal.length()) 
                       + retVal;
           }
           return retVal;
       }catch(NoSuchAlgorithmException e){
           return null;
       } 
    }
    
    /**
     * chec if a given lib name is valid not not
     * @param name name of the grooby 
     * @return boolean flag to indicat the valid result
     */
    public static String isValidLibName(String name){
        if(name == null || name.length() < 1){
            return null;
        }
        String [] segs  = name.split("\\.");
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < segs.length;i++){
            boolean validSeg = false;
            for(char s : segs[i].trim().toCharArray()){
                if(s == '_' || 
                        (validSeg && Character.isLetterOrDigit(s)) ||
                        (!validSeg && Character.isLetter(s))){
                    validSeg = true;
                }else{
                    validSeg = false;
                    break;
                }
            }
            if(validSeg){
                if(i > 0){
                    sb.append('.');
                }
                sb.append(segs[i].trim());
            }else{
                return null;
            }
        }
        return sb.toString();
    }
    
    /**
     * the default size of hte low byte
     */
    private static final int INT_127 = 127;
    /**
     * the default size of package 
     */
    private static final int INT_6 = 6;
    /**
     * default size of a unit
     */
    private static final int INT_3 = 3;
    /**
     * default size of a 2 bytes
     */
    private static final int INT_2 = 2;
    /**
     * default 4 bytes
     */
    private static final int INT_4 = 4;
    /**
     * int 15 0xf
     */
    private static final int INT_15 = 0xf;
    /**
     * low byte max size
     */
    private static final int INT_128 = 128;
    /**
     * zero
     */
    private static final int INT_0 = 0;
    /**
     * 255
     */
    private static final int INT_255 = 0xff;
    /**
     * max bse64 bits
     */
    private static final int INT_64 = 64;
    /**
     * 63
     */
    private static final int INT_63 = 63;
    /**
     * Mapping table from 6-bit nibbles to Base64 characters.
     */
    private static char[] map1 = new char[INT_64];
    /**
     *  Mapping table from Base64 characters to 6-bit nibbles.
     */
    private static byte[] map2 = new byte[INT_128];
    /**
     * static block
     */
    static {
      int i = INT_0;
      for (char c = 'A'; c <= 'Z'; c++) {
        map1[i++] = c;
      }
      for (char c = 'a'; c <= 'z'; c++) {
        map1[i++] = c;
      }
      for (char c = '0'; c <= '9'; c++) {
        map1[i++] = c;
      }
      map1[i++] = '+';
      map1[i++] = '/';
      for (i = INT_0; i < map2.length; i++) {
          map2[i] = -1;
      }
      for (i = INT_0; i < INT_64; i++) {
          map2[map1[i]] = (byte) i;
      }
    }

    

    /**
     * Encodes a string into Base64 format. No blanks or line breaks are inserted.
     *
     * @param s
     *     a String to be encoded.
     * @return A String with the Base64 encoded data.
     */
    public static String encodeBase64String(String s) {
      return new String(encodeBase64(s.getBytes()));
    }

    /**
     * Encodes a byte array into Base64 format. No blanks or line breaks are inserted.
     *
     * @param in
     *     an array containing the data bytes to be encoded.
     * @return A character array with the Base64 encoded data.
     */
    public static char[] encodeBase64(byte[] in) {
      return encodeBase64(in, in.length);
    }

    /**
     * Encodes a byte array into Base64 format. No blanks or line breaks are inserted.
     *
     * @param in
     *     an array containing the data bytes to be encoded.
     * @param iLen
     *     number of bytes to process in <code>in</code>.
     * @return A character array with the Base64 encoded data.
     */
    public static char[] encodeBase64(byte[] in, int iLen) {
      int oDataLen = (iLen * INT_4 + INT_2) / INT_3; // output length without padding
      int oLen = ((iLen + INT_2) / INT_3) * INT_4; // output length including padding
      char[] out = new char[oLen];
      int ip = INT_0;
      int op = INT_0;
      while (ip < iLen) {
        int i0 = in[ip++] & INT_255;
        int i1 = ip < iLen ? in[ip++] & INT_255 : INT_0;
        int i2 = ip < iLen ? in[ip++] & INT_255 : INT_0;
        int o0 = i0 >>> INT_2;
        int o1 = ((i0 & INT_3) << INT_4) | (i1 >>> INT_4);
        int o2 = ((i1 & INT_15) << INT_2) | (i2 >>> INT_6);
        int o3 = i2 & INT_63;
        out[op++] = map1[o0];
        out[op++] = map1[o1];
        out[op] = op < oDataLen ? map1[o2] : '=';
        op++;
        out[op] = op < oDataLen ? map1[o3] : '=';
        op++;
      }
      return out;
    }

    /**
     * Decodes a string from Base64 format.
     *
     * @param s
     *     a Base64 String to be decoded.
     * @return A String containing the decoded data.
     */
    public static String decodeBase64String(String s) {
      return new String(decodeBase64(s));
    }

    /**
     * Decodes a byte array from Base64 format.
     *
     * @param s
     *     a Base64 String to be decoded.
     * @return An array containing the decoded data bytes.
     */
    public static byte[] decodeBase64(String s) {
      return decodeBase64(s.toCharArray());
    }

    /**
     * Decodes a byte array from Base64 format. No blanks or line breaks are allowed within the Base64 encoded data.
     *
     * @param in
     *     a character array containing the Base64 encoded data.
     * @return An array containing the decoded data bytes.
     */
    public static byte[] decodeBase64(char[] in) {
      int iLen = in.length;
      if (iLen % INT_4 != INT_0) {
        throw new IllegalArgumentException("Length of Base64 encoded input string is not a multiple of 4.");
      }
      while (iLen > INT_0 && in[iLen - 1] == '=') {
        iLen--;
      }
      int oLen = (iLen * INT_3) / INT_4;
      byte[] out = new byte[oLen];
      int ip = INT_0;
      int op = INT_0;
      while (ip < iLen) {
        int i0 = in[ip++];
        int i1 = in[ip++];
        int i2 = ip < iLen ? in[ip++] : 'A';
        int i3 = ip < iLen ? in[ip++] : 'A';
        if (i0 > INT_127 || i1 > INT_127 || i2 > INT_127 || i3 > INT_127) {
          throw new IllegalArgumentException("Illegal character in Base64 encoded data.");
        }
        int b0 = map2[i0];
        int b1 = map2[i1];
        int b2 = map2[i2];
        int b3 = map2[i3];
        if (b0 < INT_0 || b1 < INT_0 || b2 < INT_0 || b3 < INT_0) {
          throw new IllegalArgumentException("Illegal character in Base64 encoded data.");
        }
        int o0 = (b0 << INT_2) | (b1 >>> INT_4);
        int o1 = ((b1 & INT_15) << INT_4) | (b2 >>> INT_2);
        int o2 = ((b2 & INT_3) << INT_6) | b3;
        out[op++] = (byte) o0;
        if (op < oLen) {
          out[op++] = (byte) o1;
        }
        if (op < oLen) {
          out[op++] = (byte) o2;
        }
      }
      return out;
    }
    /**
     * Basic inflate for a data block (the data could be anything
     * @param data the input data in bytes
     * @return a byte array
     */
    public static byte[] deflateDataBlock(byte[] data){
        ByteArrayOutputStream bts = new ByteArrayOutputStream();
        DeflaterOutputStream dfos = new DeflaterOutputStream(
                bts,new Deflater(Deflater.DEFLATED,true));
        byte[] ret = null;
        try {
            dfos.write(data);
            dfos.finish();
            ret = bts.toByteArray();
        } catch (IOException e) {
            //do notihng
            e = null;
        }
        try{
        dfos.close();
        }catch(Exception e){};
        try{
            bts.close();
            }catch(Exception e){};
        return ret;
    }
    /**
     * a infalte funciton
     * @param data input zipped data
     * @return byte array of unzipped data
     */
    public static byte[] inflateDataBlock(byte[] data){
        ByteArrayInputStream bai = new ByteArrayInputStream(data);
        InflaterInputStream ifis = new InflaterInputStream(bai,
                new Inflater(true));
        byte[] buf = new byte[BUFFER_SIZE]; // suppose the length should be enough
        ByteArrayOutputStream bts = new ByteArrayOutputStream();
        byte[] ret = null;
        try{
            int size = ifis.read(buf);
            while(size > 0){
                bts.write(buf,0,size);
                size = ifis.read(buf);
            }
        }catch(IOException e){
            // do nothing
            e = null;
        }
        ret = bts.toByteArray();
        try{
            ifis.close();
            }catch(Exception e){};
            try{
                bts.close();
                }catch(Exception e){};
                try{
                    bai.close();
                    }catch(Exception e){};
        
        return ret;
    }
    /**
     * read a file out into bytes
     * @param filePath input file path
     * @return byte array of read data
     */
    public static byte[] readAllBytes(String filePath){
        try{
            InputStream is = null;
             try {
                              is = new FileInputStream(filePath);
                              ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                 final byte[] buf = new byte[1024];
                                 int n;
                                 while ((n = is.read(buf)) != -1) {
                                   baos.write(buf, 0, n);
                                 }
                                 return baos.toByteArray();
            
                } catch (final IOException e) {
                  throw e;
                } finally {
                  if (is != null) {
                      try{
                          is.close();
                          }catch(Exception e){};
                    
                 }
                }
        }catch(IOException e){
            return null;
        }
    }
    /**
     * 
     * @param t
     * @return
     */
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
    public static byte[] readClassBytes(Class target,String clsName){
    	String path = "/" + clsName.replace('.', '/') + ".class";
    	if(target == null || !target.getName().equals(clsName) ) {
        try {
          Class srcCls = Class.forName(clsName);
          if (srcCls != null) target = srcCls;
        } catch (Throwable e) {
          if(target == null)return null;
        }
      }
    	return readClassAllBytes(target, path);
    }
    
    /**
	 * Read all bytes from file
	 * @param path file path
	 * @return byte array of the file
	 */
	public static byte[] readClassAllBytes(Class target, String path){
		InputStream inp = null;
		ByteArrayOutputStream bto = null;
		if(path == null)path = "/" + target.getName().replace('.', '/') + ".class";
		try{
			inp = target.getResourceAsStream(path);
			if(inp == null)return null;
			bto = new ByteArrayOutputStream();
			byte[] bytes = new byte[65536];
			int l = inp.read(bytes,0,65536);
			while(l > 0){
				bto.write(bytes,0,l);
				l = inp.read(bytes,0,65536);
			}
			bytes = bto.toByteArray();
			return bytes;
		}
		catch(Exception e){
			return null;
		}finally{
			if(inp!= null){
				try{
				inp.close();
				}catch(Exception e){}
			}
			if(bto != null){
				try{
					bto.close();
					}catch(Exception e){}
			}
		}
	}
}