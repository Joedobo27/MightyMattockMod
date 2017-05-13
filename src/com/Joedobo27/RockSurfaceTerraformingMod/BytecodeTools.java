package com.joedobo27.simpleconcretecreation;

import javassist.bytecode.*;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
class BytecodeTools {

    private static final Logger logger = Logger.getLogger(BytecodeTools.class.getName());
    private static final int EMPTY_INT = Integer.MAX_VALUE;

    static int findSlotInLocalVariableTable(JAssistMethodData jAssistMethodData, String variableName){
        LocalVariableAttribute table = (LocalVariableAttribute) jAssistMethodData.getCodeAttribute().getAttribute(LocalVariableAttribute.tag);
        int tableOrdinal;
        tableOrdinal = IntStream.range(0,table.tableLength()).filter(value -> Objects.equals(table.variableName(value), variableName )).findFirst().orElse(-1);
        if (tableOrdinal == -1){
            return -1;
        }
        return table.index(tableOrdinal);
    }

    static int findLineNumberInLineNumberTable(JAssistMethodData jAssistMethodData, String variableName){
        LocalVariableAttribute table = (LocalVariableAttribute) jAssistMethodData.getCodeAttribute().getAttribute(LineNumberAttribute.tag);
        int tableOrdinal;
        tableOrdinal = IntStream.range(0,table.tableLength()).filter(value -> Objects.equals(table.variableName(value), variableName )).findFirst().orElse(-1);
        if (tableOrdinal == -1){
            return -1;
        }
        return table.index(tableOrdinal);
    }

    static boolean findReplaceCodeIterator(CodeIterator ci, Bytecode find, Bytecode replace) throws BadBytecode {

        boolean toReturn = false;
        final byte[] findBytes = find.get();
        LinkedList<Integer> findInts = new LinkedList<>();
        IntStream.range(0, findBytes.length)
                .forEach(value -> findInts.add(Byte.toUnsignedInt(findBytes[value])));
        logger.log(Level.FINE, "findInts: " + findInts.toString());

        final byte[] replaceBytes = replace.get();
        ArrayList<Integer> replaceInts = new ArrayList<>(replaceBytes.length);
        IntStream.range(0, replaceBytes.length)
                .forEach(value -> replaceInts.add(Byte.toUnsignedInt(replaceBytes[value])));
        logger.log(Level.FINE, "replaceInts: " + replaceInts.toString());

        int index;
        int bitLine;
        int bitLine2;
        int findSize = findInts.size();
        logger.log(Level.FINE, "findSize: " + Integer.toString(findSize));
        logger.log(Level.FINE, "replaceSize: " + Integer.toString(replaceInts.size()));
        CIStack ciStack = new CIStack(findSize);

        ci.begin();
        while (ci.hasNext()) {
            index = ci.next();
            ciStack.indexPush(index);

            int[] instruction = getInstruction(ci.lookAhead() - index, index, ci);
            Arrays.stream(instruction).forEach(ciStack::stackPush);
            if (ciStack.getStackSize()<findSize) {
                continue;
            }
            while (ciStack.getStackSize() > findSize) {
                int[] removed = ciStack.stackPop();
            }
            boolean stackSizeUnalikeFind = ciStack.getStackSize() != findSize;
            if (stackSizeUnalikeFind)
                continue;
            boolean stackContentEqualsReplace = Arrays.equals(ciStack.getStack().toArray(), replaceInts.toArray());
            if (stackContentEqualsReplace){
                toReturn = true;
                break;
            }
            boolean stackContentUnalikeFind = !Arrays.equals(ciStack.getStack().toArray(), findInts.toArray());
            if (stackContentUnalikeFind)
                continue;
            logger.log(Level.FINE, "Insert index: " + ciStack.getInsertPoint());

            // Match found and insert the replace array at index
            ci.write(replace.get(), ciStack.getInsertPoint());
            toReturn = true;
            break;
        }
        return toReturn;
    }


