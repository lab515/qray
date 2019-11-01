package io.github.lab515.qray.deps;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.lab515.qray.conf.Config;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * Implement ClassAdapter for asm class level.
 */
public class CLVisitor extends ClassVisitor{
	private HashSet<String> dependClses = null;
	private int access = 0;
	private String stdClsName = null;
	private String clsName = null;
	private String parentClsName = null;
	private String[] ancients = null; 

	private CLVisitor(){
	    super(Opcodes.ASM5);
		dependClses = new HashSet<String>();
	}
	
	public String getClsName(){
	    return clsName;
	}
	
	public void addDepCls(String c, boolean check){
		if(dependClses != null && c != null){
			if(!check && (c.startsWith("[") || c.endsWith(";")))check = true;
			if(check && !c.endsWith(";"))return;
			int p = 0;
			while(check && p < c.length()){
				if(c.charAt(p) == '['){
					p++;
					continue;
				}else {
					if(c.charAt(p) == 'L')p++;
					else if(p > 0 || check)return; // nothing important
					break;
				}
			}
			if(p > 0){
				c = c.substring(p, c.length()-1);
			}
			dependClses.add(c);
		}
	}
	public void fillDepClses(Pattern p, HashSet<String> output, String prefix){
		if(output == null || dependClses.size() < 1)return;
		for(String k : dependClses){
			String v = k.replace('/', '.');
			if(!Config.isRemoteBaseClass(v) && (prefix == null || !v.startsWith(prefix)) && p != null) {
				Matcher m = p.matcher(v);
				if (m == null || !m.matches()) continue;
			}
			output.add(k);
		}
	}
	
	/**
	 * check is interface or not
	 * @return true for interface. false for not
	 */
	public boolean isInterface(){
		return (access & Opcodes.ACC_INTERFACE) != 0;
	}
	
	// NOTE: innerclass are not loaded here
	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value){
		addDepCls(desc,true);
		return new CLFieldVisitor(this);
	}
	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access){
		addDepCls(name,false);
	}
	
	@Override
	public  AnnotationVisitor visitAnnotation(String desc, boolean visible){
	    if(visible)addDepCls(desc,false);
		if(visible && desc.equals(Config.qrayFlag)){
		    // awesome, this is a good one
		    Config.addClassRel(clsName, "+");
		}
		return new CLAnnoVisitor(this, desc);
	}
	
	@Override
	public void visitOuterClass(String name, String outerName, String innerName){
		addDepCls(name,false);
	}
	/**
	 * Visits the header of the class. 
	 * filter blacklist and set class to . format (/ format for asm).
	 * @param version the class version (java version)
	 * @param access class's access flags 
	 * @param name internal name of the class 
	 * @param signature signature of this class
	 * @param superName internal of name of the super class 
	 * @param interfaces internal names of the class's interfaces 
	 */
	@Override
	public void visit(int version, int access, String name, String signature,
			             String superName, String[] interfaces) {
		this.access = access;
		if(clsName == null){
			clsName = name;//.replace('/', '.');
			stdClsName = name.replace('/', '.');
		}
		int ac = 0;
		if(superName != null){
		    ac = 1;
		    addDepCls(superName, false);
		    Config.addClassRel(clsName, superName);
			parentClsName = superName.replace('/', '.');
		}else
			parentClsName = null;
		if(interfaces != null){
		    ancients = new String[ac + interfaces.length];
		    if(ac > 0)ancients[0] = superName;
			for(String s : interfaces){
				addDepCls(s, false);
				Config.addClassRel(clsName, s);
				ancients[ac++] = s;
			}
		}else if(ac > 0){
		    ancients = new String[]{superName};
		}
	}
	
	public String[] getAncients(){
	    return ancients;
	}
	
	/**
	 * Visits a method of the class
	 * invoke visit CLMethodAdapter
	 * @param access method's access flags 
	 * @param name method's name
	 * @param desc method's descriptor 
	 * @param signature method's signature
	 * @param exceptions internal names of the method's exception classes
	 */
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			             String signature, String[] exceptions) {
		return new CLMethodVisitor(name, access,desc,this);
	}
	
	/**
	 * Visits the end of the class
	 * add class and methods files to the class and count the hacked class size
	 */
	@Override
	public void visitEnd() {
		dependClses.remove(clsName); 
	}
	
	
	
	/**
	 * ClassTrans/DiffUtils to load binary
	 * set class hash code (interface is 0) with all binary hash and add CLAadapter
	 * @param bts binary code of the class
	 * @param pattern hack level bit flag
	 * @param prefix need to get new data or not
	 * @return new binary code
	 */
	public static String[] process(byte[] bts, HashSet<String> calDeps, Pattern pattern, String prefix) {
		if(bts == null)return null;
		ClassReader cr = new ClassReader(bts);
		CLVisitor classAdapter = new CLVisitor();
		cr.accept(classAdapter, 0);//ClassReader.EXPAND_FRAMES);//ClassReader.SKIP_DEBUG);//
		if(calDeps != null){
			classAdapter.fillDepClses(pattern,calDeps,prefix);
		}
		classAdapter.dependClses.clear();
		return classAdapter.ancients; // return superclass and interfaces
	}
	
	
	public static void main(String[] args) throws Exception{
		java.io.FileInputStream fis = new java.io.FileInputStream("E:/work/automation/t2/qraywriter/bin/com/successfactors/test/qray/library/remote/BaseTestCase.class");
    	byte[] s = new byte[5376];
    	int t = fis.read(s,0,5376);
    	fis.close();
    	HashSet<String> results = new HashSet<String>();
    	CLVisitor.process(s,results,null,null);
    	Config.getRemoteHandler().log(s.length+"");
	}

	@Override
	public void visitAttribute(Attribute arg0) {
		
	}

	@Override
	public void visitSource(String arg0, String arg1) {
		
	}
}
