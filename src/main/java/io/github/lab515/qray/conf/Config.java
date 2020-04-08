package io.github.lab515.qray.conf;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.lab515.qray.runtime.*;
import jdk.internal.org.objectweb.asm.Opcodes;


public class Config {
    //format: child-parent = yes
    private static LinkedHashMap<String,String> libRels = null;// it must be synced
	private static ConcurrentHashMap<String,String> validBases = null;

	private static Pattern defaultClassPattern = Pattern.compile(System.getProperty("__qrayDefaultClassPattern") != null ? System.getProperty("__qrayDefaultClassPattern"):
        ".*"); // bydefault we have no limitation
	        
	private static final Pattern classTestNGFilter = Pattern.compile("org\\.testng\\.internal\\.Invoker|org\\.testng\\.TestRunner");
	private static final Pattern classJUnitFilter = Pattern.compile("org\\.junit\\.runner\\.notification\\.RunNotifier|org\\.junit\\.runners\\.ParentRunner|org\\.junit\\.internal\\.runners\\.statements\\.InvokeMethod");
	
	public static final String meClass = Config.class.getName().replace('.', '/');
	//public static final Exception continuer = new Exception();
	//public static final String continuerName = "continuer";
	//public static final String continuerType = "Ljava/lang/Exception;";
	
	public static final String qrayFlag = "L" + Remotable.class.getName().replace('.', '/') + ";"; 
	private static final String classJUnitFlag = "Lorg/junit/";
	private static final String classRemotable = "L" + Remotable.class.getName().replace('.', '/') + ";";
	private static final String classTestNGFlag = "Lorg/testng/annotations/";
	public static final String classTestNGTarget = "org.testng.annotations.Test";
	public static final String classJUnitTarget = "org.junit.Test";
	public static final String callClass = RemoteActionV2.class.getName().replace('.', '/');
	public static final String callMethod = "callMe";
	public static final String callDesc = "(Ljava/lang/Object;Ljava/lang/String;I[Ljava/lang/Object;)Z";
	public static final String callMethod2 = "getIt";
	public static final String callDesc2 = "()Ljava/lang/Object;";
	private static RemoteHandler rmHandler = null;
	
	
	
	public static boolean debugMode = false;
	private static StringBuilder sb = new StringBuilder();
	
	public static synchronized String getInstrLog(){
	    return sb.toString();
	}
	static{

		//FIX: intelli issue (CommandLineWrapper issue)
		ClassLoader sys = ClassLoader.getSystemClassLoader();
		ClassLoader me = Config.class.getClassLoader();
		while(me != null && me != sys){
			me = me.getParent();
		}
		if(me == null){
			try {
				// try to find the loader
				Class me2 = sys.loadClass(Config.class.getName());
				Field f = me2.getDeclaredField("libRels");
				f.setAccessible(true);
				libRels = (LinkedHashMap<String, String>) f.get(null);
				f = me2.getDeclaredField("validBases");
				f.setAccessible(true);
				validBases = (ConcurrentHashMap<String, String>) f.get(null);
			}catch (Throwable t){
				t.printStackTrace();
			}
		}
		if(libRels == null)libRels = new LinkedHashMap<String,String>();
		if(validBases == null) {
			validBases = new  ConcurrentHashMap<String,String>();
			addRemotoClass(Remotee.class.getName(), true);
			addRemotoClass(Remoto.class.getName(), true); // add remotee for none remoto inherited purpose
		}
	}
	public static synchronized void instrLog(String s){
	    sb.append(s);
	    sb.append("\r\n");
	}

	public static synchronized void setRemoteHandler(RemoteHandler handler){
	    if(rmHandler == null)rmHandler = handler; // remoteHandler should be located somewhere in the test code base
    }

    public static boolean isRemoteBaseClass(String clsName){
		return "io.github.lab515.qray.runtime.Remoto".equals(clsName) || "io.github.lab515.qray.runtime.Remotee".equals(clsName);
	}