    @SuppressWarnings("unused")
    static byte[] addConstantPoolReference(ConstPool cp, String javapDesc) {
        String[] splitDesc1;
        String[] splitDesc2;
        String name = "";
        String descriptor = "";
        int classConstPoolIndex = Integer.MAX_VALUE;
        int poolIndex = Integer.MAX_VALUE;
        byte[] findPoolResult;
        byte[] byteAddress;


        splitDesc1 = javapDesc.split("[ .:]");
        if (Objects.equals(splitDesc1[1], "String")) {
            splitDesc2 = javapDesc.split("String ");
            splitDesc1 = new String[]{"//", "String", splitDesc2[1]};
        }

        if (splitDesc1.length < 3 || splitDesc1.length > 5)
            throw new UnsupportedOperationException();
        if ( (Objects.equals(splitDesc1[1], "Method") || Objects.equals(splitDesc1[1], "Field")) && splitDesc1.length == 3) {
            throw new UnsupportedOperationException();
        }

        if ( (Objects.equals(splitDesc1[1], "Method") || Objects.equals(splitDesc1[1], "Field")) && splitDesc1.length == 4) {
            // The class reference is missing. This happens when a field or method is defined in the same class and javap
            // assumes the reader is aware. addFieldrefInfo and addMethodrefInfo need specifics not assumptions.
            try {
                findPoolResult = findConstantPoolReference(cp, "// class " + cp.getClassName().replaceAll("\\.", "/"));
                classConstPoolIndex = byteArrayToInt(findPoolResult, 2);

            }catch (UnsupportedOperationException e) {
                classConstPoolIndex = cp.addClassInfo(cp.getClassName().replaceAll("/", "."));
            }
            name = splitDesc1[2];
            name = name.replaceAll("\"", "");
            descriptor = splitDesc1[3];
        }
        if ( (Objects.equals(splitDesc1[1], "Method") || Objects.equals(splitDesc1[1], "Field")) && splitDesc1.length == 5) {
            try {
                findPoolResult = findConstantPoolReference(cp, "// class " + splitDesc1[2].replaceAll("\\.", "/"));
                classConstPoolIndex = byteArrayToInt(findPoolResult, 2);
            }catch (UnsupportedOperationException e) {
                classConstPoolIndex = cp.addClassInfo(splitDesc1[2].replaceAll("/", "."));
            }
            name = splitDesc1[3];
            name = name.replaceAll("\"", "");
            descriptor = splitDesc1[4];
        }
        switch (splitDesc1[1]){
            case "Method":
                poolIndex = cp.addMethodrefInfo(classConstPoolIndex, name, descriptor);
                //logger.log(Level.FINE, "bytecode class index: " + Integer.toString(classConstPoolIndex));
                //logger.log(Level.FINE, "bytecode name: " + name);
                //logger.log(Level.FINE, "bytecode descriptor: " + descriptor);
                break;
            case "Field":
                poolIndex = cp.addFieldrefInfo(classConstPoolIndex, name, descriptor);
                break;
            case "class":
                try {
                    findPoolResult = findConstantPoolReference(cp, "// class " + splitDesc1[2].replaceAll("\\.", "/"));
                    poolIndex = byteArrayToInt(findPoolResult, 2);
                }catch (UnsupportedOperationException e) {
                    poolIndex = cp.addClassInfo(splitDesc1[2].replaceAll("/", "."));
                }
                break;
            case "String":
                poolIndex = cp.addStringInfo(splitDesc1[2]);
                break;
            case "int":
                poolIndex = cp.addIntegerInfo(Integer.parseInt(splitDesc1[2]));
        }
        if (Objects.equals(poolIndex, EMPTY_INT))
            throw new UnsupportedOperationException();

        byteAddress = intToByteArray(poolIndex, 2);
        return byteAddress;
    }

