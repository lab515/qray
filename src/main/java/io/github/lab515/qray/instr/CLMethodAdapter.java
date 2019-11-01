package io.github.lab515.qray.instr;

import java.util.ArrayList;

import io.github.lab515.qray.conf.Config;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

public class CLMethodAdapter extends MethodVisitor {
	private String fullNameWithDesc = null;
	private int access = 0;
	private CLAdapter parent = null;
	private int methodFlag = 0; // 0 is case, 1 - 5 is others
	private final int[] cs = new int[]{Opcodes.ICONST_0, Opcodes.ICONST_1,Opcodes.ICONST_2,Opcodes.ICONST_3,Opcodes.ICONST_4,Opcodes.ICONST_5};
	private ArrayList<String> ptypes = null; 
	private String rtype = null;
	private boolean valid = false;
	public CLMethodAdapter(MethodVisitor v, String fullName, String desc, int access, int methodFlag, CLAdapter parent){
		super(Opcodes.ASM5,v);
		this.fullNameWithDesc = fullName + desc;
		this.access = access;
		this.methodFlag = methodFlag;
		getParamSlots(desc);
		valid = methodFlag != Config.T_CASE; // for test case, we need to check further
		this.parent = parent;
	}
	
	private void getParamSlots(String desc){
		char a = ' ';
		ptypes = new ArrayList<String>(); // record the types
		int array = 0;
		String t = null;
		int v = 0;
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
			if(array > 0)t = "[" + array + t; // [2 means 2 dimentional
			ptypes.add(t);
			array = 0;
		}
	}
	
	private void prepareCall(){
	    int start = 0;
	    if((Opcodes.ACC_STATIC & access) == 0){
	        mv.visitVarInsn(Opcodes.ALOAD, start++); // non static can be taken (defined outside)
	    }else{
	        //mv.visitInsn(Opcodes.ACONST_NULL);
	        mv.visitLdcInsn(Type.getObjectType(fullNameWithDesc.substring(0,fullNameWithDesc.lastIndexOf('.')).replace('.', '/')));
	    }
	    mv.visitLdcInsn(fullNameWithDesc);
		if(methodFlag < cs.length)
			mv.visitInsn(cs[methodFlag]);
		else 
			mv.visitLdcInsn(methodFlag);
		if(ptypes.size() > 0){
			mv.visitIntInsn(Opcodes.BIPUSH, ptypes.size());
			mv.visitTypeInsn(Opcodes.ANEWARRAY,"java/lang/Object");
			for(int i = 0; i < ptypes.size();i++){
				mv.visitInsn(Opcodes.DUP);
				mv.visitIntInsn(Opcodes.BIPUSH, i);
				if(ptypes.get(i).length() == 1){ // it must be preiitive types for sure
					switch(ptypes.get(i).charAt(0)){
					case 'Z':
						mv.visitVarInsn(Opcodes.ILOAD, start+i);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;",false);
						break;
					case 'B':
						mv.visitVarInsn(Opcodes.ILOAD, start+i);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;",false);
						break;
					case 'C':
						mv.visitVarInsn(Opcodes.ILOAD, start+i);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;",false);
						break;
					case 'S':
						mv.visitVarInsn(Opcodes.ILOAD, start+i);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;",false);
						break;
					case 'I':
						mv.visitVarInsn(Opcodes.ILOAD, start+i);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;",false);
						break;
					case 'J':
						mv.visitVarInsn(Opcodes.LLOAD, start++ + i);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;",false);
						break;
					case 'F':
						mv.visitVarInsn(Opcodes.FLOAD, start+i);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;",false);
						break;
						case 'D':
						mv.visitVarInsn(Opcodes.DLOAD, start++ + i);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;",false);
						break;
					default:
						mv.visitInsn(Opcodes.ACONST_NULL);
						break;
					}
				}else{
					mv.visitVarInsn(Opcodes.ALOAD, start+i);
				}
				mv.visitInsn(Opcodes.AASTORE);
			}
		}else{
			mv.visitInsn(Opcodes.ACONST_NULL);
		}
	}
	
	
	//@Override
    //public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
    //    // frames don't need to consider the hash!!
	//    if(type != Opcodes.F_NEW)
	//        mv.visitFrame(type, nLocal, local, nStack, stack);
	//    else{
	//        mv.visitFrame(Opcodes.F_FULL, nLocal, local, nStack, stack);
	//    }
    //}
	private void handleReturnValue(){
        if (rtype == null || rtype.equals("V")) {
            mv.visitInsn(Opcodes.RETURN); // something is going wrong, wtf
            return;
        }
        // retrive the method if need
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, Config.callClass, Config.callMethod2, Config.callDesc2,false);
        if (rtype.length() == 1) {
            switch (rtype.charAt(0)) {
                case 'Z':
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z",false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case 'B':
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B",false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case 'C':
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C",false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case 'S':
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Short");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S",false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case 'I':
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I",false);
                    mv.visitInsn(Opcodes.IRETURN);
                    break;
                case 'J':
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J",false);
                    mv.visitInsn(Opcodes.LRETURN);
                    break;
                case 'F':
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F",false);
                    mv.visitInsn(Opcodes.FRETURN);
                    break;
                case 'D':
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D",false);
                    mv.visitInsn(Opcodes.DRETURN);
                    break;
                default: // soemthing wrong
                    mv.visitInsn(Opcodes.ARETURN);
                    break;
            }

        } else {
            if (!rtype.equals("Ljava/lang/Object;")) {
                // ok, make it correct to do
                String tp = rtype;
                if (tp.charAt(0) != '[') {
                    tp = tp.substring(1, tp.length() - 1);
                }
                mv.visitTypeInsn(Opcodes.CHECKCAST, tp);
            }
            mv.visitInsn(Opcodes.ARETURN);
        }
	    
	}
	// as simple as we can
	@Override
	public void visitCode(){
	    if(!valid){
	        super.visitCode();
	        return;
	    }
	    parent.updateValid(true);
		mv.visitCode();
		prepareCall();
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, Config.callClass, Config.callMethod, Config.callDesc,false);
		Config.instrLog("instr:" + this.fullNameWithDesc);
		if(Config.requireInteception(methodFlag)){ // for case and listenrs
			Label ifer = new Label();
			mv.visitJumpInsn(Opcodes.IFNE, ifer);
			handleReturnValue();
			mv.visitLabel(ifer);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		}
		else mv.visitInsn(Opcodes.POP);
	}
	@Override 
    public AnnotationVisitor visitAnnotation(String desc, boolean visible){
        if(!valid){
            valid = visible && Config.isValidTestMethod(desc);
        }
        return super.visitAnnotation(desc, visible);
    }
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
	    if(!valid)super.visitMaxs(maxStack, maxLocals);
	    else{
    		if(maxStack < 8)maxStack = 8;
    		mv.visitMaxs(maxStack, maxLocals);
	    }
	}
}
