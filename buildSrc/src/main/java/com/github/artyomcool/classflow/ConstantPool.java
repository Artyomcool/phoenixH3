package com.github.artyomcool.classflow;

import java.nio.charset.StandardCharsets;

public class ConstantPool {

    public static final int CONSTANT_Utf8 = 1;
    public static final int CONSTANT_Integer = 3;
    public static final int CONSTANT_Float = 4;
    public static final int CONSTANT_Long = 5;
    public static final int CONSTANT_Double = 6;
    public static final int CONSTANT_Class = 7;
    public static final int CONSTANT_String = 8;
    public static final int CONSTANT_Fieldref = 9;
    public static final int CONSTANT_Methodref = 10;
    public static final int CONSTANT_InterfaceMethodref = 11;
    public static final int CONSTANT_NameAndType = 12;
    public static final int CONSTANT_MethodHandle = 15;
    public static final int CONSTANT_MethodType = 16;
    public static final int CONSTANT_Dynamic = 17;
    public static final int CONSTANT_InvokeDynamic = 18;
    public static final int CONSTANT_Module = 19;
    public static final int CONSTANT_Package = 20;

    private final ByteArrayInOut tmp = new ByteArrayInOut(null);

    private ByteArrayInOut in;

    public int count;
    public int[] offsets = new int[128];

    public int extraCount;
    public ByteArrayInOut extraCp = new ByteArrayInOut(null) {
        {
            in = out = new byte[256];
        }

        @Override
        protected void recreateOutput(int newLength) {
            super.recreateOutput(newLength);
            in = out;
        }
    };

    public int countWPos;
    public int endWPos;

    public void reset(ByteArrayInOut in) {
        this.in = in;
        count = extraCount = countWPos = endWPos = extraCp.ip = extraCp.op = 0;
    }

    public void count(int count) {
        this.count = count;
        if (offsets.length < count) {
            offsets = new int[count + 64];
        }
    }

    public void offset(int i, int tag, int pos) {
        offsets[i] = pos;
        if (tag == CONSTANT_Long || tag == CONSTANT_Double) {
            offsets[i + 1] = -1;
        }
    }

    public boolean cpEquals(int idx, byte[] bytes) {
        return cpEquals(in, idx, tmp.in(bytes), 0, bytes.length);
    }

    public boolean cpEquals(int idx, int start, int end) {
        return cpEquals(in, idx, in, start, end);
    }

    private boolean cpEquals(ByteArrayInOut in, int idx, ByteArrayInOut cmp, int start, int end) {
        int offset = offsets[idx];
        if (offset == -1) {
            return false;
        }
        int p = offset - 1;
        int tag = in.u1(p++);
        if (tag != CONSTANT_Utf8) {
            return false;
        }
        return utf8EqualsOrLength(in, p, cmp, start, end) == -1;
    }

    private static int utf8EqualsOrLength(ByteArrayInOut cp, int p, ByteArrayInOut cmp, int start, int end) {
        int len = cp.u2(p);
        if (len != end - start) {
            return len;
        }
        p += len + 1;
        for (int i = end - 1; i >= start; i--, p--) {
            if (cmp.u1(i) != cp.u1(p)) {
                return len;
            }
        }
        return -1;
    }

    public int addExtraCp(int start, int end) {
        return addExtraCp(in.in, start, end); // fixme
    }

    public int addExtraCp(byte[] bytes, int start, int end) {
        int length = end - start;
        extraCp.wu1(CONSTANT_Utf8);
        extraCp.wu2(length);
        extraCp.w(bytes, start, length);
        return extraCount++;
    }

    private static int findCp(ByteArrayInOut cp, int cpStart, int cpEnd, ByteArrayInOut data, int start, int end) {
        int i = 1;

        for (int p = cpStart; p < cpEnd; ) {
            int tag = cp.u1(p++);
            int l = tagLength(tag);
            if (l == -1) {
                l = utf8EqualsOrLength(cp, p, data, start, end);
                if (l == -1) {
                    return i;
                }
                p += l + 2;
            } else {
                p += l;
            }
            i += tagSlots(tag);
        }
        return 0;
    }

