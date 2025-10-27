package phoenix.h3.game.common;

import java.util.Vector;

public class Vectors {

    public static <T> T[] toArray(Vector<T> vector, T[] out) {
        for (int i = 0, outLength = out.length; i < outLength; i++) {
            out[i] = vector.get(i);
        }
        return out;
    }

}
