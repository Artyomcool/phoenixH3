package phoenix.h3.game.patch.replacer;

public abstract class Replacer {

    public final String name = createName();

    public abstract void performReplace(String[] tokens, int x, int y, int z, int cell, int typeAndSubtype, int event);

    // todo reuse?
    protected String createName() {
        String n = getClass().getName();
        return n.substring(n.lastIndexOf('.') + 1);
    }
}
