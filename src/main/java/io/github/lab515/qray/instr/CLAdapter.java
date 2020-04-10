package io.github.lab515.qray.instr;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.security.ProtectionDomain;

import com.sun.tools.attach.VirtualMachine;
import io.github.lab515.qray.conf.Config;
import jdk.internal.org.objectweb.asm.*;

/**
 * currently it's only dedicated for testng purpose, for junit, it goes with old fashion
 * plan: intecept all testng listner, and intecept all test targets
 * @author I073290
 *
 */
public class CLAdapter extends ClassVisitor {
	// track those cases
	private static ClassLoader baseLoader = null;

	private String parentClsName = null;
	private String clsName = null;
	private boolean valid = false;
	private int ct = 0; // case type, case 1, testng, or junit
	private ClassLoader parent = null;
	static{
		//validBases.put(_rootCls,"1"); // means it's ok
        baseLoader = CLAdapter.class.getClassLoader();
	}
	private CLAdapter(ClassVisitor v, ClassLoader cl, int type){
		super(Opcodes.ASM5, v);
		parent = cl;
		ct = type;
	}
	public void updateValid(boolean v){
	    valid = v;
	}

	// look up the parent class by using ASM as well
	private boolean isValidCase(String cls, boolean isInterface){
	    if(cls == null || ct != Config.M_CASE || cls.length() < 1 || cls.startsWith("java.lang.") || cls.startsWith("com.sun."))return false;
	    int flag = Config.getRemotoClass(cls);
		if(flag >= 0){
		    return flag > 0;
        }
		String path = cls.replace('.','/') + ".class";
		ClassLoader cl = parent;
		InputStream is = null;
		while(cl != null) {
		    is = cl.getResourceAsStream(path);
			if(is != null)break;
			if(cl == baseLoader)break;
			cl = cl.getParent();
		}
		if(is == null){
            Config.addRemotoClass(cls,false);
            return false;
        }
		byte[] buf = new byte[10240];
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try{
			int len = 0;
			while((len = is.read(buf,0,10240)) > 0){
				baos.write(buf,0,len);
			}
			buf = baos.toByteArray();
		}catch(IOException e){
			e.printStackTrace();
			buf = null;
		}
		finally {
			try{
				is.close();
				baos.close();
			}catch (IOException e){}

		}
		if(buf == null){
            Config.addRemotoClass(cls,false);
		    return false;
        }
		// check it out by using a class visitor
		try{
			ClassReader cr = new ClassReader(buf);
			CLAdapter classAdapter = new CLAdapter(null, parent, ct);
			cr.accept(classAdapter, 0);//ClassReader.EXPAND_FRAMES);//ClassReader.SKIP_DEBUG);//
			return classAdapter.ct ==  Config.M_CASE;
		}catch(Exception e){
			e.printStackTrace();
            Config.addRemotoClass(cls,false);
			return false;
		}
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			             String superName, String[] interfaces) {
		
		if(clsName == null){
			clsName = name.replace('/', '.');
		}
		if(parentClsName == null && superName != null){
			parentClsName = superName.replace('/', '.');
		}
		if(ct <= 0)return;
		if(ct == Config.M_CASE) { // only when it's a case
			boolean validCase = false;
			if(parentClsName != null)validCase = isValidCase(parentClsName,false);
			if(interfaces != null && !validCase){
				for(int i = 0; i < interfaces.length && !validCase;i++){
					validCase = isValidCase(interfaces[i].replace('/', '.'),true);
				}
			}
			if(!validCase){
				ct = 0;
				Config.addRemotoClass(clsName,false);
				valid = false; // just in case
				return;
			}else{
                if(Config.debugMode)Config.getRemoteHandler().log("loading case: " + clsName); // load it up
                Config.addRemotoClass(clsName,true);
            }
		}
		if(cv != null)cv.visit(version, access, name, signature, superName, interfaces);
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			             String signature, String[] exceptions) {
		if(ct <= 0 || cv == null)return null;
		MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
		if(mv == null)return mv;
		int mFlag = Config.getMethodFlag(clsName, name, desc, access);
		if(Config.isInvalidMethod(mFlag))return mv;
		return new CLMethodAdapter(mv,clsName + "." + name,desc, access,mFlag, this);
	}
	