    /**
     * This method looks for matches in the constantPool and returns found addresses.
     *
     * @param cp is type ConstPool. This object is for accessing a specific ConstPool.
     * @param javapDesc is a string. This string data is copied directly from javap.exe data dump and represents a description
     *                  of its explanatory value in the constantPool.
     * @return byte[] representing the address in the ConstantPool.
     */
    static byte[] findConstantPoolReference(ConstPool cp, String javapDesc) {
        String[] splitDesc1;
        String[] splitDesc2;
        String refClass = "";
        String cpClass;
        String name = "";
        String eqResult;
        String descriptor = "";
        byte[] byteAddress = null;


        splitDesc1 = javapDesc.split("[ .:]");
        if (Objects.equals(splitDesc1[1], "String")) {
            splitDesc2 = javapDesc.split("String ");
            splitDesc1 = new String[]{"//", "String", splitDesc2[1]};
        }
        if (Objects.equals(splitDesc1[1], "float")) {
            splitDesc2 = javapDesc.split("float ");
            splitDesc1 = new String[]{"//", "float", splitDesc2[1]};
        }

        if (splitDesc1.length < 3 || splitDesc1.length > 5)
            throw new UnsupportedOperationException();
        if ( (Objects.equals(splitDesc1[1], "Method") || Objects.equals(splitDesc1[1], "Field")) && splitDesc1.length == 3) {
            throw new UnsupportedOperationException();
        }

        if ( (Objects.equals(splitDesc1[1], "Method") || Objects.equals(splitDesc1[1], "Field") || Objects.equals(splitDesc1[1], "InterfaceMethod"))
                && splitDesc1.length == 4) {
            // The class reference is missing. This happens when a field or method is defined in the same class and javap
            // assumes the reader is aware. ConstPool references still show the class reference even if the lines in methods don't.
            //classConstPoolIndex = cp.addClassInfo(cp.getClassName().replaceAll("/", "."));
            refClass = cp.getClassName();
            refClass = refClass.replace("/", ".");
            name = splitDesc1[2];
            name = name.replaceAll("\"", "");
            descriptor = splitDesc1[3];
        }
        if ( (Objects.equals(splitDesc1[1], "Method") || Objects.equals(splitDesc1[1], "Field") || Objects.equals(splitDesc1[1], "InterfaceMethod"))
                && splitDesc1.length == 5) {
            //classConstPoolIndex = cp.addClassInfo(splitDesc1[2]);
            refClass = splitDesc1[2];
            refClass = refClass.replace("/", ".");
            name = splitDesc1[3];
            name = name.replaceAll("\"", "");
            descriptor = splitDesc1[4];
        }
        for (int i = 1; i < cp.getSize(); i++) {
            switch (splitDesc1[1]) {
                case "Method":
                    if (cp.getTag(i) != ConstPool.CONST_Methodref)
                        break;
                    eqResult = cp.eqMember(name, descriptor, i);
                    cpClass = cp.getMethodrefClassName(i);
                    if (eqResult != null && Objects.equals(refClass, cpClass)) {
                        byteAddress = intToByteArray(i, 2);
                    }
                    break;
                case "String":
                    if (cp.getTag(i) != ConstPool.CONST_String)
                        break;
                    String cpStr = cp.getStringInfo(i);
                    String refStr = splitDesc1[2];
                    if (cpStr != null && Objects.equals(cpStr, refStr))
                        byteAddress = intToByteArray(i, 2);
                    break;
                case "Field":
                    if (cp.getTag(i) != ConstPool.CONST_Fieldref)
                        break;
                    eqResult = cp.eqMember(name, descriptor, i);
                    cpClass = cp.getFieldrefClassName(i);
                    if (eqResult != null && Objects.equals(refClass, cpClass))
                        byteAddress = intToByteArray(i, 2);
                    break;
                case "class":
                    if (cp.getTag(i) != ConstPool.CONST_Class)
                        break;
                    refClass = splitDesc1[2];
                    refClass = refClass.replace("/", ".");
                    cpClass = cp.getClassInfo(i);
                    if (cpClass != null && Objects.equals(cpClass, refClass))
                        byteAddress = intToByteArray(i, 2);
                    break;
                case "long":
                    if (cp.getTag(i) != ConstPool.CONST_Long)
                        break;
                    long cpLong = cp.getLongInfo(i);
                    long refLong = Long.parseLong(splitDesc1[2].replace("l", ""), 10);
                    if (Objects.equals(cpLong, refLong))
                        byteAddress = intToByteArray(i, 2);
                    break;
                case "float":
                    if (cp.getTag(i) != ConstPool.CONST_Float)
                        break;
                    float cpFloat = cp.getFloatInfo(i);
                    float refFloat = Float.parseFloat(splitDesc1[2].replace("f", ""));
                    if (Objects.equals(cpFloat, refFloat))
                        byteAddress = intToByteArray(i, 2);
                    break;
                case "InterfaceMethod":
                    if (cp.getTag(i) != ConstPool.CONST_InterfaceMethodref)
                        break;
                    eqResult = cp.eqMember(name, descriptor, i);
                    cpClass = cp.getInterfaceMethodrefClassName(i);
                    if (eqResult != null && Objects.equals(refClass, cpClass))
                        byteAddress = intToByteArray(i,2);
                    break;
            }
            if (byteAddress != null)
                break;
        }
        if (byteAddress == null)
            throw new UnsupportedOperationException(String.format("Did not find in ConstantPool: name= %s, descriptor= %s", name, descriptor));
        return byteAddress;
    }

