package phoenix.h3.game.stdlib;

import static phoenix.h3.game.stdlib.Memory.putDword;

public class Stack {

    public static void putEax(int esp, int eax) {
        putDword(esp - 4, eax);
    }

    public static void putEcx(int esp, int ecx) {
        putDword(esp - 8, ecx);
    }

    public static void putEdx(int esp, int edx) {
        putDword(esp - 12, edx);
    }

    public static void putEbx(int esp, int ebx) {
        putDword(esp - 16, ebx);
    }

    public static void putEsp(int esp, int espV) {
        putDword(esp - 20, espV);
    }

    public static void putEbp(int esp, int ebp) {
        putDword(esp - 24, ebp);
    }

    public static void putEsi(int esp, int esi) {
        putDword(esp - 28, esi);
    }

    public static void putEdi(int esp, int edi) {
        putDword(esp - 32, edi);
    }

    public static void markSkip(int esp, int popStack) {
        putDword( esp - 36, 1);
        putEsp(esp, popStack);
    }
}
