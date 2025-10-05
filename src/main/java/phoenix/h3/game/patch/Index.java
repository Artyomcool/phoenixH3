package phoenix.h3.game.patch;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class Index<T> {

    private Hashtable<T, Integer> index = new Hashtable<>();

    public int index(T str) {
        if (index == null) {
            throw new IllegalStateException("Already finished");
        }

        Integer r = index.get(str);
        if (r != null) {
            return r;
        }

        int nextIndex = index.size();
        index.put(str, nextIndex);
        return nextIndex;
    }

    public Vector<T> finish() {
        if (index == null) {
            throw new IllegalStateException("Already finished");
        }

        int size = index.size();

        Vector<T> r = new Vector<>(size);
        r.setSize(size);

        Enumeration<T> keys = index.keys();
        Enumeration<Integer> values = index.elements();

        while (keys.hasMoreElements()) {
            r.set(values.nextElement(), keys.nextElement());
        }

        index = null;
        return r;
    }

}
