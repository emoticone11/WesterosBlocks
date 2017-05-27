package com.westeroscraft.westerosblocks.asm;

import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.Opcodes;

import com.westeroscraft.westerosblocks.WesterosBlocks;

import net.minecraft.launchwrapper.IClassTransformer;

public class ClassTransformer implements IClassTransformer, Opcodes {
    @Override
    public byte[] transform(String name, String tname, byte[] bytes)
    {
        if (name.equals("ajx")) { // Obfuscated name for net.minecraft.world.WorldType
            bytes = transformWorldType(name, bytes, true);
        }
        else if (name.equals("net.minecraft.world.WorldType")) {    // Clear name
            bytes = transformWorldType(name, bytes, false);
        }
        else if (name.equals("anv")) { // Obfuscated name for net.minecraft.block.BlockGlass
            bytes = transformBlockGlass(name, bytes, true);
        }
        else if (name.equals("net.minecraft.block.BlockGlass")) {    // Clear name
            bytes = transformBlockGlass(name, bytes, false);
        }
        else if (name.equals("arj")) { // Obfuscated name for net.minecraft.block.BlockPane
            bytes = transformBlockGlassPane(name, bytes, true);
        }
        else if (name.equals("net.minecraft.block.BlockPane")) {    // Clear name
            bytes = transformBlockGlassPane(name, bytes, false);
        }
        
        return bytes;
    }
    
    private MethodNode findMethod(ClassNode cls, String methname, String methsig) {
        Iterator<MethodNode> methods = cls.methods.iterator();
        while(methods.hasNext()) {
            MethodNode m = methods.next();
            // Check if method we want
            if (m.name.equals(methname) && (m.desc.equals(methsig))) {
                return m;
            }
        }
        return null;
    }
    private int findOpSequence(MethodNode meth, int[] opcodes) {
        Iterator<AbstractInsnNode> iter = meth.instructions.iterator();
        int index = -1;
        int seqindex = 0;
        int seqstartindex = -1;

        while (iter.hasNext()) {
            index++;
            AbstractInsnNode node = iter.next();
            if (node.getOpcode() == opcodes[seqindex]) {
                if (seqindex == 0) seqstartindex = index;   // Remember start
                seqindex++;
                if (seqindex == opcodes.length) {   // Done?
                    return seqstartindex;
                }
            }
            else {  // Mismatch
                seqindex = 0;
                seqstartindex = -1;
            }
        }
        return -1;
    }
    
    @SuppressWarnings("unused")
    private void printMethod(MethodNode meth) {
        Iterator<AbstractInsnNode> iter = meth.instructions.iterator();
        System.out.println("printMethod(" + meth.name + ")");
        while (iter.hasNext()) {
            AbstractInsnNode n = iter.next();
            System.out.println("  " + n.getOpcode() + " (" + n.getType() + " - " + n.getClass().getName() + ")");
        }
    }
        
    
    @SuppressWarnings("deprecation")
	private byte[] transformWorldType(String name, byte[] b, boolean obfus) {
        String targetMethodName = "";
        String targetMethodSig = "";

        WesterosBlocks.log.fine("Checking class " + name);
        
        targetMethodName ="getCloudHeight";
        targetMethodSig = "()F";
        
        //set up ASM class manipulation stuff. Consult the ASM docs for details
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(b);
        classReader.accept(classNode, 0);

        // Now find the method
        MethodNode m = findMethod(classNode, targetMethodName, targetMethodSig);
        if (m == null) {
            WesterosBlocks.log.warning("Cannot find "  + targetMethodName + "() in " + name + " for patching");
            return b;
        }
        // Find our target op sequence
        int ldx_index = findOpSequence(m, new int[] { LDC } );
        if (ldx_index < 0) {
            WesterosBlocks.log.warning("Cannot patch "  + targetMethodName + "() in " + name);
            return b;
        }
        // Replace method implementation
        MethodVisitor mv = m;
        m.instructions.clear();
        m.localVariables.clear();

        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(11, l0);
        mv.visitVarInsn(ALOAD, 0);
        if (obfus) {
            mv.visitFieldInsn(GETFIELD, "ajx", "i", "I");
        }
        else {
            mv.visitFieldInsn(GETFIELD, "net/minecraft/world/WorldType", "worldTypeId", "I");
        }
        mv.visitMethodInsn(INVOKESTATIC, "com/westeroscraft/westerosblocks/asm/ClassTransformer", "getWorldHeight", "(I)F");
        mv.visitVarInsn(FSTORE, 1);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLineNumber(12, l1);
        mv.visitVarInsn(FLOAD, 1);
        mv.visitInsn(FCONST_0);
        mv.visitInsn(FCMPL);
        Label l2 = new Label();
        mv.visitJumpInsn(IFLE, l2);
        Label l3 = new Label();
        mv.visitLabel(l3);
        mv.visitLineNumber(13, l3);
        mv.visitVarInsn(FLOAD, 1);
        mv.visitInsn(FRETURN);
        mv.visitLabel(l2);
        mv.visitLineNumber(15, l2);
        mv.visitLdcInsn(new Float("128.0"));
        mv.visitInsn(FRETURN);
        Label l4 = new Label();
        mv.visitLabel(l4);
        if (obfus) {
            mv.visitLocalVariable("this", "Lajx;", null, l0, l4, 0);
        }
        else {
            mv.visitLocalVariable("this", "Lnet/minecraft/world/WorldType;", null, l0, l4, 0);
        }
        mv.visitLocalVariable("rslt", "F", null, l1, l4, 1);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        //ASM specific for cleaning up and returning the final bytes for JVM processing.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        b = writer.toByteArray();
        
        WesterosBlocks.log.fine("Method " + targetMethodName + "() of " + name + " patched!");
        
        return b;
    }
    
