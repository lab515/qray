package io.github.lab515.qray.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;

import io.github.lab515.qray.conf.Config;
import io.github.lab515.qray.utils.ExUtils;

public class RemoteActionV2 {
    static class RemoteMethod{
        public Method mtd;
        public Remotype type;
        public boolean beamMeUp;
        public String provider;
        public String desc;
        public boolean batch;
        public boolean rollback;
        public String[] res;
        
        public RemoteMethod(Method m, String d, Remotype t, Remotable manno, Remotable canno){
            mtd = m;
            desc =d;
            type = t;
            beamMeUp = manno != null ? manno.beamMeUp() : canno.beamMeUp();
			batch = manno != null ? manno.batch() : canno.batch();
            rollback = manno != null ? manno.rollback() : canno.rollback();
            if(manno != null){
            	provider = manno.provider();
            	res = manno.resources();
			}
			if(provider == null || provider.length() < 1)provider = canno.provider();
            //if(res == null || res.length < 1)res = canno.resources();
			// note: method level resource may be merged
        }
    }
    static class RemoteDryRun{
        public int mtdIdx;
        public Object[] paras; // add parameters support
        public RemoteDryRun(int idx, Object[] ps){
            mtdIdx = idx;
            paras = ps;
        }
        
    }
    private static ThreadLocal<RemoteActionV2> v2s = new ThreadLocal<RemoteActionV2>();
	private static final int S_NORMAL = 0;
	private static final int S_DRYRUN = 1;
	private static final int C_TESTNG = 0;
	private static final int C_TESTCASE = 1;
	private static final int C_FRMK = 2; // means it's call from this framewokr
	private static final String myName = RemoteActionV2.class.getName();
	
	
	//private RemoteHandler rmHandler = null; // remote handler should be global
	private ArrayList<RemoteDryRun> dryRuns = null;
	private LinkedHashMap<String,Integer> regInfo = null;
	private ArrayList<RemoteMethod> methods = null;
	private int testMethod = -1;


	// context sensitive parts
	private boolean dryRunning = false;
	private int callIndex = 0;
	private Class caseClazz = null;
	private Remotee caseObj = null;
	// all the errors
	private String segError = null;
	private String[] segInfos = null;
	private int segStart = -1;
	private int segEnd = -1;
	private int callBackDepth = 0;


	private String sysError = null;



	private Remotype testType = Remotype.UNDEFINED; // bypassed means if current test is suitable for remoting
	private boolean testNGMode = false;
	private Object retVal = null;
	private RemoteActionV2(){}
	
	private static RemoteActionV2 getMe() throws Exception{
		RemoteActionV2 o = v2s.get();
		if(o == null){
			v2s.set(o = new RemoteActionV2());
		}
		return o;
	}

    // old format: 2 for each methods, exception, log, finally add me
    // new format: 3 for each method, exception, log, return value, finally add me
    // simply things
	protected void setErrorInfos(String errInfos, boolean serverError) throws Exception{
	    segInfos = null;
	    segError = null;
	    if(segEnd <= segStart){
	        segError = "system error: unexpected call, segEnd is not set!";
	        return;
	    }
	    if(serverError)segError = "system error: " + errInfos;
        else{
            int cc = 0;
            String[] arr = Remoto.unpackData(errInfos); // exception, and log info
            if(arr == null){
                segError = "system error: " + errInfos;
                return;
            }
            if(segEnd - segStart > 1){ // if it's multiple, check skips
                segInfos = new String[(segEnd - segStart) * Remoto.C_RESULTS + 1]; // awesome stuff
                for(int i = segStart;i<segEnd;i++){
                    RemoteMethod rm = methods.get(dryRuns.get(i).mtdIdx);
                    if(!(rm.type == Remotype.LOCAL || rm.type == Remotype.SKIP || rm.type == Remotype.DISABLED)){ // actually it should only be skip
                        for(int c = 0; c < Remoto.C_RESULTS; c++){
                            if(arr.length > cc){
                                segInfos[(i - segStart) * Remoto.C_RESULTS + c] = arr[cc];
                                cc++;
                            }
                        }
                    }else{
                    	if(rm.type == Remotype.DISABLED){
                    		segInfos[(i - segStart) * Remoto.C_RESULTS] = "Error: method " + rm.mtd.toGenericString() + " is disabled!";
						}
                        // nothing to fill
                    }
                }
                segInfos[segInfos.length - 1] = arr[arr.length - 1];
            }else {
                segInfos = arr;
                cc = (segEnd - segStart) * Remoto.C_RESULTS;
            }
            if(segInfos== null || segInfos.length != cc + 1){
                throw new Exception(segError = "unexpected server result package: num: " + (segInfos != null ? segInfos.length : 0) + ", expect " + (cc + 1));
            }
            String objPack = segInfos[segInfos.length - 1];
            if(objPack != null && objPack.length() > 0){
				ArrayList<Remotee> remotees = new ArrayList<Remotee>();
            	Remoto.unpackBase(objPack, caseClazz, caseObj.getStub(), caseObj, remotees);
            	remotees.clear();
			}
        }
	}
	
