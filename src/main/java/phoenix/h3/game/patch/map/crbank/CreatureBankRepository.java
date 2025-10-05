package phoenix.h3.game.patch.map.crbank;

import phoenix.h3.game.CreatureBank;
import phoenix.h3.game.patch.Index;

public class CreatureBankRepository extends Index<String> {

    public int indexCreatureBank(String name) {
        return index(name) + CreatureBank.PTR_CR_BANK_TABLE_INITIAL_COUNT;
    }
}
