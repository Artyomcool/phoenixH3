package phoenix.h3.game.patch.crbank;

import phoenix.h3.game.CreatureBank;

import java.util.Enumeration;
import java.util.Hashtable;

public class CreatureBankRepository {
    private Hashtable<String, CreatureBankData> banks = new Hashtable<>();

    public CreatureBankData getOrCreate(String name) {
        if (banks == null) {
            throw new IllegalStateException("Banks already finalized");
        }
        CreatureBankData data = banks.get(name);
        if (data == null) {
            banks.put(name, data = new CreatureBankData(CreatureBank.PTR_CR_BANK_TABLE_INITIAL_COUNT + banks.size(), name));
        }
        return data;
    }

    public CreatureBankData[] finish() {
        CreatureBankData[] result = new CreatureBankData[banks.size()];
        Enumeration<CreatureBankData> elements = banks.elements();
        while (elements.hasMoreElements()) {
            CreatureBankData data = elements.nextElement();
            result[data.id - CreatureBank.PTR_CR_BANK_TABLE_INITIAL_COUNT] = data;
        }

        banks = null;
        return result;
    }
}