	public static Object getIt(){
        RemoteActionV2 o = v2s.get();
        Object ret = null;
        if(o != null){
            ret = o.retVal;
        }
        return ret;
    }

	public static boolean callMe(Object that, String methodName, int methodFlag, Object[] paras) throws Exception{
		if(!Config.getQRayEnabled())return true; // if globally disabled
		return Config.isTestScope(methodFlag)? // for case, we need to determine if this call is from testng or not
			getMe().handleTestCall(that, methodName, methodFlag, paras) :
			getMe().handleFrameworkCall(that, methodName, methodFlag, paras);
	}
	
	private String handleRemoteParameters(Object[] paras, Method mtd,ArrayList<Remotee> remotees) throws Exception{
	    Class retType = mtd.getReturnType();
	    if(retType != Void.class && !caseObj.getStub().canPack(retType, null,true)){
	        caseObj.getStub().output("warning: remote method return type " + retType.getCanonicalName() + " is not remotable, make sure it's good for your serialization!!");
	    }
	    
	    if(paras == null || paras.length < 1)return "";
	    Class[] tps = mtd.getParameterTypes();
	    Annotation[][] pas = mtd.getParameterAnnotations();
	    // check return type
	    
	    String[] ps = new String[paras.length];
	    for(int i  = 0; i < ps.length;i++){
	        //if(RunBase.class.isAssignableFrom(tps[i])) rc.staticOnly = false; 
	        if(paras[i] != null){
	            // Add: check parameter annotion
	            Remotable rp = null;
	            if(pas != null && pas.length > i && pas[i] != null){
	                for(Annotation an : pas[i]){
	                    if(an instanceof Remotable){
	                        rp = (Remotable)an;
	                        break;
	                    }
	                }
	            }
	            if(caseObj.getStub().canPack(tps[i], rp, true)){
	                if(paras[i] instanceof Remotee)ps[i] = ((Remotee)paras[i]).getStub().packMe(false, true, remotees);
	                else ps[i] = caseObj.getStub().pack(paras[i]);
	            }
	            else if(rp == null || rp.type() != Remotype.SKIP)throw new Exception("parameter " + paras[i] + " is not remotable, type: " + (rp == null ? "undefined" : rp.type()));
	        }
	    }
	    return caseObj.getStub().zip(Remoto.packData(ps));
	}

	private Object[] restoreCxt(Object[] oldData, Class newCls, Remotee newClsObj) throws Exception{
		if(oldData != null){
			dryRunning = (Boolean)oldData[0];
			callIndex = (Integer)oldData[1];
			caseClazz = (Class)oldData[2];
			caseObj = (Remotee)oldData[3];
			segError = (String)oldData[4];
			segInfos = (String[])oldData[5];
			segStart = (Integer)oldData[6];
			segEnd = (Integer)oldData[7];
			callBackDepth--;
		}else {
			if(callBackDepth > 3)throw new Exception("remoting calls stacks too many! please check your code!");
			oldData = new Object[8];
			oldData[0] = dryRunning;
			oldData[1] = callIndex;
			oldData[2] = caseClazz;
			oldData[3] = caseObj;
			oldData[4] = segError;
			oldData[5] = segInfos;
			oldData[6] = segStart;
			oldData[7] = segEnd;
			callIndex = -1;
			segStart = 0;
			segEnd = 0;
			segError = null;
			segInfos = null;
			callIndex = 0;
			callBackDepth++;
			if(newCls != null)caseClazz = newCls;
			if(newClsObj != null)caseObj = newClsObj;
		}
		return oldData;
	}