	public static boolean isValidTestMethod(String annoName){
	    // change: we precisely only support junit test/before/after, testng test/beforemehtod/aftermethod, nothing else
	    String n = null;
	    if(annoName.startsWith(classJUnitFlag)){
	        n = annoName.substring(classJUnitFlag.length());
	        return (n.equals("Test;") || n.equals("Before;") || n.equals("After;"));
	    }else if(annoName.startsWith(classTestNGFlag)){
	        n = annoName.substring(classTestNGFlag.length());
            return (n.equals("Test;") || n.equals("BeforeMethod;") || n.equals("AfterMethod;"));
	    }else return annoName.equals(classRemotable);
	}
	

	public static boolean isTargetMethod(boolean testNGMode, String annoClz){
	    return annoClz.equals(testNGMode ? classTestNGTarget : classJUnitTarget);
	}
	public static boolean isValidQRayClass(String clsName){
	    boolean ret = false;
	    synchronized(libRels){
    	    String v = libRels.get(clsName);
    	    if(v != null){
    	        if(v.startsWith("+"))ret = true;
    	        else if(!v.startsWith("-")){
    	            String[] vs = v.split(",");
            	    for(String s : vs){
            	        if(s.length() < 1)continue;
            	        if(isValidQRayClass(s)){
            	            ret = true;
            	            break;
            	        }
            	    }
    	        }
    	    }
    	    if(!ret){
    	        int p = clsName.indexOf('$'); // sub class
                if(p > 0)ret = isValidQRayClass(clsName.substring(0,p));
    	    }
    	    libRels.put(clsName, ret ? "+" :"-");
    	    return ret;
	    }
	}
	
	
	public static void addClassRel(String sub, String parent){
	    synchronized(libRels){
	        String v = libRels.get(sub);
	        if(parent.startsWith("+"))v = "+";
	        else if(parent.startsWith("-"))v = "-";
	        else if(v != null){
	            if(v.startsWith("+") || v.startsWith("-") || v.indexOf("," + parent + ",") >= 0)return;
	            v += parent + ",";
	        }
	        else v = "," + parent + ",";
	        libRels.put(sub, v);
	    }
	}
	
    public static RemoteHandler getRemoteHandler(){
        return rmHandler;
    }
	
	public static boolean getQRayEnabled(){
	    return !Boolean.getBoolean("__qray_disabled");
	}
	
	private static final String[] matchMethods = new String[]{
		" ", // test case methods 
		"org.testng.internal.Invoker.invokeMethod",
		"org.testng.TestRunner.addInvokedMethod",
		"org.testng.internal.Invoker.runConfigurationListeners",
		"org.testng.internal.Invoker.runInvokedMethodListeners",
		"org.testng.internal.Invoker.runTestListeners",
		"org.testng.TestRunner.addPassedTest",
		// testng and junit
		"org.junit.runners.ParentRunner.runLeaf",
		"org.junit.internal.runners.statements.InvokeMethod.evaluate",
		"org.junit.runner.notification.RunNotifier.fireTestFinished",
		"org.junit.runner.notification.RunNotifier.fireTestStarted",
		};
	
	private static final int T_INVALID = -1;
	public static final int T_CASE = 0;
	private static final int T_TESTNG_INVOKE = 1;
	private static final int T_JUNIT_INVOKE = 7;
	
	private static final int T_TESTNG_RUNMIN = 2;
	private static final int T_TESTNG_RUNMAX = 6;
	
	private static final int T_JUNIT_RUNMIN = 8;
	private static final int T_JUNIT_RUNMAX = 10;
	
	private static final int T_TESTNG_TEST = 2;
	private static final int T_JUNIT_TEST = 8;
	
	private static final int T_TESTNG_MAX = 6;
	
	public static final int M_CASE = 1;
	public static final int M_TESTNG = 2;
	public static final int M_JUNIT = 3;
	
	//mv = new CLMethodAdapter(mv,clsName + "." + name,desc, access,0);
	private static final String testNGPackage = "org.testng.";
	private static final String jUnitPackage = "org.junit.";
	
	private Config(){}
	