	static class ClassTrans implements ClassFileTransformer{
		public ClassTrans(){
		}
		
		@Override
		public byte[] transform(ClassLoader loader, String className,
				Class<?> classBeingRedefined,
				ProtectionDomain protectionDomain, byte[] classfileBuffer)
				throws IllegalClassFormatException {
			if(classfileBuffer == null || className == null || classfileBuffer.length > 1024000)return null;
			if(classBeingRedefined != null && (classBeingRedefined.isInterface() ||  classBeingRedefined.isArray() || classBeingRedefined.isPrimitive()))return null;
			// this is simple, only capture the targeted class
			String stdClzName = className.replace('/', '.');
			int tp = Config.matchClass(stdClzName,-1);
			if(tp <= 0)return null;
			return instrument(classfileBuffer,loader,tp);
		}

	}
	public static volatile boolean _inited = false;

	private static byte[] instrument(byte[] data, ClassLoader cl, int type){
		try{
			ClassReader cr = new ClassReader(data);
			ClassWriter cw = new ClassWriter(0); //ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);//ClassWriter.COMPUTE_MAXS); ClassWriter.COMPUTE_FRAMES// use it to do so
			CLAdapter classAdapter = new CLAdapter(cw, cl, type);
			cr.accept(classAdapter, 0);//ClassReader.EXPAND_FRAMES);//ClassReader.SKIP_DEBUG);//
			if(classAdapter.valid){
				if(Config.debugMode)Config.getRemoteHandler().log("instrument " + classAdapter.clsName);
			    return cw.toByteArray();
			}else return null;
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
	}
	
	public static void agentmain(String agentArgument, Instrumentation inst) throws Exception 
    {
	    premain(agentArgument,inst);
	    Class[] cls = inst.getAllLoadedClasses();
	    if(cls != null){
	    	// we have to loop through the loaded class and retransform
				for(Class cl : cls){
					if(Config.matchClass(cl.getName(),-1) > 0) {
						try {
							inst.retransformClasses(cl);
						}catch (Throwable e){
							System.out.println("error during retansforming " + cl.getName());
							e.printStackTrace();
						}
					}
				}
			}
    }
	
	public static void premain(String agentArgument, 
            Instrumentation instrumentation) {
	    if(!Config.getQRayEnabled() || _inited)return;
	    _inited = true;
		try{
			instrumentation.addTransformer(new ClassTrans());
		}catch(Exception e){
			e.printStackTrace();
		}
    }
	
		private static String getMyPath(){
				try {
					URL o = CLAdapter.class.getProtectionDomain().getCodeSource().getLocation();
					if (o.getProtocol() != null && o.getProtocol().equalsIgnoreCase("file") && o.getFile() != null && o.getFile().toLowerCase().endsWith(".jar")) {
						String path = o.getPath();
						if(File.separatorChar == '\\'){
							path = path.substring(1);
						}
						return path;
					}
					return null;
				}catch (Exception e){
					return null;
				}
		}
	// only call it when it's necessary
    public static void initRemoting() throws Exception{
        if(CLAdapter._inited)return;
        // find out jar path
				String p = getMyPath();
				if(p == null)return;
        String pid = getProcessId();
        if(pid == null)return;
        VirtualMachine vm = null;
        vm = VirtualMachine.attach(pid);
        vm.loadAgent(p, "");
        vm.detach();
    }
    
    private static String getProcessId(){
        String s = ManagementFactory.getRuntimeMXBean().getName();
        if(s == null)return null;
        int p = s.indexOf('@');
        if(p < 1)return null;
        return s.substring(0,p);
    }
    
    public static void main(String[] argsx) throws Exception{
			System.setProperty("__qrayDefaultClassPattern", "argo\\.remote\\..*");
		int g = Config.matchClass("org.junit.runners.model.Annotatable", -1);
        initRemoting();
				Thread.sleep(5000);
    }
}
