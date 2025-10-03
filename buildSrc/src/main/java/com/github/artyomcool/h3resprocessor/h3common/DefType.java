package com.github.artyomcool.h3resprocessor.h3common;

import java.util.Arrays;
import java.util.List;

public enum DefType {
    DefDefault("default", 0x40),
    DefCombatCreature("combat creature", 0x42),
    DefAdventureObject("adventure object", 0x43),
    DefAdventureHero("adventure hero", 0x44),
    DefGroundTile("ground tiles", 0x45),
    DefMousePointer("mouse pointer", 0x46),
    DefInterface("interface", 0x47),
    DefCombatHero("combat hero", 0x49),

    Unknown("Unknown", 0)
    ;

    public static List<DefType> VALUES = Arrays.asList(DefType.values());

    public final String name;
    public final int type;

    DefType(String name, int type) {
        this.name = name;
        this.type = type;
    }

    public static DefType of(int type) {
        for (DefType value : VALUES) {
            if (value.type == type) {
                return value;
            }
        }
        return Unknown;
    }

    public static DefType of(String name) {
        for (DefType value : VALUES) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        return Unknown;
    }
}