	public static boolean isTestNGRun(int methodFlag){
		return methodFlag <= T_TESTNG_MAX;
	}
	public static boolean isTestScope(int methodFlag){
		return methodFlag == T_CASE;
	}
	public static boolean isTestInit(int methodFlag){
		return methodFlag == T_TESTNG_INVOKE || methodFlag == T_JUNIT_INVOKE;
	}
	
	public static boolean isTestTarget(int methodFlag){
		return methodFlag == T_JUNIT_TEST || methodFlag == T_TESTNG_TEST;
	}
	
	public static boolean isInvalidMethod(int methodId){
		return methodId < 0 || methodId >= matchMethods.length;
	}
	public static String getFrameworkInitMethod(int methodFlag){
		if(isTestNGRun(methodFlag)){
			return matchMethods[T_TESTNG_INVOKE];
		}else return matchMethods[T_JUNIT_INVOKE];
	}
	public static String getFrameworkPackage(int methodFlag){
		return isTestNGRun(methodFlag) ? testNGPackage : jUnitPackage;
	}
	
	public static boolean isRootTestCall(boolean testNGMode){
	    StackTraceElement[] els = Thread.currentThread().getStackTrace();
	    // skip current,caller at least, then it should be teh real method, then it's the target method
	    if(els == null || els.length < 6)return true;
	    for(int i = 5; i < els.length;i++){
	        String cls = els[i].getClassName();
	        if(cls == null)continue;
	        if(getRemotoClass(cls) > 0)return false;
	        else if(cls.startsWith(testNGMode ? testNGPackage : jUnitPackage))return true;
	    }
	    return true;
	}

	public static int matchClass(String stdClazz, int level){
		if((level & 2)!= 0){
			Matcher m = Config.classTestNGFilter.matcher(stdClazz);
			if(m != null && m.matches()){
				return M_TESTNG;
			}
			m = Config.classJUnitFilter.matcher(stdClazz);
			if(m != null && m.matches()){
				return M_JUNIT;
			}
		}
		if((level & 1)!= 0 && !isRemoteBaseClass(stdClazz)){
			Matcher m = Config.defaultClassPattern.matcher(stdClazz);
			if(m != null && m.matches())return M_CASE;
		}
		return 0;
	}
	
	public static boolean requireInteception(int methodFlag){
		return methodFlag != T_TESTNG_INVOKE && methodFlag != T_JUNIT_INVOKE && methodFlag != T_JUNIT_TEST;
	}
	
	public static String getMethodName(int methodFlag){
		if(methodFlag > 0 && methodFlag < matchMethods.length)return matchMethods[methodFlag];
		return null;
	}
	
	public static boolean isListenerCalls(int methodFlag){
		return (methodFlag >= T_TESTNG_RUNMIN && methodFlag <= T_TESTNG_RUNMAX) || (methodFlag >= T_JUNIT_RUNMIN && methodFlag <= T_JUNIT_RUNMAX);
	}
	
	// static, private, parametrs, return values are all supported now
	public static int getMethodFlag(String stdClazz, String name, String desc, int access){
		int m = matchClass(stdClazz,-1);
		if(m == M_CASE){
		    // since 2017/07, no limitation anymore!
			if(//(Opcodes.ACC_PUBLIC & access) != -1 && 
					 //(Opcodes.ACC_STATIC & access) == 0 && // for junit, beforeclas or beforemethod is static, we just skip it during remote action handler
			//	   desc.endsWith("()V") && 
			       //  desc.endsWith(")V") && // method now can contains parameters!!!
					 !name.startsWith("<")){ // public, void non static, and no constructor
				 return T_CASE;
			 }
		}else if(m > 0){
			if((Opcodes.ACC_STATIC & access) != -1){ // we assume it's all non static
				String n = stdClazz + "." + name; 
				for(int i = 0;i < matchMethods.length;i++){
					if(matchMethods[i].equals(n))return i;
				}
			}
		}
		return T_INVALID;
	}

	public static void addRemotoClass(String cls, boolean valid){
		validBases.put(cls,valid ? "1" : "");
	}
	public static int getRemotoClass(String cls){
		String ret = validBases.get(cls);
		if(ret == null)return -1;
		return ret.length();
	}
}