	private void resetResult(int start, int end){
	    segStart = start;
	    segEnd = end;
	    segError = null;
	    segInfos = null;
	}
	private void handleRes(String[] rs, HashSet<String> res){
	    if(rs != null){
            for(String f : rs){
                if(f == null)continue;
                if((f = f.trim()).length() < 1)continue;
                res.add(f);
            }
        }
	}
	
	private boolean checkStaticOnlyParas(Object[] paras, HashSet<String> checker){
	    if(paras == null || paras.length < 1)return true;
	    boolean ret = true;
	    for(Object p : paras){
	        if(ret && p == caseObj)ret = false;
	        if(p != null)checker.add(p.getClass().getName());
	    }
	    return ret;
	}
	
	// support drynRUnCall, remote method clal
	private void handleRemoteRun(int callIdx, Method method, Object[] paras) throws Exception{
	    // figure out what to run
	    //TODO: need to put back the instr log later!!
	    //if(Config.debugMode)Config.getRemoteHandler().log(Config.getInstrLog());
	    RemoteConfig rc = new RemoteConfig();
	    boolean staticOnly = true; // for member packing
	    boolean beamMeUp = false;
	    int end = callIdx + 1;
	    Remotable manno = null;
	    Remotable canno = null;
	    ArrayList<Method> ms = new ArrayList<Method>();
	    HashSet<String> pCzCheker = new HashSet<String>();
	    if(method != null){
            staticOnly = Modifier.isStatic(method.getModifiers());
            manno = method.getAnnotation(Remotable.class);
            canno = getClassRemotable(method.getDeclaringClass());
            beamMeUp = manno != null ? manno.beamMeUp() : canno.beamMeUp();
            if(manno != null)rc.provider = manno.provider();
            if(rc.provider == null || rc.provider.length() < 1)rc.provider = canno.provider();
            resetResult(callIdx, callIdx+1);
            if(staticOnly)staticOnly = checkStaticOnlyParas(paras,pCzCheker);
            ms.add(method);
            rc.rollback = manno != null ? manno.rollback() : canno.rollback();
        }else{
            RemoteMethod first = methods.get(dryRuns.get(callIdx).mtdIdx);  // this must be remote!
            beamMeUp = first.beamMeUp;
            rc.provider = first.provider;
            for(; end < dryRuns.size();end++){
                RemoteMethod rm = methods.get(dryRuns.get(end).mtdIdx);
                // continue condtion, !local, canBatch = true
                if(rm.type == Remotype.LOCAL || !rm.batch)break;
                if(rm.provider != first.provider && 
                        !(rm.provider != null && first.provider != null && rm.provider.equals(first.provider))){
                    break;
                }
                if(rm.rollback != first.rollback)break;
                if(rm.beamMeUp)beamMeUp = true;
            }
            // change, we allow methods freely combined
            //if(!(end > testMethod && callIdx <= testMethod))end = callIdx + 1; // turn into single mode
            for(int i = callIdx; i < end; i++){
                RemoteMethod rm = methods.get(dryRuns.get(i).mtdIdx);
                if(rm.type == Remotype.SKIP || rm.type == Remotype.DISABLED)continue; // skipper
                if(!Modifier.isStatic(rm.mtd.getModifiers()))staticOnly = false;
                if(staticOnly)staticOnly = checkStaticOnlyParas(dryRuns.get(i).paras,pCzCheker);
                ms.add(rm.mtd);
            }
            canno = getClassRemotable(caseClazz);
            rc.rollback = first.rollback;
            resetResult(callIdx, end);
        }
        // prepare the stuff
		// first of all, set the provider into targetObj
		try{
			Field f = Remoto.class.getDeclaredField("_q_provider"); // hard set the provider
			f.setAccessible(true);
			f.set(caseObj.getStub(), rc.provider);
			f = Remoto.class.getDeclaredField("_q_target"); // hard set the provider
			f.setAccessible(true);
			f.set(caseObj.getStub(), caseObj);
            f = Remoto.class.getDeclaredField("_q_rollback"); // hard set the provider
            f.setAccessible(true);
            f.set(caseObj.getStub(), rc.rollback);
		}catch(Exception e){
			segError = "system error: prepare Remoto for remote object failed: " + e.getMessage() + "\r\n" + ExUtils.getStackTrace(e);
			return;
		}
        //  added: dryrun for a test case
		Object[] d = null;
        if(method == null && callIdx <= 0) {
            RemoteMethod tester = methods.get(dryRuns.get(testMethod).mtdIdx);  // this must be remote!
			try {
				d = restoreCxt(null, null,null);
                caseObj.getStub().onTestRun(tester.mtd); // caseObj could be null!!!
            } catch (Throwable e) {
                sysError = "system callback error onTestRun: " + e.getMessage() + "\r\n" + ExUtils.getStackTrace(e);
                return;
            }finally {
				if(d != null)restoreCxt(d,null,null);
			}
        }
	    Method[] mtds = new Method[ms.size()];
        ms.toArray(mtds);
        ms.clear();
        d = null;
        try{
			d = restoreCxt(null,null,null);
			caseObj.getStub().onRemoting(mtds);
        }catch(Exception e){e.printStackTrace();
            segError = "system callback error onRemoting:  " + e.getMessage() + "\r\n" + ExUtils.getStackTrace(e);
            return;
        }finally{
			if(d != null)restoreCxt(d,null,null);
		}
        try{
			ArrayList<Remotee> remotees = new ArrayList<Remotee>();
			rc.packedMe = caseObj.getStub().packMe(staticOnly, beamMeUp, remotees);
    	    if(pCzCheker.size() > 0){
    	        rc.paraClzz = new String[pCzCheker.size()];
    	        pCzCheker.toArray(rc.paraClzz);
    	        pCzCheker.clear();
    	    }
    	    handleRes(canno.resources(),pCzCheker);
    	    
            String packedMethods = null;
    	    String s = null;
    	    
    	    if(method != null){
                s = method.toGenericString();
                if(Config.debugMode)caseObj.getStub().output("calling remote method: " + s);
                packedMethods = "#" + s + "@" + handleRemoteParameters(paras,method, remotees);
                if(manno != null)handleRes(manno.resources(), pCzCheker);
                mtds = new Method[]{method};
            }else{
                StringBuilder sb = new StringBuilder();
                if(Config.debugMode)caseObj.getStub().output("we have total " + methods.size() + " methods, they will be called in below sequence:");
                for(int i = callIdx; i < end; i++){
                    RemoteMethod rm = methods.get(dryRuns.get(i).mtdIdx);
                    if(rm.type == Remotype.SKIP || rm.type == Remotype.DISABLED)continue; // skipper
                    handleRes(rm.res, pCzCheker);
                    if(sb.length() > 0)sb.append("\n");
                    s = rm.mtd.toGenericString();
                    if(Config.debugMode)caseObj.getStub().output("\t" + (i == testMethod ? "target:" : (i > testMethod ? "teardown:" : "setup:")) + s);
                    if(testMethod == i || (end - callIdx) == 1)sb.append("#");
                    sb.append(s);
                    sb.append("@");
                    sb.append(handleRemoteParameters(dryRuns.get(i).paras,rm.mtd, remotees));
                }
                packedMethods = sb.toString();
            }
    	    
    	    handleRes(caseObj.getStub().getExtraDataRetention(), pCzCheker);
            // ok, filter the data
    	    if(pCzCheker.size() > 0){
                rc.res = new String[pCzCheker.size()];
                pCzCheker.toArray(rc.res);
                pCzCheker.clear();
            }
            // run remotely
			remotees.clear();
    	    RemoteHelper.run(this, packedMethods,rc);
            if(segError == null && segInfos == null)segError = "system error: no results cam from remote call!";
        }catch(Exception e){
            segError = "system error: remote run with system error: " + e.getMessage() + (e.getCause() != null ? e.getCause().getClass().getName() + "/" + e.getCause().getMessage() : "");
            e.printStackTrace();
        }finally{
			d = null;
            try{
				d = restoreCxt(null,null,null);
                caseObj.getStub().onRemoted(mtds);
            }catch(Exception e){e.printStackTrace();}
            finally {
				if(d != null)restoreCxt(d,null,null);
			}
        }
	}
	
	
	private void reset(){
	    resetResult(0, 0);
	    sysError = null; // reset teh error info
		if(dryRuns != null)dryRuns.clear();
		else dryRuns = new ArrayList<RemoteDryRun>();
		if(regInfo != null)regInfo.clear();
		else regInfo = new LinkedHashMap<String,Integer>();
		if(methods != null)methods.clear();
		else methods = new ArrayList<RemoteMethod>();
		testMethod = -1;
		testType = Remotype.UNDEFINED;
		callIndex = 0;
		caseClazz = null;
		caseObj = null;
	}
	
