package phoenix.h3.game.patch;

import phoenix.h3.annotations.Arg;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;

import static phoenix.h3.annotations.R.*;
import static phoenix.h3.game.stdlib.Memory.putDword;

public class DefPatcher extends Patcher.Stateless {

    private final OwnResourceCache ownCache;

    public DefPatcher(OwnResourceCache ownCache) {
        this.ownCache = ownCache;
    }

    @Upcall(base = 0x47B820)
    public void onInterfaceDrawFrame(@R(ESP) int esp, @R(ECX) int _this, @Arg(1) int frameIndex) {
        int replacement = ownCache.findPatchedFrame(_this, 0, frameIndex);
        if (replacement != 0) {
            putDword(esp - 8, replacement); // ecx
            putDword(esp + 8, 0); // arg 1
        }
    }

    @Upcall(base = 0x47B7D0)
    public void onPointerDrawFrame(@R(ESP) int esp, @R(ECX) int _this, @Arg(1) int frameIndex) {
        int replacement = ownCache.findPatchedFrame(_this, 0, frameIndex);
        if (replacement != 0) {
            putDword(esp - 8, replacement); // ecx
            putDword(esp + 8, 0); // arg 1
        }
    }

    @Upcall(base = 0x47B610)
    public void onSpriteDrawFrame(@R(ESP) int esp, @R(ECX) int _this, @Arg(1) int groupIndex, @Arg(2) int frameIndex) {
        int replacement = ownCache.findPatchedFrame(_this, groupIndex, frameIndex);
        if (replacement != 0) {
            putDword(esp - 8, replacement); // ecx
            putDword(esp + 8, 0); // arg 1
            putDword(esp + 12, 0); // arg 2
        }
    }
}
