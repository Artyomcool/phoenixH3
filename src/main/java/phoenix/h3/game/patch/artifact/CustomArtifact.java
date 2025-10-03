package phoenix.h3.game.patch.artifact;

public abstract class CustomArtifact {

    public static final int SLOT_SHIELD = 5;
    public static final int SLOT_RING = 7;
    public static final int SLOT_BOOTS = 8;
    public static final int SLOT_MISC = 9;

    public static final int COST_TREASURE = 1000;
    public static final int COST_RELIC = 20000; // TODO actually i don't know the correct value

    public static final int RARITY_TREASURE = 0;
    public static final int RARITY_MINOR = 1;
    public static final int RARITY_MAJOR = 2;
    public static final int RARITY_RELIC = 3;

    public static final int PRIMARY_ATTACK = 0;
    public static final int PRIMARY_DEFENSE = 1;
    public static final int PRIMARY_POWER = 2;
    public static final int PRIMARY_KNOWLEDGE = 3;

    public int id;
    public final String name = createName();
    public final String defName = createDefName();

    public final String nameText;   // TODO localization?
    public final String descText;   // TODO localization?
    public final int cost;
    public final int slot;
    public final int rarity;

    public final int[] bonuses = new int[4];
    public int value = 0;

    protected CustomArtifact(String nameText, String descText, int cost, int slot, int rarity) {
        this.nameText = nameText;
        this.descText = descText;
        this.cost = cost;
        this.slot = slot;
        this.rarity = rarity;
    }

    protected String createName() {
        String n = getClass().getName();
        return n.substring(n.lastIndexOf('.') + 1);
    }

    protected String createShortName() {
        char[] firstLetters = new char[3];
        int c = 0;
        int length = name.length();
        for (int i = 0; i < length; i++) {
            char letter = name.charAt(i);
            if (letter >= 'A' && letter <= 'Z') {
                firstLetters[c++] = (char)(letter - 'A' + 'a');
                if (c == firstLetters.length) {
                    break;
                }
            }
        }

        return new String(firstLetters, 0, c);
    }

    protected String createDefName() {
        return new StringBuffer("artifact.").append(createShortName()).toString();
    }
}
