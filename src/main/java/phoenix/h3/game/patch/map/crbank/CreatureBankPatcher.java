package phoenix.h3.game.patch.map.crbank;

import phoenix.h3.game.CreatureBank;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.stdlib.StdString;

import java.util.Vector;

import static phoenix.h3.game.stdlib.Memory.mallocAuto;
import static phoenix.h3.game.stdlib.Memory.putCstr;

public class CreatureBankPatcher extends Patcher<Vector<byte[]>> {

    private final CreatureBankRepository banks;

    public CreatureBankPatcher(CreatureBankRepository banks) {
        this.banks = banks;
    }

    @Override
    protected Vector<byte[]> createInitialSaveData() {
        return banks.finishCreatureBanks();
    }

    @Override
    protected void onNewGameStarted(Vector<byte[]> names) {
        patchCrBankTable();
    }

    @Override
    protected void onSaveGameLoaded(Vector<byte[]> names) {
        for (byte[] name : names) {
            banks.indexCreatureBank(name);
        }
        banks.finishCreatureBanks();
        patchCrBankTable();
    }

    private void patchCrBankTable() {
        int ptrCrBankTable = CreatureBank.PTR_CR_BANK_TABLE;
        int oldCount = CreatureBank.PTR_CR_BANK_TABLE_INITIAL_COUNT;
        int recordSize = CreatureBank.PTR_CR_BANK_TABLE_RECORD_SIZE;

        int oldSize = oldCount * recordSize;
        int newFrameArray = patchArray(ptrCrBankTable, oldSize, (oldCount + savedData.size()) * recordSize);

        int ptr = newFrameArray + oldSize;
        for (byte[] name : savedData) {
            int cstr = mallocAuto(name.length + 1);
            putCstr(cstr, name);
            StdString.put(ptr, cstr, name.length);
            ptr += recordSize;
        }
    }

}