	protected Class getTestClass(){
	    return caseClazz;
	}
	protected Remotee getTestObject(){
	    return caseObj;
	}

	// support interface now
	private Remotable getClassRemotable(Class clz){
		if(clz == null || clz == Object.class)return null;
		Remotable t = null;
		try{
			t = (Remotable)clz.getAnnotation(Remotable.class);
		}catch(Exception e){}
		if(t == null)t = getClassRemotable(clz.getSuperclass());
		if(t == null){
			Class[] clzs = clz.getInterfaces();
			if(clzs != null && clzs.length > 0){
				for(Class c : clzs){
					t = getClassRemotable(c);
					if(t != null)break;
				}
			}
		}
	    return t;
	}

	private Exception handleException(String err) {
	    Exception ret = null;
			try{
					ret = caseObj.getStub().unpack(err, null);
			}catch(Exception e){
					try{
						ret = caseObj.getStub().unpack(err, Exception.class);
					}catch(Exception e2){
						e2.printStackTrace();
					}
			}
			if(ret == null) ret = new Exception(err.length() > 200 ? err.substring(0,200) + "..."  : err);
			return ret;
	}
	
	private Method getTestMethod(String shortName, Class clazz, Object[] paras, boolean isStatic){
	    Method m = null;
	    boolean clsIn = shortName.indexOf('.') > 0;
	    while(clazz != null && clazz != Object.class && m == null){
	        Method[] ms = clazz.getDeclaredMethods();
	        for(int i = 0; i < ms.length && m == null;i++){
	            Method m1 = ms[i];
	            if((clsIn ? m1.getDeclaringClass().getName() + "." + m1.getName() :  m1.getName()).equals(shortName) && (Modifier.isStatic(m1.getModifiers()) ? isStatic : !isStatic)){
	                Class[] ss = m1.getParameterTypes();
	                if((ss != null ? ss.length : 0) == (paras != null ? paras.length : 0)){
	                    // awesome to death, check parameters
	                    if(ss != null && ss.length > 0){
	                        for(int p = 0; p < ss.length;p++){
	                            if(!((paras[p] == null && !ss[p].isPrimitive()) || (paras[p] != null && ss[p].isAssignableFrom(paras[p].getClass())))){
	                                if(ss[p].isPrimitive() && paras[p] != null){
	                                    Class z = paras[p].getClass();
	                                    if((ss[p] == int.class && z == Integer.class) || 
	                                    (ss[p] == double.class && z == Double.class) || 
	                                    (ss[p] == char.class && z == Character.class) || 
	                                    (ss[p] == float.class && z == Float.class) ||
	                                    (ss[p] == boolean.class && z == Boolean.class)||
	                                    (ss[p] == short.class && z == Short.class)||
	                                    (ss[p] == long.class && z == Long.class)||
	                                    (ss[p] == byte.class && z == Byte.class))continue;
	                                }
	                                ss = null;
	                                break;
	                            }
	                        }
	                        if(ss != null)m = m1;
	                    }else m = m1;
	                }
	            }
	        }
	        clazz = clazz.getSuperclass();
	    }
	    return m;
	}
	