    private static int findCp(ByteArrayInOut cp, int cpStart, int cpEnd, int tag, long value) {
        int i = 1;

        for (int p = cpStart; p < cpEnd; ) {
            int t = cp.u1(p++);
            int l = tagLength(t);
            if (t == tag) {
                switch (l) {
                    case 1:
                        if (cp.u1(p) == value) return i;
                        break;
                    case 2:
                        if (cp.u2(p) == value) return i;
                        break;
                    case 4:
                        if (cp.u4(p) == value) return i;
                        break;
                    case 8:
                        if (((long) cp.u4(p) << 32L | cp.u4(p + 4)) == value) return i;
                        break;
                }
            }
            if (l == -1) {
                l = cp.u2(p);
                p += l + 2;
            } else {
                p += l;
            }
            i += tagSlots(tag);
        }
        return 0;
    }

    private int getOrCreateCp(ByteArrayInOut data, int start, int end) {
        int r = findCp(in, offsets[1] - 1, offsets[count - 1], data, start, end);
        if (r != 0) {
            return r;
        }

        r = findCp(extraCp, 0, extraCp.wpos(), data, start, end);
        if (r != 0) {
            return r - 1 + count;
        }

        return addExtraCp(data.in, start, end) + count;
    }

    public int getOrCreateCp(byte[] bytes, int start, int end) {
        return getOrCreateCp(tmp.in(bytes), start, end);
    }

    public int getOrCreateCp(int start, int end) {
        return getOrCreateCp(in, start, end);
    }

    public int getOrCreateCpForTag(int tag, long value) {
        int r = findCp(in, offsets[1] - 1, offsets[count - 1], tag, value);
        if (r != 0) {
            return r;
        }

        r = findCp(extraCp, 0, extraCp.wpos(), tag, value);
        if (r != 0) {
            return r - 1 + count;
        }

        extraCp.wu1(tag);
        switch (tagLength(tag)) {
            case 1:
                extraCp.wu1((int) value);
                break;
            case 2:
                extraCp.wu2((int) value);
                break;
            case 4:
                extraCp.wu4((int) value);
                break;
            default:
                throw new IllegalArgumentException();
        }
        return count + extraCount++;
    }

    public static int tagLength(int tag) {
        switch (tag) {
            case CONSTANT_Utf8:
                return -1; // variable-length, depends on u2 length
            case CONSTANT_Long:
            case CONSTANT_Double:
                return 8;
            case CONSTANT_Class:
            case CONSTANT_String:
            case CONSTANT_MethodType:
            case CONSTANT_Module:
            case CONSTANT_Package:
                return 2;
            case CONSTANT_Integer:
            case CONSTANT_Float:
            case CONSTANT_Fieldref:
            case CONSTANT_Methodref:
            case CONSTANT_InterfaceMethodref:
            case CONSTANT_NameAndType:
            case CONSTANT_Dynamic:
            case CONSTANT_InvokeDynamic:
                return 4;
            case CONSTANT_MethodHandle:
                return 3;
            default:
                throw new IllegalArgumentException("Class stream error: Unknown CP tag: " + tag);
        }
    }

    public static int tagSlots(int tag) {
        if (tag == CONSTANT_Long || tag == CONSTANT_Double) {
            return 2;
        }
        return 1;
    }

