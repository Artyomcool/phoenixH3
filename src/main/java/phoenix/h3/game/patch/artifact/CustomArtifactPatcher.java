package phoenix.h3.game.patch.artifact;

import phoenix.h3.annotations.Dword;
import phoenix.h3.annotations.R;
import phoenix.h3.annotations.Upcall;
import phoenix.h3.game.Def;
import phoenix.h3.game.Hero;
import phoenix.h3.game.patch.OwnResourceCache;
import phoenix.h3.game.patch.Patcher;
import phoenix.h3.game.stdlib.Stack;

import java.util.Vector;

import static phoenix.h3.annotations.R.*;
import static phoenix.h3.game.patch.artifact.ArtifactRepository.FIRST_CUSTOM_ARTIFACT_ID;
import static phoenix.h3.game.stdlib.Memory.*;

public class CustomArtifactPatcher extends Patcher.Stateless {

    public static final int ARTIFACT_TABLE = 0x660B68;

    private final ArtifactRepository artifacts;
    private final OwnResourceCache ownCache;

    public CustomArtifactPatcher(ArtifactRepository artifacts, OwnResourceCache ownCache) {
        //todo add support of class literals super(DefPatcher.class, DefFrameDlgItemPatcher.class);
        this.artifacts = artifacts;
        this.ownCache = ownCache;
    }

    @Override
    public void onGameStarted() {
        int artifactDef = loadArtifactDef();
        int[] defsOffsets = preloadDefs();
        for (int i = 0; i < defsOffsets.length; i++) {
            ownCache.patchFrame(artifactDef, 0, FIRST_CUSTOM_ARTIFACT_ID + i, defsOffsets[i]);
        }
        patchArtifactsDef(artifactDef, defsOffsets);
        patchArtifactsTable();
    }

    @Upcall(base = 0x4D97C1)
    public void onSpellMaskEvaluate(@R(EBP) int ebp, @Dword(at = EBP, offset = -4) int artId) {
        CustomArtifact artifact = artifacts.artifact(artId);
        if (artifact != null && artifact.spell != 0) {
            int result = ebp - 0x1C;
            putByte(result + artifact.spell / 8, 1 << (artifact.spell % 8));
        }
    }

    // todo move to hero patcher
    @Upcall(base = 0x4E2DEE)
    public void onAddArtifactBonusesToHero(@R(ESP) int esp, @R(EDI) int hero, @R(EAX) int primarySkill, @R(ECX) int artifactId) {
        if (artifactId >= FIRST_CUSTOM_ARTIFACT_ID) {
            // disable original bonuses
            Stack.putEcx(esp, 0);
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
            Stack.putEsi(esp, 0);
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
            Stack.putEcx(esp, -1);
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

    public static int loadArtifactDef() {
        int tmpName = malloc(16);
        putCstr(tmpName, "artifact.def".getBytes());
        int artifacts = Def.getByName(tmpName);
        free(tmpName);
        return artifacts;
    }

    private int[] preloadDefs() {
        Vector<CustomArtifact> customArtifacts = artifacts.artifacts;
        int[] loadedDefsOffsets = new int[customArtifacts.size()];
        for (int i = 0, customArtifactsSize = customArtifacts.size(); i < customArtifactsSize; i++) {
            CustomArtifact artifact = customArtifacts.get(i);
            loadedDefsOffsets[i] = ownCache.customDef(artifact.defName);
        }
        return loadedDefsOffsets;
    }

    private void patchArtifactsDef(int artifactDef, int[] singleFrameDefs) {
        int oldArtifactsCount = FIRST_CUSTOM_ARTIFACT_ID;
        int totalArtifactsInGame = artifacts.totalArtifactsInGame();
        patchArtifactsDef(artifactDef, oldArtifactsCount, totalArtifactsInGame, singleFrameDefs);
    }


    private void patchArtifactsDef(int artifactDef, int oldArtifactsCount, int totalArtifactsInGame, int[] singleFrameDefs) {
        int prevGroupArray = dwordAt(artifactDef + Def.OFFSET_GROUPS_ARRAY);
        int prevGroup = dwordAt(prevGroupArray);

        int framesCountAddress = prevGroup + Def.Group.OFFSET_FRAMES_COUNT;
        int framesMaxCountAddress = prevGroup + Def.Group.OFFSET_MAX_FRAMES_COUNT;
        int framesAddress = prevGroup + Def.Group.OFFSET_FRAMES;

        patchDword(framesCountAddress, totalArtifactsInGame);
        patchDword(framesMaxCountAddress, totalArtifactsInGame);
        int newFrameArray = patchArray(framesAddress, oldArtifactsCount * 4, totalArtifactsInGame * 4);

        int nextFrameAddress = newFrameArray + oldArtifactsCount * 4;
        for (int i = 0; i < singleFrameDefs.length; i++) {
            int groups = dwordAt(singleFrameDefs[i] + Def.OFFSET_GROUPS_ARRAY);
            int group = dwordAt(groups);
            int frames = dwordAt(group + Def.Group.OFFSET_FRAMES);
            int frame = dwordAt(frames);
            putDword(nextFrameAddress + i * 4, frame);
        }
    }

    private void patchArtifactsTable() {
        int artifactRecordSize = 0x20;
        int customArtifactsCount = artifacts.artifacts.size();
        int extraSize = artifactRecordSize * customArtifactsCount;
        int oldArtifactsCount = FIRST_CUSTOM_ARTIFACT_ID;
        int oldArtifactsSize = artifactRecordSize * oldArtifactsCount;
        int newTable = patchArray(ARTIFACT_TABLE, oldArtifactsSize, oldArtifactsSize + extraSize);

        int[] artifactRecord = SHARED_TMP_INT_BUFFER;
        for (int i = 0; i < customArtifactsCount; i++) {
            CustomArtifact artifact = artifacts.artifacts.get(i);
            int bytesForName = artifact.nameText.length() + 1;
            int bytesForDesc = artifact.descText.length() + 1;
            int nameAndDesc = mallocAuto(bytesForName + bytesForDesc);
            int descOffset = nameAndDesc + bytesForName;
            putCstr(nameAndDesc, artifact.nameText.getBytes());
            putCstr(descOffset, artifact.descText.getBytes());
            artifactRecord[0] = nameAndDesc;
            artifactRecord[1] = artifact.cost;
            artifactRecord[2] = artifact.slot;
            artifactRecord[3] = artifact.rarity;
            artifactRecord[4] = descOffset;
            artifactRecord[5] = -1; // todo artifact.composite;
            artifactRecord[6] = -1; // todo artifact.part;
            artifactRecord[7] = artifact.spell << 8;  // todo disabled + spell
            putArray(newTable + oldArtifactsSize + artifactRecordSize * i, artifactRecord, 0, artifactRecordSize);
        }

        putDword(ARTIFACT_TABLE, newTable);
    }
}
