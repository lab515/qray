package io.github.lab515.qray.deps;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public class CLFieldVisitor extends FieldVisitor{
	private CLVisitor parent; 
	public CLFieldVisitor(CLVisitor cla){
	    super(Opcodes.ASM5);
		parent = cla;
	}
	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
	    if(visible)parent.addDepCls(desc, false);
		return new CLAnnoVisitor(parent, desc);
	}

	@Override
	public void visitAttribute(Attribute arg0) {
	}

	@Override
	public void visitEnd() {
	}
}
