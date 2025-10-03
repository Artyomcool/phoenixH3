package phoenix.h3.game.patch.artifact;

import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.Def;
import phoenix.h3.game.Hero;
import phoenix.h3.game.patch.OwnResourceCache;
import phoenix.h3.game.patch.Patcher;

import java.util.Vector;

import static phoenix.h3.annotations.R.*;
import static phoenix.h3.game.patch.artifact.ArtifactRepository.FIRST_CUSTOM_ARTIFACT_ID;
import static phoenix.h3.game.stdlib.Memory.*;

public class CustomArtifactPatcher extends Patcher {

    public static final int ARTIFACT_TABLE = 0x660B68;

    private final ArtifactRepository artifacts;
    private final OwnResourceCache ownCache;

    public CustomArtifactPatcher(ArtifactRepository artifacts, OwnResourceCache ownCache) {
        //todo bug in cldc hi? super(DefPatcher.class, DefFrameDlgItemPatcher.class);
        this.artifacts = artifacts;
        this.ownCache = ownCache;
    }

    @Override
    public void onGameCreated() {
        super.onGameCreated();
        int artifactDef = loadArtifactDef();
        int[] defsOffsets = preloadDefs();
        for (int i = 0; i < defsOffsets.length; i++) {
            ownCache.patchFrame(artifactDef, 0, FIRST_CUSTOM_ARTIFACT_ID + i, defsOffsets[i]);
        }
        patchArtifactsDef(artifactDef, defsOffsets);
        patchArtifactsTable();
    }

    // todo move to hero patcher
    @Upcall(base = 0x4E2DEE)
    public void onAddArtifactBonusesToHero(@R(ESP) int esp, @R(EDI) int hero, @R(EAX) int primarySkill, @R(ECX) int artifactId) {
        if (artifactId >= FIRST_CUSTOM_ARTIFACT_ID) {
            // disable original bonuses
            putDword(esp - 8, 0); // ecx
            // use our bonus
            int[] bonuses = artifacts.artifact(artifactId).bonuses;
            Hero.putPrimarySkill(hero, primarySkill, Hero.getPrimarySkill(hero, primarySkill) + bonuses[primarySkill]);
        }
    }

    // todo move to hero patcher
    @Upcall(base = 0x4E2F79)
    public void onRemoveArtifactBonusesFromHero(@R(ESP) int esp, @R(EDI) int hero, @R(ESI) int artifactId) {
        if (artifactId >= FIRST_CUSTOM_ARTIFACT_ID) {
            // disable original bonuses
            putDword(esp - 28, 0); // esi
            // use our bonus
            int[] bonuses = artifacts.artifact(artifactId).bonuses;
            for (int i = 0; i < 4; i++) {
                Hero.putPrimarySkill(hero, i, Hero.getPrimarySkill(hero, i) - bonuses[i]);
            }
        }
    }

    int lastArtifactId;
    // todo move to hero patcher
    @Upcall(base = 0x04336CD)
    public void onHeroArtGetValue(@R(ESP) int esp, @R(ECX) int artifactId) {
        lastArtifactId = artifactId;
        if (artifactId >= FIRST_CUSTOM_ARTIFACT_ID) {
            // disable original value
            putDword(esp - 8, -1); // ecx
        }
    }
//  todo, clashes
//    @Upcall(base = 0x4336DA)
//    public void onHeroArtGetValueExit(@R(ESP) int esp) {
//        if (lastArtifactId >= FIRST_CUSTOM_ARTIFACT_ID) {
//            // probably need to fix in case of victory conditions
//            putDword(esp - 4, artifacts.artifact(lastArtifactId).value); // eax
//        }
//    }

    private static int loadArtifactDef() {
        int tmpName = SHARED_TMP_NATIVE_BUFFER + 1024;
        putCstr(tmpName, "artifact.def");
        return Def.getByName(tmpName);
    }

    private int[] preloadDefs() {
        Vector<CustomArtifact> customArtifacts = artifacts.artifacts;
        int[] loadedDefsOffsets = new int[customArtifacts.size()];
        for (int i = 0, customArtifactsSize = customArtifacts.size(); i < customArtifactsSize; i++) {
            CustomArtifact artifact = customArtifacts.get(i);
            int def = Def.loadFromJar(artifact.defName);
            loadedDefsOffsets[i] = def;
            ownCache.register(artifact.defName, def);
        }
        return loadedDefsOffsets;
    }

    private void patchArtifactsDef(int artifactDef, int[] singleFrameDefs) {
        int oldArtifactsCount = FIRST_CUSTOM_ARTIFACT_ID;
        int totalArtifactsInGame = artifacts.totalArtifactsInGame();
        Def.patchArtifactsDef(artifactDef, oldArtifactsCount, totalArtifactsInGame, singleFrameDefs);
    }

    private void patchArtifactsTable() {
        int artifactRecordSize = 0x20;
        int customArtifactsCount = artifacts.artifacts.size();
        int extraSize = artifactRecordSize * customArtifactsCount;
        int oldArtifactsCount = FIRST_CUSTOM_ARTIFACT_ID;
        int oldArtifactsSize = artifactRecordSize * oldArtifactsCount;
        int oldTable = dwordAt(ARTIFACT_TABLE);
        int newTable = malloc(oldArtifactsSize + extraSize);
        memcpy(newTable, oldTable, oldArtifactsSize);

        int[] artifactRecord = SHARED_TMP_INT_BUFFER;
        for (int i = 0; i < customArtifactsCount; i++) {
            CustomArtifact artifact = artifacts.artifacts.get(i);
            int bytesForName = artifact.nameText.length() + 1;
            int bytesForDesc = artifact.descText.length() + 1;
            int nameAndDesc = malloc(bytesForName + bytesForDesc);
            int descOffset = nameAndDesc + bytesForName;
            putCstr(nameAndDesc, artifact.nameText);
            putCstr(descOffset, artifact.descText);
            artifactRecord[0] = nameAndDesc;
            artifactRecord[1] = artifact.cost;
            artifactRecord[2] = artifact.slot;
            artifactRecord[3] = artifact.rarity;
            artifactRecord[4] = descOffset;
            artifactRecord[5] = -1; // todo artifact.composite;
            artifactRecord[6] = -1; // todo artifact.part;
            artifactRecord[7] = 0;  // todo disabled + spell + paddings
            putArray(newTable + oldArtifactsSize + artifactRecordSize * i, artifactRecord, 0, artifactRecordSize);
        }

        putDword(ARTIFACT_TABLE, newTable);
    }
}