	private boolean handleCallResult(int idx, boolean dryRun, Method m) throws Exception{
	    retVal = null;
	    Exception e = null;
	    if(dryRun){
	        Class cz = m.getReturnType();
	        if(cz != Void.class) {
	            retVal = caseObj.getStub().getDryReturn(m);
	            if(retVal == null && cz.isPrimitive()){
	                if(cz == boolean.class)retVal = false;
	                else if(cz == byte.class)retVal = (byte)0;
	                else if(cz == char.class)retVal = (char)0;
	                else if(cz == short.class)retVal = (short)0;
	                else if(cz == int.class)retVal = 0;
	                else if(cz == long.class)retVal = 0L;
	                else if(cz == float.class)retVal = (float)0;
	                else if(cz == double.class)retVal = (double)0;
	            }
	        }
	    }else{
    	    if(segError != null)e = new Exception(segError);
    	    else if(segInfos == null)e = new Exception("system error: no remote call results available!");
    	    else {
				idx = (idx - segStart) * Remoto.C_RESULTS;
				if (idx < 0 || idx >= segInfos.length - 2)
					e = new Exception("system error: lack of return results segments: " + idx);
				else {
					if (segInfos[idx + 1] != null && segInfos[idx + 1].length() > 0) {
						caseObj.getStub().output(segInfos[idx + 1]);
					}
					// handle exception
					if (segInfos[idx] != null && segInfos[idx].length() > 0)
						e = handleException(segInfos[idx]);
					else {
						String pk = segInfos[idx + 2];
						if (pk != null && pk.length() > 0 && m.getReturnType() != Void.class) {
							if (Remotee.class.isAssignableFrom(m.getReturnType()) || (m.getReturnType() == Object.class && pk.startsWith("#"))) {
								ArrayList<Remotee> remotees = new ArrayList<Remotee>();
								retVal = Remoto.unpackBase(pk, m.getReturnType(), caseObj.getStub(), null, remotees);
								remotees.clear();
							} else
								retVal = caseObj.getStub().unpack(segInfos[idx + 2], m.getReturnType()); // pack the object
						}
					}
				}
			}
			Object[] d = null;
    	    try {
				d = restoreCxt(null,null,null);
				retVal = caseObj.getStub().onResult(m, retVal, e);
			}finally {
    	    	if(d != null)restoreCxt(d,null,null);
			}
	    }
	    return false;
	}
	
	
	private boolean handleTestCall(Object caser, String method, int methodFlag, Object[] paras) throws Exception{
	    // special logic
	    Class clazz = null;
	    Method mtd = null;
	    if(caser instanceof Class){ // this is only for junittest, static methods!!!
	        clazz = (Class)caser; 
	        caser = null; 
	    }else{
	        clazz = caser.getClass();
	    }
	    if(!Remotee.class.isAssignableFrom(clazz)){
	        if(dryRunning)caseClazz = clazz; // just fix the issue
	        return !dryRunning;// ? false: true;//throw new Exception("test object must be inheritted from RunBase!!");
	    }
	    //FIX: if caseClass != clazz, then it's 
	    if(sysError != null && caseClazz == clazz){
	        if(testMethod < 0 && !dryRunning){ // it means this is now a good test case run, just skip it
	            return false; // ignore the sys error
	        }
	        throw new Exception(sysError);
	    }
        
	    int pos = method.lastIndexOf('.');
        String mname = method.substring(pos+1, method.lastIndexOf('('));
		Remotype tp = Remotype.UNDEFINED; // undefined by default == remote
        
        mtd = getTestMethod(mname,clazz,paras,caser == null);
        if(mtd == null){ // unlikely
            throw new Exception("system error: method " + method + " not existed in class: " + clazz.getName());
        }
        //FIX: support private for now
        mname = mtd.toGenericString();
		Remotable canno = getClassRemotable(clazz);
		Remotable manno = mtd.getAnnotation(Remotable.class);
        
        if(dryRunning){
            // add: change to generic string
			if(clazz != caseClazz){ // init stuff
			    caseClazz = clazz;
			    caseObj = null;
            }
			if(caseObj == null && caser != null){
			    caseObj = (Remotee)caser;
			}
			if(Config.debugMode)caseObj.getStub().output("calling: " + method);

			// determine if this is a test target, since testng might change, from framework call, it's not reliable
			Annotation[] annos = mtd.getAnnotations();
			if(annos != null){
			    for(Annotation an : annos){
			        if(Config.isTargetMethod(testNGMode,an.annotationType().getName())){
			            testMethod = dryRuns.size();
			        }
			    }
			}
			// only for testMethod
			if(canno == null && manno == null){
			     throw new Exception("system error: remote class should have Remotable annoation!");
			}
			tp = manno != null ? manno.type() : canno.type();
		    //if(tp == Remotype.UNDEFINED){
			    //if(testMethod == dryRuns.size()){ // it's a test method
			    //    tp = Config.getRemoteHandler().isRemotable(caser, mtd) ? Remotype.REMOTE :  Remotype.LOCAL;
			    //}else{
			    //    tp = Config.getRemoteHandler().onInvoke(caser, mtd, true);
			    //}
		    //}
			// only for test method
			if(testMethod == dryRuns.size()){
			    if(tp == Remotype.UNDEFINED){
			        tp = Config.getQRayEnabled() ? Remotype.REMOTE : Remotype.LOCAL;
			    } 
			    testType = tp; // could be skip, local or remote
            }
			Integer i = regInfo.get(method);
			if(i == null){
				regInfo.put(method,i = methods.size());
				methods.add(new RemoteMethod(mtd,method,tp,manno,canno)); // we use mname
			}
			dryRuns.add(new RemoteDryRun(i,paras));
			
			// we enabled remote method is static
			//if(caser == null && tp == Remotype.REMOTE){
            //    setErrorInfos("sys error: method " + method + " is static, please mark it as either skip or local!!",true);
            //}
			return handleCallResult(dryRuns.size() - 1,true,mtd);
		}else {
			// we have difficulty to check if a method call is a planned or not, so we can only check if this call is from framework or not
			boolean rootCall = Config.isRootTestCall(testNGMode);
			//caseClazz == clazz might not be true, because of the static method now is also allowed
			// CHANGE 2018/12/01, allow any remote call now. listener, static calls, etc
			if (testType == Remotype.REMOTE && rootCall && dryRuns != null && caseClazz == clazz && (caseObj == caser || caser == null)) {
				//FIX: object might not the same
				//if (caser != null && caser != caseObj) {
				//	caseObj = (Remotee) caser;
				//}
				Integer idx = regInfo.get(method);
				if (idx != null) {
					while (callIndex < dryRuns.size()) {
						if (dryRuns.get(callIndex).mtdIdx == idx) break;
						callIndex++;
					}
					if (callIndex >= dryRuns.size()) idx = null;
					else idx = callIndex++;
				}
				if (idx != null) {
					//throw new Exception("system error: call method out of dryRun scope, call method: " + method);
					RemoteMethod rm = methods.get(dryRuns.get(idx).mtdIdx);
					if (rm.type == Remotype.SKIP) return false;
					else if(rm.type == Remotype.DISABLED)throw new Exception("method: " + rm.mtd.toGenericString() + " is disabled!");
					else if (rm.type == Remotype.LOCAL) return true;
					// chekc if there is a remote call already, only happend for serial remote calls
					if (!(segEnd > segStart && segStart <= idx && segEnd > idx)) {
						handleRemoteRun(idx, null, null);
					}
					return handleCallResult(idx, false, mtd);
				}
			}
			// it's not a normal test method, process as remote common method
			if (manno != null) {
				tp = manno.type();
				if (tp == Remotype.UNDEFINED) tp = testType;
			} else tp = testType;
			if (tp == Remotype.UNDEFINED) tp = Remotype.LOCAL;

			// logic, if test is remotable, or method is remotable
			if (tp == Remotype.SKIP) return false;
			else if(tp == Remotype.DISABLED)throw new Exception("method: " + mtd.toGenericString() + " is disabled!");
			else if (tp == Remotype.LOCAL) return true;

			// could be static
			Object[] d = restoreCxt(null,clazz, (Remotee)caser);
			try {
				if (caseObj == null) {
					// ADD: find it from parameters
					if (paras != null) {
						for (Object p : paras) {
							if (p != null && p.getClass() == clazz) {
								caseObj = (Remotee) p;
								if (Config.debugMode)
									caseObj.getStub().output("calling static method for class " + mtd.toGenericString() + "/" + clazz.getName() + ", get instance from parameters");
							}
						}
					}
					if (caseObj == null) {
						Constructor ctor = clazz.getConstructor();
						ctor.setAccessible(true);
						caseObj = (Remotee) ctor.newInstance();
						if (Config.debugMode)
							caseObj.getStub().output("calling static method for class " + mtd.toGenericString() + "/" + clazz.getName() + ", create instance...");
					}
				}
				handleRemoteRun(0, mtd, paras);
				return handleCallResult(0, false, mtd);
			} finally {
				restoreCxt(d,null,null);
			}

		}
	}
	
