package com.detectautoalchers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class StaffClassifier
{
    static final int STAFF_OF_FIRE = 1387;

    private static final Set<Integer> FIRE_RUNE_STAVES = new HashSet<>(Arrays.asList(
        STAFF_OF_FIRE,
        1393,   // Fire battlestaff
        1401,   // Mystic fire staff
        3053,   // Lava battlestaff
        3054,   // Mystic lava staff
        11787,  // Steam battlestaff
        11789,  // Mystic steam staff
        11998,  // Smoke battlestaff
        12000,  // Mystic smoke staff
        21198,  // Smoke battlestaff variant
        21200   // Mystic smoke staff variant
    ));

    private StaffClassifier()
    {
    }

    static boolean isBasicFireStaff(int weaponId)
    {
        return weaponId == STAFF_OF_FIRE;
    }

    static boolean isFireRuneProvider(int weaponId)
    {
        return FIRE_RUNE_STAVES.contains(weaponId);
    }
}
