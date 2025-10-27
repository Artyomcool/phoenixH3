package com.github.artyomcool.classflow;

public class BaseClassFlow {

    public static final byte[] CODE = "Code".getBytes();

    public static final int MEMBER_FIELD = 0;
    public static final int MEMBER_METHOD = 1;

    public static final int ATTRIBUTE_FIELD = MEMBER_FIELD;
    public static final int ATTRIBUTE_METHOD = MEMBER_METHOD;
    public static final int ATTRIBUTE_CLASS = 2;
    public static final int ATTRIBUTE_CODE = 3;

    public ByteArrayInOut inOut;

    protected final ConstantPool cp = new ConstantPool();
    protected final FrameInfo frameInfo = new FrameInfo();

    protected int currentAcc;
    protected int currentNameIdx;
    protected int currentDescIdx;
    protected int codeAttrLenPos;
    protected int codeAttrLen;
    protected int classAcc;
    protected int thisCls;
    protected int superCls;

    public void parse(ByteArrayInOut inOut) {
        this.inOut = inOut;
        cp.reset(inOut);

        onMagic(inOut.u4());

        int minor = inOut.u2();
        int major = inOut.u2();
        onClassVersion(minor, major);
        readCp();

        int access = inOut.u2();
        int thisCls = inOut.u2();
        int superCls = inOut.u2();
        onClassHeader(access, thisCls, superCls);

        readInterfaces();
        readMembers(MEMBER_FIELD);
        readMembers(MEMBER_METHOD);

        readAttributes(ATTRIBUTE_CLASS);

        if (cp.extraCount != 0) {
            rewriteCp();
        }
    }

    protected void readCp() {
        cp.countWPos = inOut.wpos();
        int cpCount = inOut.u2();
        cp.count(cpCount);
        parseCp(cpCount);
        cp.endWPos = inOut.wpos();
    }

    protected void parseCp(int count) {
        inOut.wu2(count);
        for (int i = 1; i < count; ) {
            int tag = inOut.u1();
            parseCpEntry(i, tag);
            i += ConstantPool.tagSlots(tag);
        }
    }

    protected void parseCpEntry(int i, int tag) {
        cp.offset(i, tag, inOut.pos());
        inOut.wu1(tag);
        int len = ConstantPool.tagLength(tag);
        if (len == -1) {
            len = inOut.u2();
            inOut.wu2(len);
        }
        inOut.transfer(len);
    }

    protected void readInterfaces() {
        int count = inOut.u2();
        parseInterfaces(count);
    }

    protected void readMembers(int members) {
        int count = inOut.u2();
        parseMembers(members, count);
    }

    public void readAttributes(int attribute) {
        int count = inOut.u2();
        parseAttributes(attribute, count);
    }

    protected void parseInterfaces(int count) {
        inOut.wu2(count);
        for (int i = 0; i < count; i++) {
            onInterface(i, inOut.u2());
        }
    }

    protected void onInterface(int i, int classIdx) {
        inOut.wu2(classIdx);
    }

    protected void parseMembers(int members, int count) {
        inOut.wu2(count);
        for (int i = 0; i < count; i++) {
            parseMember(members);
        }
    }

    protected void parseMember(int member) {
        int acc = inOut.u2();
        int nameIdx = inOut.u2();
        int descIdx = inOut.u2();

        parseMember(member, acc, nameIdx, descIdx);
    }

    protected void parseMember(int member, int acc, int nameIdx, int descIdx) {
        inOut.wu2(currentAcc = acc);
        inOut.wu2(currentNameIdx = nameIdx);
        inOut.wu2(currentDescIdx = descIdx);
        readAttributes(member);
    }

    protected void parseAttributes(int attributeOwner, int attributesCount) {
        inOut.wu2(attributesCount);
        for (int j = 0; j < attributesCount; j++) {
            int attrNameIdx = inOut.u2();
            int len = inOut.u4();

            parseAttribute(attributeOwner, attrNameIdx, len);
        }
    }

    protected void parseAttribute(int attributeOwner, int attrNameIdx, int len) {
        if (needParseCodeAttributes()) {
            if (attributeOwner == ATTRIBUTE_METHOD) {
                if (cp.cpEquals(attrNameIdx, CODE)) {
                    parseCode(attrNameIdx, len);
                    return;
                }
            }
        }
        inOut.wu2(attrNameIdx);
        inOut.wu4(len);
        parseUnknownAttribute(len);
    }

    protected void parseCode(int attrNameIdx, int len) {
        inOut.wu2(attrNameIdx);
        codeAttrLenPos = inOut.wpos();
        codeAttrLen = len;
        inOut.wu4(len);

        int maxStack = inOut.u2();
        int maxLocals = inOut.u2();
        onFrameInfo(maxStack, maxLocals);

        int codeLength = inOut.u4();
        onCode(codeLength);

        int exceptionsLength = inOut.u2();
        onExceptions(exceptionsLength);

        readAttributes(ATTRIBUTE_CODE);
    }

    protected void parseUnknownAttribute(int len) {
        inOut.transfer(len);
    }

    protected void onMagic(int magic) {
        if (magic != 0xCAFEBABE) {
            String msg = "Bad magic: 0x" + Integer.toHexString(magic);
            throw new IllegalArgumentException("Class stream error: " + msg);
        }
        inOut.wu4(magic);
    }

    protected void onClassVersion(int minor, int major) {
        inOut.wu2(minor);
        inOut.wu2(major);
    }

    protected void onClassHeader(int acc, int thisCls, int superCls) {
        classAcc = acc;
        this.thisCls = thisCls;
        this.superCls = superCls;
        inOut.wu2(acc);
        inOut.wu2(thisCls);
        inOut.wu2(superCls);
    }

    protected void onFrameInfo(int maxStack, int maxLocals) {
        inOut.wu2(maxStack);
        inOut.wu2(maxLocals);
        frameInfo.maxStack = maxStack;
        frameInfo.maxLocals = maxLocals;
    }

    protected void onCode(int len) {
        inOut.wu4(len);
        inOut.transfer(len);
    }

    protected void onExceptions(int count) {
        inOut.wu2(count);
        for (int i = 0; i < count; i++) {
            int startPc = inOut.u2();
            int endPc = inOut.u2();
            int handlerPc = inOut.u2();
            int catchType = inOut.u2();
            onException(startPc, endPc, handlerPc, catchType);
        }
    }

    protected void onException(int startPc, int endPc, int handlerPc, int catchType) {
        inOut.wu2(startPc);
        inOut.wu2(endPc);
        inOut.wu2(handlerPc);
        inOut.wu2(catchType);
    }

    protected void rewriteCp() {
        cp.rewrite();
    }

    protected boolean needParseCodeAttributes() {
        return false;
    }
}