	private boolean handleFrameworkCall(Object runner, String methodName, int methodFlag, Object[] paras) throws Exception{
	    testNGMode = Config.isTestNGRun(methodFlag);
	    if(Config.isTestInit(methodFlag)){
			int c = findRunCaller(methodFlag); // reset things
			if(c != C_FRMK){
				dryRunning = false;
				reset();
                // FIX 2018-02-06, for non qray case, skip the framework call, this has to go into those mud holes
                String testClsName = null;
                if(testNGMode){
                  if(paras[0] != null && paras[0].getClass().isArray()) {
                    Object[] insts = (Object[]) paras[0];
                    testClsName = insts[(Integer) paras[1]].getClass().getName();
                  }else if(paras[0] != null){
                    testClsName = paras[0].getClass().getName();
                  }
                }else{
                    // for junit, call it's method and make sure it has
                    Method tm = getTestMethod("getName",runner.getClass(),null,false);
                    tm.setAccessible(true);
                    testClsName = (String)tm.invoke(runner);
                }
                // only when it's a valid qray test case
                if(testClsName == null || Config.getRemotoClass(testClsName) < 1){
                    return true;
                }
			}
			// we just need to check if this is doable
			//make sure it's fit
			if(!dryRunning){
			    if(Config.debugMode)Config.getRemoteHandler().log("Framework reset called method " + methodName);
				dryRunning = true;
				// now, tricky part here
				try{
					Method m = getTestMethod(Config.getMethodName(methodFlag),runner.getClass(),paras,false);
					if(m == null){
						sysError = "system error: testng interface changes, there are multiple method:" + Config.getMethodName(methodFlag);
					}else{
						m.setAccessible(true);
						m.invoke(runner, paras);
						callIndex = 0;
					}
					if(testMethod < 0 || testType == Remotype.UNDEFINED || dryRuns.size() < 1){
					    sysError = "system error: test method not found during dryrun, possible reason, your test case is skipped, before you have a before/after method defined in parent class and it got failure previously!" + "[tm:" + testMethod + ", type:" + testType + ", dryRuns: " + dryRuns.size();
					}else{
					    RemoteMethod tm = methods.get(dryRuns.get(testMethod).mtdIdx);
    					for(int i = 0; i < methods.size();i++){
    	                    if(methods.get(i).type == Remotype.UNDEFINED){
    	                        methods.get(i).type = testType;
    	                    }
    	                }
    					if(Config.debugMode)Config.getRemoteHandler().log("running test case " + tm.mtd.toGenericString());
					}
				}catch(Throwable e){
				    sysError = "system error: " + e.getMessage() + "\r\n" + ExUtils.getStackTrace(e);
				}finally{
					dryRunning = false;
					
				}
			}
		}
		if(!dryRunning)return true;
		
		
		if(Config.isListenerCalls(methodFlag)){
			return false;
		}
		return true; // by default 
	}
	
