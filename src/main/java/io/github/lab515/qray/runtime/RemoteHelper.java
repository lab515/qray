package io.github.lab515.qray.runtime;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;

import io.github.lab515.qray.conf.Config;
import io.github.lab515.qray.deps.Dependor;
import io.github.lab515.qray.utils.ExUtils;
public class RemoteHelper {
	// case 1778
    private static String _runnerScript = "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";
    private static String _runnerClsName = "com.successfactors.qray.test._main_";
    private static Class  _runnerClz = null;
    public static void doNotDoItUnlessYouAreSureWhatYouAreDoing_setRunnerClass(Class runnerCls) throws Exception{
         if(runnerCls == null) return;
         _runnerClz = runnerCls;
    }
    
    private static String getEncodedClazzData(Remotee caser, Class target, String clz) throws Exception{
    	if(clz == null)return null;
    	byte[] bts = ExUtils.readClassBytes(target, clz);
    	if(bts == null)return null;
    	return caser.getStub().zip2(bts); // new String(ExUtils.encodeBase64(ExUtils.deflateDataBlock(bts)));
    }
    
    // return status, returnresult, console. stack, execinfo
    public static void run(RemoteActionV2 action, String methodCalls, RemoteConfig rc) throws Exception{ //new Class[]{BaseTestCase.class,
    	// assume none of them
        Remotee targetObj = action.getTestObject();
        Class target = targetObj.getClass();
        if(target != action.getTestClass()){
            action.setErrorInfos("system error: remoteobject is not correct, expect: " + target.getName() + ", now is " + (action.getTestClass() != null ? action.getTestClass().getName() : "null"), true);
            return;
        }
        
        RemoteHandler caseHandler = Config.getRemoteHandler();
    	if(caseHandler == null){
    	    action.setErrorInfos("system error: remotehandler instance is not set in Config!!!!",true);
    	    return;
    	}
    	
    	//add: unofficial support for some configuration
    	targetObj.getStub().getConfig(":class:" + target.getName());
    	
    	String libClassPattern = targetObj.getStub().getConfig("libraryPackage");
		String testClassPattern = targetObj.getStub().getConfig("testPackage");
		String dataBase = targetObj.getStub().getConfig("dataFolder");
		if(dataBase == null)dataBase = "/";
		else if(!dataBase.startsWith("/")) dataBase = "/" + dataBase;
		if(!dataBase.endsWith("/"))dataBase += "/";
		if(libClassPattern == null || libClassPattern.length() < 1){
			throw new Exception("warning: libraryPacakge is not specified, using default!");
		}
		String[] exClazz = targetObj.getStub().getExtraDependentClasses();
		if(rc.paraClzz != null){
		    if(exClazz == null)exClazz = rc.paraClzz;
		    else{
		        HashSet<String> all = new HashSet<String>();
		        for(String s : exClazz){
		            if(s != null)all.add(s);
		        }
		        for(String s : rc.paraClzz){
		            if(s != null)all.add(s);
		        }
		        if(all.size() > 0){
		            exClazz =new String[all.size()];
		            all.toArray(exClazz);
		        }else exClazz = null;
		    }
		}
    	String[] targetClses = Dependor.getDependencies(target, exClazz, libClassPattern);
    	// PROBLEM: require all remote library with annoation??
    	// filter again, based on the qray requirement
    	int total = 0; 
    	for(int i = 0; i < targetClses.length;i++){
    	    String clsT = targetClses[i];
    	    if(i == targetClses.length - 1 || Config.isValidQRayClass(clsT) || Config.getRemoteHandler().isTransferable(clsT.replace('/', '.'))){ // the last class is test case, always good
    	        targetClses[total++] = clsT; 
    	    }else{
    	        if(Config.debugMode)targetObj.getStub().output("Missing classes: "+ clsT);
    	    }
    	}
    	
    	String[] arr = new String[total * 2 + 2]; // always add additional RunBase
    	if(_runnerClz != null){
    	    synchronized(RemoteHelper.class){
    	        if(_runnerClz != null){
    	            String data = getEncodedClazzData(targetObj, _runnerClz, _runnerClz.getName());
    	            if(data != null){
    	                _runnerClsName = _runnerClz.getName();
    	                _runnerScript = data;
    	            }
    	            _runnerClz = null;
    	        }
    	    }
    	}
    	arr[0] = _runnerClsName;
    	arr[1] = _runnerScript;
    	//arr[2] = RemoteHandler.class.getName();
    	//arr[3] = getEncodedClazzData(RemoteHandler.class, arr[2]);
    	//arr[2] = RunBase.class.getCanonicalName();
    	//arr[3] = getEncodedClazzData(RunBase.class, arr[2]);
    	for(int i = 0; i < total;i++){
    		String encodedClazz = getEncodedClazzData(targetObj, target, targetClses[i]);
    		if(encodedClazz == null){
    			throw new Exception("class : " + targetClses[i] + " can not be fetched!");
    		}
    		arr[2 + i * 2] = targetClses[i].replace('/', '.');
    		arr[3 + i * 2] = encodedClazz;
    	}
    	String extraCaseData = Remoto.packData(new String[]{
    	        target.getName() + "\n" + methodCalls, 
    			packExtraDataWithClientFiles(action, rc.res, dataBase, testClassPattern), 
    			rc.packedMe});
    	// st, result, log, exception
    	String[] ret = caseHandler.execute(Remoto.packData(arr), extraCaseData,targetObj);
    	if(ret == null || ret.length < 5){
    		action.setErrorInfos("no valid QRay Result returned from server!",true);
    		return;
    	}
		if("OK".equals(ret[0])){
			if(ret[1] != null){
				try{
				    if(ret[1].startsWith("#"))action.setErrorInfos(targetObj.getStub().unzip(ret[1].substring(1)), false);
				    else action.setErrorInfos(ret[1], true); //new String(ExUtils.inflateDataBlock(ExUtils.decodeBase64(ret[1])),"utf-8");
	    			
	    		}catch(Exception e){
	    		    e.printStackTrace();
	    			action.setErrorInfos("remote errorinof is not correct:" + e.getMessage() +", raw " + ret[1],true);
	    		}
			}
			else  action.setErrorInfos("no valid QRay formatted package returned from server!",true);
		}
		else{
			if(ret[3] == null || ret[3].length() < 1)ret[3] = "unknown server error!";
			action.setErrorInfos(ret[3], true);
		}
    }
    
