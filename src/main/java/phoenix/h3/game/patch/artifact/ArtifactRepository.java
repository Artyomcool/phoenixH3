package phoenix.h3.game.patch.artifact;

import java.util.Hashtable;
import java.util.Vector;

public class ArtifactRepository {

    public static final int FIRST_CUSTOM_ARTIFACT_ID = 146;

    public final Vector<CustomArtifact> artifacts;
    private final Hashtable<String, Integer> idByName;

    public static ArtifactRepository withCustomArtifacts(CustomArtifact... artifacts) {
        Vector<CustomArtifact> r = new Vector<>(artifacts.length);
        int id = FIRST_CUSTOM_ARTIFACT_ID;
        for (CustomArtifact artifact : artifacts) {
            r.addElement(artifact);
            artifact.id = id++;
        }

        return new ArtifactRepository(r);
    }

    private ArtifactRepository(Vector<CustomArtifact> artifacts) {
        this.artifacts = artifacts;
        idByName = new Hashtable<>(artifacts.size() * 2);

        int i = 0;
        for (int j = 0, artifactsSize = artifacts.size(); j < artifactsSize; j++) {
            CustomArtifact artifact = artifacts.get(j);
            idByName.put(artifact.name, i++);
        }
    }

    public CustomArtifact artifact(int id) {
        return artifacts.get(id - FIRST_CUSTOM_ARTIFACT_ID);
    }

    public CustomArtifact artifact(String name) {
        Integer index = idByName.get(name);
        if (index == null) {
            throw new IllegalArgumentException(
                    new StringBuffer("No such artifact installed: ").append(name).toString());
        }
        return artifacts.get(index);
    }

    public int idOf(CustomArtifact artifact) {
        return idByName.get(artifact.name) + FIRST_CUSTOM_ARTIFACT_ID;
    }

    public int totalArtifactsInGame() {
        return FIRST_CUSTOM_ARTIFACT_ID + artifacts.size();
    }
}
