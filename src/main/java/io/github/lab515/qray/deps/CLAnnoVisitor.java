package io.github.lab515.qray.deps;

import io.github.lab515.qray.conf.Config;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * Method Annotation Adapter
 * @author I312865
 *
 */
public class CLAnnoVisitor extends AnnotationVisitor{
	private CLVisitor parent = null;
	private String annoName = null;
	private boolean hasQray = false;
	/**
	 * Constructor
	 * @param parCls Annotation Visitor
	 * @param nameDesc ByteArrayOutputStream from CLMethodAdapter.
	 */
	public CLAnnoVisitor(CLVisitor parCls, String nameDesc){
		super(Opcodes.ASM5);
		parent = parCls;
		annoName = nameDesc;
		if(annoName != null && annoName.equals(Config.qrayFlag)){
		    Config.addClassRel(parent.getClsName(), "+"); // for now, it might be turned off
		    hasQray = true;
		}
	}
	
	/**
	 * Visits a primitive value of the annotation.
	 * @param name value name
	 * @param value actual value
	 */
	@Override
	public void visit(String name, Object value) {
		//parent.addDepCls(name,false);
		if(value instanceof jdk.internal.org.objectweb.asm.Type){
			jdk.internal.org.objectweb.asm.Type v = (jdk.internal.org.objectweb.asm.Type)value;
			String s = v.getDescriptor();
			parent.addDepCls(s, true);
		}else if(hasQray && (value instanceof Boolean) && !((Boolean)value)){
		    Config.addClassRel(parent.getClsName(), "-"); 
		}
	}
	
	/**
	 * Visits a nested annotation value of the annotation
	 * @param name value name
	 * @param desc class descriptor of the nested annotation class
	 * @return a visitor to visit the actual nested annotation value
	 */
	@Override
	public AnnotationVisitor visitAnnotation(String name, String desc) {
		return new CLAnnoVisitor(parent, desc);
	}

	/**
	 * Visits an array value of the annotation
	 * @param name value name
	 * @return a visitor to visit the actual array value elements
	 */
	@Override
	public AnnotationVisitor visitArray(String name) {
	    return new CLAnnoVisitor(parent, name);
	}
	
	/**
	 * Visits the end of the annotation
	 */
	@Override
	public void visitEnd() {
	}

	/**
	 * Visits an enumeration value of the annotation
	 * @param name value name
	 * @param desc class descriptor of the enumeration class
	 * @param value actual enumeration value.
	 */
	@Override
	public void visitEnum(String name, String desc, String value) {
		parent.addDepCls(desc,false);
	}
	
}
