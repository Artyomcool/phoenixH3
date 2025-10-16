package phoenix.image;

import java.util.Vector;

public class HMap {

    public MapHeader header;
    public Artifacts artefacts;
    public Spells spells;
    public SecSkills secSkills;
    public Rumors rumors;

    public static HMap read(HeroStream in) {
        HMap r = new HMap();
        r.header = MapHeader.read(in);
        r.artefacts = Artifacts.read(in);
        r.spells = Spells.read(in);
        r.secSkills = SecSkills.read(in);
        r.rumors = Rumors.read(in);
        return r;
    }

    public static class MapHeader {
        public int format;          // 4 bytes: 0x0E RoE, 0x15 AB, 0x1C SoD
        public int hasHero;         // 1 byte: 0x01 if at least one hero
        public int size;            // 4 bytes: width/height of the map (square)
        public int twoLevel;        // 1 byte: 0 = single-level, 1 = two-level
        public byte[] name;         // variable-length, prefixed with 4-byte length
        public byte[] description;  // variable-length, prefixed with 4-byte length
        public int difficulty;      // 1 byte: 0–4
        public int levelLimit;      // 1 byte: 0–4
        public PlayerAttributes[] playerAttributes = new PlayerAttributes[8];
        public AvailableHeroes availableHeroes;
        public SpecialVictoryCondition specialVictoryCondition;
        public SpecialLossCondition specialLossCondition;
        public Teams teams;
        public FreeHeroes freeHeroes;

        public static MapHeader read(HeroStream in) {
            MapHeader h = new MapHeader();
            h.format = in.u32();
            h.hasHero = in.u8();
            h.size = in.u32();
            h.twoLevel = in.u8();
            h.name = in.readString32();
            h.description = in.readString32();
            h.difficulty = in.u8();
            h.levelLimit = in.u8();
            for (int i = 0; i < h.playerAttributes.length; i++) {
                h.playerAttributes[i] = PlayerAttributes.read(in);
            }
            h.specialVictoryCondition = SpecialVictoryCondition.read(in);
            h.specialLossCondition = SpecialLossCondition.read(in);
            h.teams = Teams.read(in);
            h.freeHeroes = FreeHeroes.read(in);
            in.skip(31);
            return h;
        }

        public static class PlayerAttributes {
            // Input order & sizes from spec:
            public int canHuman;               // u8 (1=YES, 0=NO)
            public int canComputer;            // u8 (1=YES, 0=NO)
            public int behavior;               // u8 (0=Random,1=Warrior,2=Builder,3=Explorer)
            public int hasCustomTownOwnership; // u8
            public int townTypesMask;          // u16 (bits: 0=Castle .. 8=Conflux)
            public int hasRandomTown;          // u8 (1=owns Random Town)
            public int hasMainTown;            // u8 (1=main town present)

            // Present only if hasMainTown == 1:
            public int createHero;             // u8 (only if hasMainTown==1). Otherwise -1
            public int mainTownType;           // u8 (FF=Random, 0=Castle, ...). Otherwise -1
            public int mainTownX;              // u8 (only if hasMainTown==1). Otherwise -1
            public int mainTownY;              // u8 (only if hasMainTown==1). Otherwise -1
            public int mainTownZ;              // u8 (only if hasMainTown==1). Otherwise -1

            public AvailableHeroes availableHeroes;

            public static PlayerAttributes read(HeroStream in) {
                PlayerAttributes p = new PlayerAttributes();
                p.canHuman = in.u8();
                p.canComputer = in.u8();
                p.behavior = in.u8();
                p.hasCustomTownOwnership = in.u8();
                p.townTypesMask = in.u16();
                p.hasRandomTown = in.u8();
                p.hasMainTown = in.u8();

                if (p.hasMainTown == 1) {
                    p.createHero = in.u8();
                    p.mainTownType = in.u8(); // 0xFF = Random, 0 = Castle, etc.
                    p.mainTownX = in.u8();
                    p.mainTownY = in.u8();
                    p.mainTownZ = in.u8();
                } else {
                    p.createHero = -1;
                    p.mainTownType = -1;
                    p.mainTownX = -1;
                    p.mainTownY = -1;
                    p.mainTownZ = -1;
                }

                p.availableHeroes = AvailableHeroes.read(in);
                return p;
            }
        }

