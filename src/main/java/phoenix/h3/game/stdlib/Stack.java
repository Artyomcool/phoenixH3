package phoenix.h3.game.stdlib;

import static phoenix.h3.game.stdlib.Memory.putDword;

public class Stack {

    public static void putEax(int esp, int eax) {
        putDword(esp - 4, eax);
    }

    public static void putEcx(int esp, int ecx) {
        putDword(esp - 8, ecx);
    }

}
