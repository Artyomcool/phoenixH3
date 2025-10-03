package phoenix.h3.game.patch.replacer;

import java.util.Enumeration;
import java.util.Hashtable;

public class ReplacerRepository {

    public static ReplacerRepository withReplacers(Replacer... replacers) {
        Hashtable<String, Replacer> r = new Hashtable<>(replacers.length * 2);
        for (Replacer replacer : replacers) {
            r.put(replacer.name, replacer);
        }
        return new ReplacerRepository(r);
    }

    private final Hashtable<String, Replacer> replacers;

    private ReplacerRepository(Hashtable<String, Replacer> replacers) {
        this.replacers = replacers;
    }

    public void performReplace(String[] tokens, int x, int y, int z, int cell, int typeAndSubtype, int event) {
        String name = tokens[0];
        Replacer replacer = replacers.get(name);
        if (replacer == null) {
            throw new IllegalArgumentException(new StringBuffer("Unknown replacer: ").append(name).toString());
        }
        replacer.performReplace(tokens, x, y, z, cell, typeAndSubtype, event);
    }

    public Replacer[] allReplacers() {
        Replacer[] r = new Replacer[replacers.size()];
        Enumeration<Replacer> elements = replacers.elements();
        int i = 0;
        while (elements.hasMoreElements()) {
            r[i++] = elements.nextElement();
        }
        return r;
    }
}