        public static class AvailableHeroes {
            // Top-level flags
            public int hasRandomHero;    // u8: 1=yes, 0=no
            public int heroType;         // u8: 0x00..0x7F; 0xFF => no hero, following block absent

            // Present only if heroType != 0xFF:
            public int faceId;           // u8: 0x00..0x7F; 0xFF => standard face
            public byte[] heroName;      // readString32; may be empty if len=0 => standard name
            public int unknownGarbage;   // u8: observed 0x00 or arbitrary; no gameplay effect
            public Vector<NamedHero> playerHeroes; // custom heroes list

            public static class NamedHero {
                public int id;           // u8: hero identifier
                public byte[] name;      // readString32

                public static NamedHero read(HeroStream in) {
                    NamedHero h = new NamedHero();
                    h.id = in.u8();
                    h.name = in.readString32();
                    return h;
                }
            }

            public static AvailableHeroes read(HeroStream in) {
                AvailableHeroes ah = new AvailableHeroes();
                ah.hasRandomHero = in.u8();
                ah.heroType = in.u8();

                if (ah.heroType != 0xFF) {
                    ah.faceId = in.u8();
                    ah.heroName = in.readString32();
                } else {
                    ah.faceId = -1;
                    ah.heroName = null;
                }
                ah.unknownGarbage = in.u8();
                int count = in.u32();
                ah.playerHeroes = new Vector<>(count);
                for (int i = 0; i < count; i++) {
                    ah.playerHeroes.add(NamedHero.read(in));
                }

                return ah;
            }
        }

        public abstract static class SpecialVictoryCondition {

            public static SpecialVictoryCondition read(HeroStream in) {
                int t = in.u8();
                switch (t) {
                    case 0xFF: return None.read(in);
                    case 0x00: return AcquireArtifact.read(in);
                    case 0x01: return AccumulateCreatures.read(in);
                    case 0x02: return AccumulateResources.read(in);
                    case 0x03: return UpgradeTown.read(in);
                    case 0x04: return BuildGrail.read(in);
                    case 0x05: return DefeatHero.read(in);
                    case 0x06: return CaptureTown.read(in);
                    case 0x07: return DefeatMonster.read(in);
                    case 0x08: return FlagAllCreatureDwellings.read(in);
                    case 0x09: return FlagAllMines.read(in);
                    case 0x0A: return TransportArtifact.read(in);
                    default:
                        throw new IllegalArgumentException("Unsupported SpecialVictoryCondition type: 0x" + Integer.toHexString(t));
                }
            }

            // 0xFF — нет спец-победы (без payload)
            public static final class None extends SpecialVictoryCondition {
                public static None read(HeroStream in) {
                    return new None();
                }
            }

            // 0x00 — Acquire a specific artifact
            public static final class AcquireArtifact extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int artifactCode;        // u8

                public static AcquireArtifact read(HeroStream in) {
                    AcquireArtifact s = new AcquireArtifact();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.artifactCode = in.u8();
                    return s;
                }
            }

            // 0x01 — Accumulate creatures
            public static final class AccumulateCreatures extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int creatureId;          // u16
                public int amount;              // u32

                public static AccumulateCreatures read(HeroStream in) {
                    AccumulateCreatures s = new AccumulateCreatures();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.creatureId = in.u16();
                    s.amount = in.u32();
                    return s;
                }
            }

            // 0x02 — Accumulate resources
            public static final class AccumulateResources extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int resourceCode;        // u8 (0..6)
                public int amount;              // u32

