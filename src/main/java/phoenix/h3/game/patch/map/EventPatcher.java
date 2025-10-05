package phoenix.h3.game.patch.map;

import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.*;
import phoenix.h3.game.common.CustomMarker;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.patch.map.replacer.Replacer;
import phoenix.h3.game.patch.map.replacer.ReplacerRepository;
import phoenix.h3.game.stdlib.Stack;
import phoenix.h3.game.stdlib.StdString;

import java.util.Vector;

import static phoenix.h3.annotations.R.EBP;
import static phoenix.h3.annotations.R.ESP;

public class EventPatcher extends Patcher.Stateless {

    private static class ReplacementInfo {
        private final int x;
        private final int y;
        private final int z;
        private final String[] tokens;
        private final int event;

        private ReplacementInfo(int x, int y, int z, String[] tokens, int event) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.tokens = tokens;
            this.event = event;
        }
    }

    private final Vector<ReplacementInfo> replacers = new Vector<>();
    private final ReplacerRepository repository;

    public EventPatcher(ReplacerRepository repository) {
        this.repository = repository;
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
        Vector<Vector<String>> customs = CustomMarker.parseForCustomMarkerAndFixup(msg);
        if (customs == null) {
            return;
        }

        int coords = CellInfo.coords(cellInfo);
        int x = coords & 0xff;
        int y = (coords >>> 8) & 0xff;
        int z = (coords >>> 16) & 0xff;
        for (int i = 0; i < customs.size(); i++) {
            Vector<String> tokens = customs.get(i);
            if (tokens.get(0).equals("REPLACE")) {
                if (tokens.get(1).equals("LEFT")) {
                    String[] tok = new String[tokens.size() - 2];
                    for (int j = 0; j < tok.length; j++) {
                        tok[j] = tokens.get(j + 2);
                    }
                    replacers.add(new ReplacementInfo(x - 1, y, z, tok, event));
                }
            }
        }
        Stack.putEax(esp, 0);
    }

    @Override
    protected void onGameStarted() {
        super.onGameStarted();
        int game = Game.instance();
        int map = Game.map(game);
        int cells = NewfullMap.cells(map);
        int size = NewfullMap.size(map);

        repository.init(game, map, cells, size);
        for (int i = 0; i < replacers.size(); i++) {
            ReplacementInfo info = replacers.get(i);

            int cell = NewfullMap.cell(cells, size, info.x, info.y, info.z);

            int typeAndSubtype = NewmapCell.typeAndSubtype(cell);
            if (typeAndSubtype == 0) {
                throw new IllegalStateException(new StringBuffer("No objects to replace at ")
                        .append(info.x)
                        .append(':')
                        .append(info.y)
                        .append(':')
                        .append(info.z)
                        .toString()
                );
            }

            repository.performReplace(info.tokens, info.x, info.y, info.z, cell, typeAndSubtype,  info.event);
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
