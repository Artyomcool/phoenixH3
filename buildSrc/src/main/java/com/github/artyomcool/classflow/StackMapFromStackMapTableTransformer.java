package com.github.artyomcool.classflow;

import java.lang.reflect.Modifier;

public class StackMapFromStackMapTableTransformer extends BaseClassFlow {

    // ITEM_* (JVMS §4.7.4)
    public static final int ITEM_Top = 0;
    public static final int ITEM_Integer = 1;
    public static final int ITEM_Float = 2;
    public static final int ITEM_Double = 3;
    public static final int ITEM_Long = 4;
    public static final int ITEM_Null = 5;
    public static final int ITEM_UninitializedThis = 6;
    public static final int ITEM_Object = 7;
    public static final int ITEM_Uninitialized = 8;

    // frame types
    public static final int SAME_MIN = 0;
    public static final int SAME_MAX = 63;
    public static final int SL1_MIN = 64;
    public static final int SL1_MAX = 127;
    public static final int SL1_EXT = 247;
    public static final int CHOP_MIN = 248;
    public static final int CHOP_MAX = 250;
    public static final int SAME_EXT = 251;
    public static final int APPEND_MIN = 252;
    public static final int APPEND_MAX = 254;
    public static final int FULL = 255;

    private static final byte[] STACK_MAP = "StackMap".getBytes();
    private static final byte[] STACK_MAP_TABLE = "StackMapTable".getBytes();

    private int stackMapIndex;

    @Override
    public void parse(ByteArrayInOut inOut) {
        stackMapIndex = 0;
        super.parse(inOut);
    }

    @Override
    protected boolean needParseCodeAttributes() {
        return true;
    }

    private int getStackMapIndex() {
        if (stackMapIndex == 0) {
            stackMapIndex = cp.getOrCreateCp(STACK_MAP, 0, STACK_MAP.length);
        }
        return stackMapIndex;
    }

    @Override
    protected void parseAttribute(int attributeOwner, int attrNameIdx, int len) {
        if (attributeOwner == ATTRIBUTE_CODE) {
            if (cp.cpEquals(attrNameIdx, STACK_MAP_TABLE)) {
                inOut.wu2(getStackMapIndex());
                int lenPos = inOut.wpos();
                inOut.wu4(0);
                int ii = inOut.wpos();
                rewriteStackMapTableToStackMap();
                int newLength = inOut.wpos() - ii;
                inOut.wu4(lenPos, newLength);
                inOut.wu4(codeAttrLenPos, codeAttrLen += newLength - len);
                return;
            }
        }
        super.parseAttribute(attributeOwner, attrNameIdx, len);
    }

    private int readPackedVT() {
        int t = inOut.u1();
        return (t == ITEM_Object || t == ITEM_Uninitialized) ? (t | (inOut.u2() << 8)) : t;
    }

    protected void rewriteStackMapTableToStackMap() {
        int nFrames = inOut.u2();
        inOut.wu2(nFrames);

        frameInfo.resetArrays();

        int lsize = buildInitialLocalsPacked();

        int curOff = -1;

        int[] locals = frameInfo.locals;
        int[] stack = frameInfo.stack;
        for (int f = 0; f < nFrames; f++) {
            int ft = inOut.u1();
            int delta;
            int ssize = 0;

            if (ft >= SAME_MIN && ft <= SAME_MAX) {
                delta = ft;
            } else if (ft >= SL1_MIN && ft <= SL1_MAX) {
                delta = ft - 64;
                ssize = 1;
                stack[0] = readPackedVT();
            } else if (ft == SL1_EXT) {
                delta = inOut.u2();
                ssize = 1;
                stack[0] = readPackedVT();
            } else if (ft >= CHOP_MIN && ft <= CHOP_MAX) {
                delta = inOut.u2();
                lsize -= 251 - ft;
            } else if (ft == SAME_EXT) {
                delta = inOut.u2();
            } else if (ft >= APPEND_MIN && ft <= APPEND_MAX) {
                delta = inOut.u2();
                int k = ft - 251;
                for (int i = 0; i < k; i++) locals[lsize++] = readPackedVT();
            } else if (ft == FULL) {
                delta = inOut.u2();
                lsize = inOut.u2();
                for (int i = 0; i < lsize; i++) locals[i] = readPackedVT();

                ssize = inOut.u2();
                for (int i = 0; i < ssize; i++) stack[i] = readPackedVT();
            } else {
                throw new IllegalStateException("frame_type=" + ft);
            }

            inOut.wu2(curOff += delta + 1);
            writeVerificationTypes(lsize, locals);
            writeVerificationTypes(ssize, stack);
        }
    }

    private void writeVerificationTypes(int ssize, int[] stack) {
        inOut.wu2(ssize);
        for (int i = 0; i < ssize; i++) {
            int vt = stack[i];
            int t = vt & 0xFF;
            inOut.wu1(t);
            if (t == ITEM_Object || t == ITEM_Uninitialized) inOut.wu2(vt >>> 8);
        }
    }

    private int buildInitialLocalsPacked() {
        int[] locals = frameInfo.locals;
        int n = 0;
        int i = cp.offsets[currentDescIdx] + 2;
        if (!Modifier.isStatic(currentAcc)) {
            locals[n++] = (ITEM_Object | (thisCls << 8));
        }
        if (inOut.u1(i++) != '(') throw new IllegalArgumentException("Illegal signature");
        while (inOut.u1(i) != ')') {
            int c = inOut.u1(i);
            switch (c) {
                case 'B':
                case 'C':
                case 'S':
                case 'Z':
                case 'I':
                    locals[n++] = ITEM_Integer;
                    i++;
                    break;
                case 'F':
                    locals[n++] = ITEM_Float;
                    i++;
                    break;
                case 'J':
                    locals[n++] = ITEM_Long;
                    i++;
                    break;
                case 'D':
                    locals[n++] = ITEM_Double;
                    i++;
                    break;
                case 'L': {
                        int start = ++i;
                        while (inOut.u1(++i) != ';') ;

                        int className = cp.getOrCreateCp(start, i++);
                        int ci = cp.getOrCreateCpForTag(ConstantPool.CONSTANT_Class, className);
                        locals[n++] = (ITEM_Object | (ci << 8));
                    }
                    break;
                case '[': {
                        int start = i;
                        int l;
                        while ((l = inOut.u1(++i)) == '[') ;
                        if (l == 'L') while (inOut.u1(++i) != ';') ;

                        int className = cp.getOrCreateCp(start, ++i);
                        int ci = cp.getOrCreateCpForTag(ConstantPool.CONSTANT_Class, className);
                        locals[n++] = (ITEM_Object | (ci << 8));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Illegal signature");
            }
        }
        return n;
    }
}
