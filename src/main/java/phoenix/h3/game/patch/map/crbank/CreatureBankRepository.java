package phoenix.h3.game.patch.map.crbank;

import phoenix.h3.game.CreatureBank;
import phoenix.h3.game.common.ByteString;
import phoenix.h3.game.common.Func;
import phoenix.h3.game.patch.Index;

import java.util.Vector;

public class CreatureBankRepository extends Index<ByteString> {

    private ByteString tmp = new ByteString();

    public int indexCreatureBank(byte[] name) {
        int nextIndexIfAdded = nextIndex();
        int index = index(tmp.with(name));
        if (nextIndexIfAdded != index) {
            tmp = new ByteString();
        }
        return index + CreatureBank.PTR_CR_BANK_TABLE_INITIAL_COUNT;
    }

    public Vector<byte[]> finishCreatureBanks() {
        return finishAndTransform(new Func<ByteString, byte[]>() {
            @Override
            public byte[] f(ByteString a) {
                return a.data();
            }
        });
    }

}