    static byte[] intToByteArray(int value, int byteLength) {
        switch (byteLength) {

            case 1:
                return new byte[]{(byte) value};
            case 2:
                return new byte[]{
                        (byte) (value >>> 8),
                        (byte) value};
            case 4:
                return new byte[]{
                        (byte) (value >>> 24),
                        (byte) (value >>> 16),
                        (byte) (value >>> 8),
                        (byte) value};
        }
        return new byte[0];
    }

    public static int byteArrayToInt(byte[] b, int byteLength) {
        switch (byteLength) {

            case 2:
                return b[1] & 0xFF |
                        (b[0] & 0xFF) << 8;
            case 4:
                return b[3] & 0xFF |
                        (b[2] & 0xFF) << 8 |
                        (b[1] & 0xFF) << 16 |
                        (b[0] & 0xFF) << 24;
        }
        return 0;
    }

    private static int[] getInstruction(int size, int index, CodeIterator codeIterator) {
        int[] toReturn = null;
        int bitLine;
        int bitLine2;
        switch (size) {
            case 1:
                bitLine = codeIterator.byteAt(index);
                toReturn = new int[]{
                        bitLine
                };
                break;
            case 2:
                bitLine = codeIterator.s16bitAt(index);
                toReturn = new int[]{
                        (bitLine & 0xff00) >>> 8,
                        bitLine & 0x00ff
                };
                break;
            case 3:
                bitLine = codeIterator.s32bitAt(index);
                toReturn = new int[]{
                        (bitLine & 0xff000000) >>> 24,
                        (bitLine & 0x00ff0000) >>> 16,
                        (bitLine & 0x0000ff00) >>> 8
                        // not using the last byte
                };
                break;
            case 4:
                bitLine = codeIterator.s32bitAt(index);
                toReturn = new int[]{
                        (bitLine & 0xff000000) >>> 24,
                        (bitLine & 0x00ff0000) >>> 16,
                        (bitLine & 0x0000ff00) >>> 8,
                        (bitLine & 0x000000ff)
                };
                break;
            case 5:
                bitLine = codeIterator.s32bitAt(index);
                bitLine2 = codeIterator.byteAt(index + 4);
                toReturn = new int[]{
                        (bitLine & 0xff000000) >>> 24,
                        (bitLine & 0x00ff0000) >>> 16,
                        (bitLine & 0x0000ff00) >>> 8,
                        (bitLine & 0x000000ff),
                        bitLine2
                };
                break;
            case 6:
                bitLine = codeIterator.s32bitAt(index);
                bitLine2 = codeIterator.s16bitAt(index + 4);
                toReturn = new int[]{
                        (bitLine & 0xff000000) >>> 24,
                        (bitLine & 0x00ff0000) >>> 16,
                        (bitLine & 0x0000ff00) >>> 8,
                        (bitLine & 0x000000ff),
                        (bitLine2 & 0xff00) >>> 8,
                        (bitLine2 & 0x00ff)
                };
                break;
            case 7:
                bitLine = codeIterator.s32bitAt(index);
                bitLine2 = codeIterator.s32bitAt(index + 4);
                toReturn = new int[]{
                        (bitLine & 0xff000000) >>> 24,
                        (bitLine & 0x00ff0000) >>> 16,
                        (bitLine & 0x0000ff00) >>> 8,
                        (bitLine & 0x000000ff),
                        (bitLine2 & 0xff000000) >>> 24,
                        (bitLine2 & 0x00ff0000) >>> 16,
                        (bitLine2 & 0x0000ff00) >>> 8
                        // not using the last byte
                };
                break;
            case 8:
                bitLine = codeIterator.s32bitAt(index);
                bitLine2 = codeIterator.s32bitAt(index + 4);
                toReturn = new int[]{
                        (bitLine & 0xff000000) >>> 24,
                        (bitLine & 0x00ff0000) >>> 16,
                        (bitLine & 0x0000ff00) >>> 8,
                        (bitLine & 0x000000ff),
                        (bitLine2 & 0xff000000) >>> 24,
                        (bitLine2 & 0x00ff0000) >>> 16,
                        (bitLine2 & 0x0000ff00) >>> 8,
                        (bitLine2 & 0x000000ff)
                };
                break;
        }
        return toReturn;
    }