	private static int findRunCaller(int methodFlag){
		Throwable t = new Throwable();
		
		boolean findMe = false;
		String m = Config.getFrameworkInitMethod(methodFlag);
		for(StackTraceElement e : t.getStackTrace()){
			if(e == null)continue;
			String c = e.getClassName(); 
			if(!findMe){
				c += "." + e.getMethodName();
				if(c.equals(m)){
					findMe = true;
				}
				continue;
			}
			
			if(c.equals(myName)){
				return C_FRMK;
			}else if(Config.getRemotoClass(c) > 0){
				return C_TESTCASE;
			}	
		}
		return C_TESTNG;
	}
	
	private static int findCaseCaller(int methodFlag){
		Throwable t = new Throwable();
		String c = null;
		boolean findMe = false;
		String pkg = Config.getFrameworkPackage(methodFlag);
		for(StackTraceElement e : t.getStackTrace()){
			if(c == null)continue;
			c = e.getClassName();
			if(!findMe){
				if(Config.getRemotoClass(c) > 0){
					findMe = true;
				}else continue;
			}
			
			if(Config.getRemotoClass(c) > 0){
				return C_TESTCASE;
			}else if(c.startsWith(pkg)){
				return C_TESTNG;
			}
		}
		return C_TESTNG;
	}
	
}