    // Note: resources is a file name list
    private static String packExtraDataWithClientFiles(RemoteActionV2 action, String[] fnames, String baseFolder, String testPackage) {
        if(fnames == null || fnames.length < 1)return null;
        Class target = action.getTestClass();
        Remotee targetObj = action.getTestObject();
        if(testPackage == null){
			targetObj.getStub().output("testPackage is not set, data transfering will try to match!");
    		testPackage = "";
    	}
    	// get teh current class mapping path
    	testPackage = testPackage.trim();
    	String folder = target.getName(); // assume it must be under the com.successfactors.test.qray.cases prefix
    	folder = baseFolder + folder.substring(testPackage.length()).replace('.', '/');
    	String[] data = new String[fnames.length*2];
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	byte[] buf = new byte[8096];
    	Remotee caser = action.getTestObject();
    	for(int i = 0; i < fnames.length;i++){
    		String s = fnames[i];
    		if(s == null)continue;
    		String filePath = null;
    		boolean regress = s.indexOf('/') < 0;
    		InputStream is = null;
    		String p = folder;
    		if(Config.debugMode)targetObj.getStub().output("packing local file: " + s);
    		while(is == null && p != null){
    			if(regress){
    				filePath = p + "/" + s;
    				int pos = p.lastIndexOf('/');
    				if(pos < 1)p = null;
    				else p = p.substring(0,pos);
    			}else {
    				filePath = s;
    				p = null;
    			}
    			is = target.getResourceAsStream(filePath);
    			if(is != null && Config.debugMode)targetObj.getStub().output("packed file path: " + filePath);
    		}
    		data[i*2] = s;
    		if(is == null){
    		    if(Config.debugMode)targetObj.getStub().output("failed to pack file: " + s);
    		    continue;
    		}
     		data[i*2+1] = null;
    		try{
	    		baos.reset();
		    	int read = 0;
		    	while((read = is.read(buf,0,8096)) > 0){
		    		baos.write(buf, 0, read);
		    	}
		    	byte[] buf2 = baos.toByteArray();
		    	String fileData = buf2.length > 0 ? caser.getStub().zip2(buf2) : ""; // new String(ExUtils.encodeBase64(ExUtils.deflateDataBlock(buf2))) : "";
		    	data[i*2+1] = fileData;
    		}catch(Exception e){
    			
    		}
    	}
    	return Remoto.packData(data);
    }
    
/******************* QRAY REMOTE Logic in Groovy 
return this.getClass().getClassLoader().
    loadClass("io.github.lab515.qray.runtime.remote.RunBase").
    getDeclaredMethod("execute",Object.class,Object.class,String.class).
    invoke(null, this, this.getBinding(), data);
    
    /////////////////// Non Groovy Runner Class impl(so we make it compitable with groovy runner /////////////////////
     // classloader
     public static class QRClassLoader extends ClassLoader {
        static{
            registerAsParallelCapable();
        }
        public QRClassLoader(ClassLoader parent){
            super(parent);
        }
        
        public void clearCache(){
        }
        
        public Class defineClass(String name, byte[] b) {
            return super.defineClass(name, b, 0, b.length);
        }
    }
    // qrruner
     public static abstract class QRRunner {
        private LinkedHashMap<String,Object> binds = new LinkedHashMap<String,Object>();
        
        public QRRunner getBinding(){
            return this;
        }
        
        public abstract Object execute();
        public abstract void teardown();
        
        public Object run(){
            return execute();
        }
        
        public QRRunner getMetaClass(){
            return this;
        }
        
        public Object getMetaMethod(String mname, Object npe) {
            if(mname.equals("teardown")){
                return this;
            }else{
                return null;
            }
        }
        
        public void invokeMethod(String name, Object npe) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException{
            if("teardown".equals(name))teardown();
        }
        
        public void setVariable(String name, Object val){
            binds.put(name, val);
        }
        
        public void setProperty(String name, String val){
            binds.put(name, val);
        }
        public Object getVariable(String name){
            return binds.get(name);
        }
    }
    
*/
}
