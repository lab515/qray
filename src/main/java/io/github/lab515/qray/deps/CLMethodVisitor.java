package io.github.lab515.qray.deps;


import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public class CLMethodVisitor extends MethodVisitor{
	private String mname = null;
	private int acc = 0;
	private CLVisitor parent = null;
	/**
	 * Method Adapter
	 * @param name method name + description
	 * @param access method access
	 * @param desc description (parameter+return)
	 * @param cla (class adapter)
	 */
	public CLMethodVisitor(String name, int access, String desc, CLVisitor cla){
	    super(Opcodes.ASM5);
		mname = name;
		acc = access;
		parent = cla;
		getParamSlots(desc);
	}
	
	/**
	 * Parameter slot is the end of method parameters
	 * @param desc description (parameter+return)
	 */
	private void getParamSlots(String desc){
		char a = ' ';
		int array = 0;
		String t = null;
		int v = 0;
		String rtype = null;
		for(int i = 1; i < desc.length();i++){
			a = desc.charAt(i);
			// "ZBCSIJFDL[V","boolean, byte, char, short, J: long, L:longname
			if(a == 'L'){
				v = i; 
				i = desc.indexOf(';',i);
				t = desc.substring(v, i);
			}
			else if(a == '['){
				// fix: long type/ double type will be special
				array++;
				continue;
			}
			else if(array == 0 & (a == 'D' || a == 'J')){ // double, long take 2
				t = desc.substring(i,i+1);
			}
			else if(a == ')'){
				rtype = desc.substring(i+1);
				break;
			}
			else 
				t = desc.substring(i, i+1);
			// add: deps check for parameters
			if(a == 'L')parent.addDepCls(t.substring(1),false); 
			if(array > 0)t = "[" + array + t; // [2 means 2 dimentional
			array = 0;
		}
		parent.addDepCls(rtype, true); 
	}
	
	/**
	 * Visit Annotation
	 * @param desc class descriptor 
	 * @param visible true if the annotation is visible at runtime
	 */
	@Override 
	public AnnotationVisitor visitAnnotation(String desc, boolean visible){
		if(visible)parent.addDepCls(desc,false);
		return new CLAnnoVisitor(parent,desc);
	}
	
	/**
	 * Visit Annotation with a parameter this method
	 * @param para parameter index
	 * @param desc class descriptor 
	 * @param visible true if the annotation is visible at runtime
	 * @return Annotation Visitor
	 */
	@Override 
	public AnnotationVisitor visitParameterAnnotation(int para, String desc, boolean visible){
	    if(visible)parent.addDepCls(desc,true);
		return new CLAnnoVisitor(parent,desc);
	}
	
	/**
	 * Visits the current state of the local variables and operand stack elements
	 * @param type type of this stack map frame
	 * @param nLocal number of local variables 
	 * @param local local variable types in this frame
	 * @param nStack number of operand stack elements
	 * @param stack operand stack types in this frame
	 */
	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack){
	}
	
	/**
	 * Visits an instruction with a single int operand
	 * @param opcode opcode of the instruction to be visited
	 * @param operand operand of the instruction to be visited
	 */
	@Override
	public void visitIntInsn(int opcode, int operand) {
	}
	
	/**
	 * Visits a jump instruction
	 * @param opcode opcode of the type instruction to be visited
	 * @param label operand of the instruction to be visited
	 */
	@Override
	public void visitJumpInsn(int opcode, Label label) {
	}
	
	/**
	 * Visits a local variable instruction
	 * @param code opcode of the type instruction to be visited
	 * @param index operand of the instruction to be visited
	 */
	@Override
	public void visitVarInsn(int code, int index){

	}
	
	/**
	 * visit local variable. the first one is 'this' for non-static
	 * @param name of a local variable
	 * @param desc descriptor of this local variable
	 * @param signature signature of this local variable
	 * @param start instruction corresponding to the scope of this local variable
	 * @param end instruction corresponding to the scope of this local variable
	 * @param index local variable's index
	 */
	@Override
	public void visitLocalVariable(String name, String desc, String signature,Label start, Label end, int index){
		//mv.visitLocalVariable(name, desc, signature, start, end, index);
		parent.addDepCls(desc,true);
	}
	
	/**
	 * Visits a line number declaration
	 * @param line a line number
	 * @param tag instruction corresponding to this line number
	 */
	@Override
	public void visitLineNumber(int line, Label tag){
	}
	
	/**
	 * Visits a line number declaration
	 * @param dflt beginning of the default handler block
	 * @param keys values of the keys
	 * @param labels beginnings of the handler blocks
	 */
	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
	}
	
	/**
	 * Visits a line number declaration
	 * @param opcode of the type instruction to be visited
	 * @param owner name of the field's owner class
	 * @param name 's name
	 * @param desc's descriptor
	 */
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		parent.addDepCls(desc,true);
		parent.addDepCls(owner,false);
	}
	
	
	/**
	 * Starts the visit of the method's code (non abstract method)
	 * add go/reset/incpt/incpt2/incpt3 at beginning
	 */
	@Override
	public void visitCode(){
	}
	
	
	/**
	 * Visits a type instruction
	 * @param opcode opcode of the type instruction to be visited
	 * @param desc operand of the instruction to be visited
	 */
	@Override
	public void visitTypeInsn(int opcode, String desc) {
		parent.addDepCls(desc,false);
	}
	
	//public static LinkedHashMap<String,String> _man = new LinkedHashMap<String, String>();
	/**
	 * Visits a method instruction
	 * add bound before
	 * @param opcode opcode of the type instruction to be visited
	 * @param owner the internal name of the method's owner class 
	 * @param name method's name
	 * @param desc method's descriptor
	 */
	@Override
	@Deprecated
	public void visitMethodInsn(int opcode, String owner, String name, String desc){
		parent.addDepCls(owner,false);
	}
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
	    parent.addDepCls(owner,false);
	}
	@Override
	public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
            Object... bsmArgs) {
	    // nothing to do, normallyu it's a closure method
	}
	/**
	 * Visits a MULTIANEWARRAY instruction
	 * @param desc an array type descriptor 
	 * @param dims number of dimensions of the array to allocate
	 */
	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		parent.addDepCls(desc,true);
	}
	
	/**
	 * Visits a TABLESWITCH instruction
	 * @param min minimum key value 
	 * @param max maximum key value
	 * @param dflt beginning of the default handler block
	 * @param labels beginnings of the handler blocks
	 */
	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt,
			Label[] labels) {
	}
	
	/**
	 * Visits a try catch block
	 * @param start of the exception handler's scope
	 * @param end of the exception handler's scope 
	 * @param handler of the exception handler's code
	 * @param type internal name of the type of exceptions handled by the handler
	 */
	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler,
			String type) {
		parent.addDepCls(type,false);
	}
	
	/**
	 * Visits an IINC instruction
	 * @param var index of the local variable to be incremented
	 * @param inc amount to increment the local variable by
	 */
	@Override
	public void visitIincInsn(int var, int inc){
		
	}
	
	/**
	 * Visits a zero operand instruction
	 * @param opcode of the instruction to be visited
	 */
	@Override
	public void visitInsn(int opcode) {
	}
	
	/**
	 * Visits the end of the method
	 */
	@Override
	public void visitEnd(){
		
	}
	
	/**
	 * Visits a non standard attribute of this method
	 * @param attr an attribute
	 */
	@Override
	public void visitAttribute(Attribute attr) {
	}
	
	/**
	 * Visits a label
	 * @param label Label object
	 */
	@Override
	public void visitLabel(Label label){
	}
	
	/**
	 * Visits a LDC instruction
	 * @param cst the constant to be loaded on the stack
	 */
	@Override
	public void visitLdcInsn(Object cst) {
	    // FIX: ldc for the class should also be considered
	    if(cst instanceof Type){
	        Type t = (Type)cst;
	        parent.addDepCls(t.getClassName().replace('.', '/'),false);
	    }
	}
	
	/**
	 * Visits the maximum stack size and the maximum number of local variables of the method
	 * count new frame
	 * @param maxStack maximum stack size of the method
	 * @param maxLocal maximum number of local variables for the method
	 */
	@Override
	public void visitMaxs(int maxStack, int maxLocal) {
	}

	@Override
	public AnnotationVisitor visitAnnotationDefault() {
		return new CLAnnoVisitor(parent, "");
	}
}
