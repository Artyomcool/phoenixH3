package com.github.artyomcool.classflow;

public class FrameInfo {
    public int maxLocals;
    public int maxStack;
    public int[] locals = new int[32];
    public int[] stack = new int[32];

    public void resetArrays() {
        if (locals.length < maxLocals) {
            locals = new int[maxLocals + 32];
        }
        if (stack.length < maxStack) {
            stack = new int[maxStack + 32];
        }
    }
}