    public String asString(int idx) {
        int offset = offsets[idx];
        int tag = in.in[offset - 1] & 0xFF;

        switch (tag) {
            case CONSTANT_Utf8: {
                int len = in.u2(offset);
                String s = new String(in.in, offset + 2, len, StandardCharsets.UTF_8);
                return "Utf8: " + s;
            }
            case CONSTANT_Integer: {
                int bits = in.u4(offset);
                return "Integer: " + bits;
            }
            case CONSTANT_Float: {
                int bits = in.u4(offset);
                float f = Float.intBitsToFloat(bits);
                return "Float: " + f;
            }
            case CONSTANT_Long: {
                long hi = (in.u4(offset) & 0xFFFFFFFFL);
                long lo = (in.u4(offset + 4) & 0xFFFFFFFFL);
                long v = (hi << 32) | lo;
                return "Long: " + v + "L";
            }
            case CONSTANT_Double: {
                long hi = (in.u4(offset) & 0xFFFFFFFFL);
                long lo = (in.u4(offset + 4) & 0xFFFFFFFFL);
                long bits = (hi << 32) | lo;
                double d = Double.longBitsToDouble(bits);
                return "Double: " + d;
            }
            case CONSTANT_Class: {
                int nameIndex = in.u2(offset);
                return "Class: #" + nameIndex;
            }
            case CONSTANT_String: {
                int stringIndex = in.u2(offset);
                return "String: #" + stringIndex;
            }
            case CONSTANT_Fieldref: {
                int classIndex = in.u2(offset);
                int natIndex = in.u2(offset + 2);
                return "Fieldref: class=#" + classIndex + ", name_and_type=#" + natIndex;
            }
            case CONSTANT_Methodref: {
                int classIndex = in.u2(offset);
                int natIndex = in.u2(offset + 2);
                return "Methodref: class=#" + classIndex + ", name_and_type=#" + natIndex;
            }
            case CONSTANT_InterfaceMethodref: {
                int classIndex = in.u2(offset);
                int natIndex = in.u2(offset + 2);
                return "InterfaceMethodref: class=#" + classIndex + ", name_and_type=#" + natIndex;
            }
            case CONSTANT_NameAndType: {
                int nameIndex = in.u2(offset);
                int descIndex = in.u2(offset + 2);
                return "NameAndType: name=#" + nameIndex + ", descriptor=#" + descIndex;
            }
            case CONSTANT_MethodHandle: {
                int refKind = in.u1(offset);
                int refIndex = in.u2(offset + 1);
                return "MethodHandle: kind=" + refKindString(refKind) + " (" + refKind + "), ref=#" + refIndex;
            }
            case CONSTANT_MethodType: {
                int descIndex = in.u2(offset);
                return "MethodType: descriptor=#" + descIndex;
            }
            case CONSTANT_Dynamic: {
                int bsmAttrIndex = in.u2(offset);
                int natIndex = in.u2(offset + 2);
                return "Dynamic: bootstrap_method_attr=#" + bsmAttrIndex + ", name_and_type=#" + natIndex;
            }
            case CONSTANT_InvokeDynamic: {
                int bsmAttrIndex = in.u2(offset);
                int natIndex = in.u2(offset + 2);
                return "InvokeDynamic: bootstrap_method_attr=#" + bsmAttrIndex + ", name_and_type=#" + natIndex;
            }
            case CONSTANT_Module: {
                int nameIndex = in.u2(offset);
                return "Module: name=#" + nameIndex;
            }
            case CONSTANT_Package: {
                int nameIndex = in.u2(offset);
                return "Package: name=#" + nameIndex;
            }
            default:
                return "Unknown tag: " + tag;
        }
    }

    private static String refKindString(int k) {
        switch (k) {
            case 1:
                return "getField";
            case 2:
                return "getStatic";
            case 3:
                return "putField";
            case 4:
                return "putStatic";
            case 5:
                return "invokeVirtual";
            case 6:
                return "invokeStatic";
            case 7:
                return "invokeSpecial";
            case 8:
                return "newInvokeSpecial";
            case 9:
                return "invokeInterface";
            default:
                return "kind?" + k;
        }
    }

    public void rewrite() {
        in.wu2(countWPos, count + extraCount);
        int extraLength = extraCp.wpos();
        in.insert(endWPos, extraLength);
        in.w(endWPos, extraCp.out, 0, extraLength);
    }
}
