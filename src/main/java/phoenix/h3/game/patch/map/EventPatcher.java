package phoenix.h3.game.patch.map;

import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.*;
import phoenix.h3.game.common.CustomMarker;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.patch.artifact.ArtifactRepository;
import phoenix.h3.game.patch.artifact.CustomArtifact;
import phoenix.h3.game.patch.map.replacer.Replacer;
import phoenix.h3.game.patch.map.replacer.ReplacerRepository;
import phoenix.h3.game.stdlib.Memory;
import phoenix.h3.game.stdlib.Stack;
import phoenix.h3.game.stdlib.StdString;

import java.util.Hashtable;
import java.util.Vector;

import static phoenix.h3.annotations.R.*;
import static phoenix.h3.game.TypeObscuringObject.*;

public class EventPatcher extends Patcher.Stateless {

    private static class ReplacementInfo {
        private final int x;
        private final int y;
        private final int z;
        private final CustomMarker.Value info;
        private final int event;

        private ReplacementInfo(int x, int y, int z, CustomMarker.Value info, int event) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.info = info;
            this.event = event;
        }
    }

    private final ArtifactRepository artifacts;
    private final Vector<ReplacementInfo> replacers = new Vector<>();
    private final Hashtable<Integer, CustomMarker.Value> allowsByCoords = new Hashtable<>();    // todo saved state
    private final ReplacerRepository repository;

    public EventPatcher(ArtifactRepository artifacts, ReplacerRepository repository) {
        this.artifacts = artifacts;
        this.repository = repository;
    }

    @Override
    public void installPatch() {
        super.installPatch();
        //patchDword(0x4CF67A, 0x81ffA000);   // up to 160 arts
    }

    @Upcall(base = 0x4CF682)
    public void onArtifactToBigThrowError(@R(ESP) int esp) {
        Stack.markSkip(esp, 0);
    }

    @Upcall(base = 0x5062B7)
    public void onEventCellAssign(
            @R(ESP) int esp,
            @Dword(at = EBP, offset = -0xC) int cellInfo,
            @Dword(at = EBP, offset = -0x1C) int map
    ) {
        int eventId = CellInfo.eventId(cellInfo);
        int events = NewfullMap.blackboxListData(map);
        int event = events + eventId * Blackbox.SIZE;

        StdString msg = Blackbox.msg(event);
        CustomMarker.Value customs = CustomMarker.parseForCustomMarkerAndFixup(msg);
        if (customs == null) {
            return;
        }

        int coords = CellInfo.coords(cellInfo);
        int x = coords & 0xff;
        int y = (coords >>> 8) & 0xff;
        int z = (coords >>> 16) & 0xff;
        CustomMarker.Value v = customs.val("replace");
        if (v != null) {
            replacers.add(new ReplacementInfo(
                    x + customs.integer("dx", 0),
                    y + customs.integer("dy", 0),
                    z,
                    v,
                    event
            ));
            Stack.putEax(esp, 0);
        }

        CustomMarker.Value allow = customs.val("allow");
        CustomMarker.Value give = customs.val("give");
        if (allow != null) {
            allowsByCoords.put(coords, allow);
        }
        if (give != null) {
            String artifact = give.ascii("artifact");
            if (artifact != null) {
                CustomArtifact a = artifacts.artifact(artifact);
                Blackbox.replaceArtifact(event, 0, a.id);
            }
        }
    }

    boolean skipEvent;
    @Upcall(base = 0x480AD2)
    public void onEvent(@R(ESP) int esp, @R(ESI) int hero, @Dword(at = ESI, offset = OFFSET_MAP_X) int xy,  @Dword(at = ESI, offset = OFFSET_MAP_Z) int zv) {
        skipEvent = false;

        int x = xy & 0xffff;
        int y = xy >>> 16;
        int z = zv & 0xffff;

        int coords = x | y << 8 | z << 16; // todo z?
        CustomMarker.Value value = allowsByCoords.get(coords);
        if (value == null) {
            return;
        }

        String hasArtifact = value.ascii("hasArtifact");
        if (hasArtifact != null) {
            CustomArtifact a = artifacts.artifact(hasArtifact);
            if (Hero.hasArtifact(hero, a.id) == 0) {
                Stack.putEax(esp, 0);
                skipEvent = true;
            }
        }
    }
//
//    @Upcall(base = 0x48051B)
//    public void onAfterEvent(@R(ESP) int esp) {
//        if (skipEvent) {
//            skipEvent = false;
//            Stack.putEax(esp, Integer.MAX_VALUE);
//        }
//    }

    @Override
    protected void onGameStarted() {
        super.onGameStarted();
        int game = Game.instance();
        int map = Game.map(game);
        int cells = NewfullMap.cells(map);
        int size = NewfullMap.size(map);

        repository.init(game, map, cells, size);
        for (ReplacementInfo info : replacers) {
            int cell = NewfullMap.cell(cells, size, info.x, info.y, info.z);

            int typeAndSubtype = NewmapCell.typeAndSubtype(cell);
            if (typeAndSubtype == 0) {
                throw new IllegalStateException("No objects to replace at " +
                        info.x +
                        ':' +
                        info.y +
                        ':' +
                        info.z
                );
            }

            repository.performReplace(info.info, info.x, info.y, info.z, cell, typeAndSubtype, info.event);
        }
    }

    @Override
    public Vector<Patcher<?>> createChildPatches() {
        Vector<Patcher<?>> r = new Vector<>();
        for (Replacer replacer : repository.allReplacers()) {
            Patcher<?> patcher = replacer.asPatcher();
            if (patcher != null) {
                r.add(patcher);
            }
        }
        return r;
    }
}
