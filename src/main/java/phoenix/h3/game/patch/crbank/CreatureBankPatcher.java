package phoenix.h3.game.patch.crbank;

import phoenix.h3.game.CreatureBank;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.stdlib.StdString;

import static phoenix.h3.game.stdlib.Memory.*;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class CreatureBankPatcher extends Patcher {

    private final CreatureBankRepository banks;

    public CreatureBankPatcher(CreatureBankRepository banks) {
        this.banks = banks;
    }

    @Override
    public void onGameCreated() {
        patchCrBankTable();
    }

    private void patchCrBankTable() {
        int ptrCrBankTable = CreatureBank.PTR_CR_BANK_TABLE;
        int oldCount = CreatureBank.PTR_CR_BANK_TABLE_INITIAL_COUNT;
        int recordSize = CreatureBank.PTR_CR_BANK_TABLE_RECORD_SIZE;

        CreatureBankData[] data = banks.finish();

        int prevFrameArray = dwordAt(ptrCrBankTable);

        int newFrameArray = malloc((oldCount + data.length) * recordSize);
        memcpy(newFrameArray, prevFrameArray, oldCount * recordSize);
        putDword(ptrCrBankTable, newFrameArray);

        int ptr = newFrameArray + oldCount * recordSize;
        for (CreatureBankData d : data) {
            StdString.put(ptr, d.name);
            ptr += recordSize;
        }
    }

}