    public static float getWorldHeight(int wtIndex) {
        return 256.0F;
    }

    private byte[] transformBlockGlass(String name, byte[] b, boolean obfus) {
        String targetMethodName = "";
        String targetMethodSig = "";

        WesterosBlocks.log.fine("Checking class " + name);
        
        if (obfus) {
            targetMethodName ="f";
            targetMethodSig = "()Lajk;";
        }
        else {
            targetMethodName ="getBlockLayer";
            targetMethodSig = "()Lnet/minecraft/util/BlockRenderLayer;";
        }
        //set up ASM class manipulation stuff. Consult the ASM docs for details
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(b);
        classReader.accept(classNode, 0);

        // Now find the method
        MethodNode m = findMethod(classNode, targetMethodName, targetMethodSig);
        if (m == null) {
            //WesterosBlocks.log.warning("Cannot find "  + targetMethodName + "() in " + name + " for patching");
            return b;
        }
        // Replace method implementation
        MethodVisitor mv = m;
        m.instructions.clear();
        m.localVariables.clear();
        
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(27, l0);
        if (obfus) {
            mv.visitFieldInsn(GETSTATIC, "ajk", "d", "Lajk;");
        }
        else {
            mv.visitFieldInsn(GETSTATIC, "net/minecraft/util/BlockRenderLayer", "TRANSLUCENT", "Lnet/minecraft/util/BlockRenderLayer;");
        }
        mv.visitInsn(ARETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        if (obfus) {
            mv.visitLocalVariable("this", "Lanv;", null, l0, l1, 0);
        }
        else {
            mv.visitLocalVariable("this", "Lnet/minecraft/block/BlockGlass;", null, l0, l1, 0);
        }
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        //ASM specific for cleaning up and returning the final bytes for JVM processing.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        b = writer.toByteArray();
        
        WesterosBlocks.log.fine("Method " + targetMethodName + "() of " + name + " patched!");
        
        return b;
    }

    private byte[] transformBlockGlassPane(String name, byte[] b, boolean obfus) {
        String targetMethodName = "";
        String targetMethodSig = "";

        WesterosBlocks.log.fine("Checking class " + name);
        
        if (obfus) {
            targetMethodName ="f";
            targetMethodSig = "()Lajk;";
        }
        else {
            targetMethodName ="getBlockLayer";
            targetMethodSig = "()Lnet/minecraft/util/BlockRenderLayer;";
        }
        //set up ASM class manipulation stuff. Consult the ASM docs for details
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(b);
        classReader.accept(classNode, 0);

        // Now find the method
        MethodNode m = findMethod(classNode, targetMethodName, targetMethodSig);
        if (m == null) {
            //WesterosBlocks.log.warning("Cannot find "  + targetMethodName + "() in " + name + " for patching");
            return b;
        }
        // Replace method implementation
        MethodVisitor mv = m;
        m.instructions.clear();
        m.localVariables.clear();
        
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(27, l0);
        if (obfus) {
            mv.visitFieldInsn(GETSTATIC, "ajk", "d", "Lajk;");
        }
        else {
            mv.visitFieldInsn(GETSTATIC, "net/minecraft/util/BlockRenderLayer", "TRANSLUCENT", "Lnet/minecraft/util/BlockRenderLayer;");
        }
        mv.visitInsn(ARETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        if (obfus) {
            mv.visitLocalVariable("this", "Larj;", null, l0, l1, 0);
        }
        else {
            mv.visitLocalVariable("this", "Lnet/minecraft/block/BlockPane;", null, l0, l1, 0);
        }
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        //ASM specific for cleaning up and returning the final bytes for JVM processing.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        b = writer.toByteArray();
        
        WesterosBlocks.log.fine("Method " + targetMethodName + "() of " + name + " patched!");
        
        return b;
    }

}
