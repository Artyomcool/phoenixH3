package phoenix.h3.game.patch.crbank;

import phoenix.h3.game.CreatureBank;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.stdlib.StdString;

import static phoenix.h3.game.stdlib.Memory.mallocAuto;
import static phoenix.h3.game.stdlib.Memory.putCstr;

public class CreatureBankPatcher extends Patcher {

    private final CreatureBankRepository banks;

    public CreatureBankPatcher(CreatureBankRepository banks) {
        this.banks = banks;
    }

    @Override
    public void onGameCreated(boolean saveLoad) {
        patchCrBankTable();
    }

    private void patchCrBankTable() {
        int ptrCrBankTable = CreatureBank.PTR_CR_BANK_TABLE;
        int oldCount = CreatureBank.PTR_CR_BANK_TABLE_INITIAL_COUNT;
        int recordSize = CreatureBank.PTR_CR_BANK_TABLE_RECORD_SIZE;

        CreatureBankData[] data = banks.finish();

        int oldSize = oldCount * recordSize;
        int newFrameArray = patchArray(ptrCrBankTable, oldSize, (oldCount + data.length) * recordSize);

        int ptr = newFrameArray + oldSize;
        for (CreatureBankData d : data) {
            int cstr = mallocAuto(d.name.length() + 1);
            putCstr(cstr, d.name);
            StdString.put(ptr, cstr, d.name.length());
            ptr += recordSize;
        }
    }

}
