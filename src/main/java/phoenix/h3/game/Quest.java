package phoenix.h3.game;

import phoenix.h3.game.stdlib.StdString;

public class Quest {

    public static StdString startText(int quest) {
        return StdString.tmp.readFrom(quest + 8);
    }

}