                public static AccumulateResources read(HeroStream in) {
                    AccumulateResources s = new AccumulateResources();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.resourceCode = in.u8();
                    s.amount = in.u32();
                    return s;
                }
            }

            // 0x03 — Upgrade a specific town
            public static final class UpgradeTown extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int x;                   // u8
                public int y;                   // u8
                public int z;                   // u8
                public int hallLevel;           // u8 (0 Town,1 City,2 Capitol)
                public int castleLevel;         // u8 (0 Fort,1 Citadel,2 Castle)

                public static UpgradeTown read(HeroStream in) {
                    UpgradeTown s = new UpgradeTown();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.x = in.u8();
                    s.y = in.u8();
                    s.z = in.u8();
                    s.hallLevel = in.u8();
                    s.castleLevel = in.u8();
                    return s;
                }
            }

            // 0x04 — Build the grail structure
            public static final class BuildGrail extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int x;                   // u8
                public int y;                   // u8
                public int z;                   // u8

                public static BuildGrail read(HeroStream in) {
                    BuildGrail s = new BuildGrail();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.x = in.u8();
                    s.y = in.u8();
                    s.z = in.u8();
                    return s;
                }
            }

            // 0x05 — Defeat a specific Hero
            public static final class DefeatHero extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int x;                   // u8
                public int y;                   // u8
                public int z;                   // u8

                public static DefeatHero read(HeroStream in) {
                    DefeatHero s = new DefeatHero();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.x = in.u8();
                    s.y = in.u8();
                    s.z = in.u8();
                    return s;
                }
            }

            // 0x06 — Capture a specific town
            public static final class CaptureTown extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int x;                   // u8
                public int y;                   // u8
                public int z;                   // u8

                public static CaptureTown read(HeroStream in) {
                    CaptureTown s = new CaptureTown();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.x = in.u8();
                    s.y = in.u8();
                    s.z = in.u8();
                    return s;
                }
            }

            // 0x07 — Defeat a specific monster
            public static final class DefeatMonster extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int x;                   // u8
                public int y;                   // u8
                public int z;                   // u8

                public static DefeatMonster read(HeroStream in) {
                    DefeatMonster s = new DefeatMonster();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.x = in.u8();
                    s.y = in.u8();
                    s.z = in.u8();
                    return s;
                }
            }

            // 0x08 — Flag all creature dwellings
            public static final class FlagAllCreatureDwellings extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8

                public static FlagAllCreatureDwellings read(HeroStream in) {
                    FlagAllCreatureDwellings s = new FlagAllCreatureDwellings();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    return s;
                }
            }

            // 0x09 — Flag all mines
            public static final class FlagAllMines extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8

                public static FlagAllMines read(HeroStream in) {
                    FlagAllMines s = new FlagAllMines();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    return s;
                }
            }

            // 0x0A — Transport a specific artifact
            public static final class TransportArtifact extends SpecialVictoryCondition {
                public int allowStandardEnding; // u8
                public int availableForAI;      // u8
                public int artifactCode;        // u8
                public int x;                   // u8
                public int y;                   // u8
                public int z;                   // u8

                public static TransportArtifact read(HeroStream in) {
                    TransportArtifact s = new TransportArtifact();
                    s.allowStandardEnding = in.u8();
                    s.availableForAI = in.u8();
                    s.artifactCode = in.u8();
                    s.x = in.u8();
                    s.y = in.u8();
                    s.z = in.u8();
                    return s;
                }
            }
        }

        public abstract static class SpecialLossCondition {

            public static SpecialLossCondition read(HeroStream in) {
                int t = in.u8();
                switch (t) {
                    case 0xFF: return None.read(in);
                    case 0x00: return LoseSpecificTown.read(in);
                    case 0x01: return LoseSpecificHero.read(in);
                    case 0x02: return TimeExpires.read(in);
                    default:
                        throw new IllegalArgumentException("Unsupported SpecialLossCondition type: 0x" + Integer.toHexString(t));
                }
            }

            // 0xFF — None (no payload)
            public static final class None extends SpecialLossCondition {
                public static None read(HeroStream in) {
                    return new None();
                }
            }

            // 0x00 — Lose a specific town
            public static final class LoseSpecificTown extends SpecialLossCondition {
                public int x; // u8
                public int y; // u8
                public int z; // u8

                public static LoseSpecificTown read(HeroStream in) {
                    LoseSpecificTown s = new LoseSpecificTown();
                    s.x = in.u8();
                    s.y = in.u8();
                    s.z = in.u8();
                    return s;
                }
            }

            // 0x01 — Lose a specific hero
            public static final class LoseSpecificHero extends SpecialLossCondition {
                public int x; // u8
                public int y; // u8
                public int z; // u8

                public static LoseSpecificHero read(HeroStream in) {
                    LoseSpecificHero s = new LoseSpecificHero();
                    s.x = in.u8();
                    s.y = in.u8();
                    s.z = in.u8();
                    return s;
                }
            }

            // 0x02 — Time expires
            public static final class TimeExpires extends SpecialLossCondition {
                public int days; // u16

                public static TimeExpires read(HeroStream in) {
                    TimeExpires s = new TimeExpires();
                    s.days = in.u16();
                    return s;
                }
            }
        }

        public static class Teams {
            public int teamCount;   // u8: number of teams; if 0 -> no team bytes follow
            public int red;    // u8 or -1 if teamCount==0
            public int blue;   // u8 or -1 if teamCount==0
            public int tan;    // u8 or -1 if teamCount==0
            public int green;  // u8 or -1 if teamCount==0
            public int orange; // u8 or -1 if teamCount==0
            public int purple; // u8 or -1 if teamCount==0
            public int teal;   // u8 or -1 if teamCount==0
            public int pink;   // u8 or -1 if teamCount==0

            public static Teams read(HeroStream in) {
                Teams t = new Teams();
                t.teamCount = in.u8();
                if (t.teamCount == 0) {
                    t.red = t.blue = t.tan = t.green = t.orange = t.purple = t.teal = t.pink = -1;
                    return t; // next 8 bytes are absent
                }
                t.red = in.u8();
                t.blue = in.u8();
                t.tan = in.u8();
                t.green = in.u8();
                t.orange = in.u8();
                t.purple = in.u8();
                t.teal = in.u8();
                t.pink = in.u8();
                return t;
            }
        }

        public static class FreeHeroes {
            // 20 bytes bitmask: for each class byte, bit=1 hero is free, bit=0 taken
            public int[] classBits = new int[20]; // u8 each

            // 4 bytes reserved (unused/padding)
            public int reserved; // u32 raw

            public Vector<ConfiguredHero> configured = new Vector<>();

            public static class ConfiguredHero {
                public int id;              // u8: hero ID
                public int portrait;        // u8: portrait (0xFF => standard)
                public byte[] name;         // string32 (len=0 => standard)
                public int allowedPlayers;  // u8 bitmask: 0xFF => all

                public static ConfiguredHero read(HeroStream in) {
                    ConfiguredHero h = new ConfiguredHero();
                    h.id = in.u8();
                    h.portrait = in.u8();
                    h.name = in.readString32();
                    h.allowedPlayers = in.u8();
                    return h;
                }
            }

            public static FreeHeroes read(HeroStream in) {
                FreeHeroes fh = new FreeHeroes();

                for (int i = 0; i < 20; i++) {
                    fh.classBits[i] = in.u8();
                }

                fh.reserved = in.u32();
                System.out.println("reserved: " + fh.reserved);

                int count = in.u8();
                fh.configured = new Vector<>(count);
                for (int i = 0; i < count; i++) {
                    fh.configured.add(ConfiguredHero.read(in));
                }

                return fh;
            }
        }

    }

    public static class Artifacts {
        public byte[] artefacts;

        public static Artifacts read(HeroStream in) {
            Artifacts r = new Artifacts();
            r.artefacts = in.bytes(18);
            return r;
        }
    }

    public static class Spells {
        public byte[] spells;

        public static Spells read(HeroStream in) {
            Spells r = new Spells();
            r.spells = in.bytes(9);
            return r;
        }
    }

    public static class SecSkills {
        public byte[] skills;

        public static SecSkills read(HeroStream in) {
            SecSkills r = new SecSkills();
            r.skills = in.bytes(4);
            return r;
        }
    }

    public static class Rumors {
        public int total_rumors;
        public final Vector<Rumor> rumors = new Vector<>();

        public static Rumors read(HeroStream in) {
            Rumors r = new Rumors();
            r.total_rumors = in.u32();
            for (int i = 0; i < r.total_rumors; i++) {
                r.rumors.add(Rumor.read(in));
            }
            return r;
        }

        public static class Rumor {
            public byte[] rumor_name;
            public byte[] rumor_text;

            public static Rumor read(HeroStream in) {
                Rumor r = new Rumor();
                r.rumor_name = in.readString32();
                r.rumor_text = in.readString32();
                return r;
            }
        }
    }

    public static class HeroStream {
        public final byte[] in;
        public int p;

        public HeroStream(byte[] in) {
            this.in = in;
        }

        public int u8() {
            return in[p++] & 0xff;
        }

        public int u16() {
            return u8() | u8() << 8;
        }

        public int u32() {
            return u8() | u8() << 8 | u8() << 16 | u8() << 24;
        }

        public void skip(int i) {
            p += i;
        }

        public byte[] readString32() {
            int c = u32();
            return bytes(c);
        }

        public byte[] bytes(int i) {
            byte[] r = new byte[i];
            System.arraycopy(in, p, r, 0, i);
            p += i;
            return r;
        }
    }
}