    public static void byteCodePrint(String destinationPath, CodeIterator codeIterator) throws FileNotFoundException, BadBytecode {
        Path printPath = Paths.get(destinationPath);
        PrintWriter out = new PrintWriter(printPath.toFile());
        final String[] instructionOut = {""};
        codeIterator.begin();
        while (codeIterator.hasNext()) {
            int index = codeIterator.next();
            int[] instruction = getInstruction(codeIterator.lookAhead() - index, index, codeIterator);
            instructionOut[0] += Integer.toString(index);
            instructionOut[0] += " ";
            instructionOut[0] += Mnemonic.OPCODE[instruction[0]];
            if (instruction.length > 1) {
                instructionOut[0] += " ";
                IntStream.range(1, instruction.length)
                        .forEach(value -> {
                            instructionOut[0] += Integer.toString(instruction[value]);
                            instructionOut[0] += " ";
                        });
            }
            out.println(instructionOut[0]);
            instructionOut[0] = "";
        }
        out.close();
    }

    static void printArrayToHex(Object[] obj, String name){
        int length = obj.length;
        int[] c = new int[length];
        for (int i=0;i<length;i++){
            c[i]=(int)obj[i];
        }
        String[] a = new String[length];
        for (int i=0;i<length;i++){
            a[i]=String.format("%02X", c[i] & 0xff);
        }
        logger.log(Level.INFO,name + " : " + Arrays.toString(a));
    }

    private static class CIStack {
        private LinkedList<Integer> stack;
        private LinkedList<Integer> listPositions;
        private LinkedList<Integer> byteIndexes;
        @SuppressWarnings({"FieldCanBeLocal"})
        private int maxSize;
        private int stackSize = 0;

        CIStack(int aSize){
            stack = new LinkedList<>();
            listPositions = new LinkedList<>();
            byteIndexes = new LinkedList<>();
            maxSize = aSize;
        }

        void stackPush(int i){
            stack.add(i);
            stackSize++;
        }

        void indexPush(int i){
            listPositions.add(stackSize);
            // Actual last index in stack is stackSize - 1. We need index for the next value to be added.
            byteIndexes.add(i);
        }

        int[] stackPop(){
            int removeAllUpTo = IndexPop();
            removeAllUpTo--; // returns the next position after what was removed. pop 0 and return the new 0.
            // . We want to remove all up to the new value in 0.
            int[] removed = new int[removeAllUpTo+1]; // +1 because of 0-based arrays.
            for (int i=0;i<=removeAllUpTo;i++){
                removed[i] = stack.remove(0);
                stackSize--;
            }
            return removed;
        }

        private int IndexPop(){
            byteIndexes.removeFirst();
            int removed = listPositions.removeFirst();
            int nextPosition = listPositions.peekFirst();
            int size = listPositions.size();
            for (int i=0;i<size;i++){
                listPositions.set(i, listPositions.get(i)-nextPosition);
            }
            return nextPosition;
        }

        int getStackSize(){
            return stackSize;
        }

        LinkedList<Integer> getStack(){
            return stack;
        }

        int getInsertPoint(){
            return byteIndexes.peekFirst();
        }

        LinkedList<Integer> getListPositions(){
            return listPositions;
        }

        LinkedList<Integer> getByteIndexes(){
            return byteIndexes;
        }
    }
}
