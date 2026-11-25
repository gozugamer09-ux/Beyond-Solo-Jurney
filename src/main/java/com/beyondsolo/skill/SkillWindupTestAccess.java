package com.beyondsolo.skill;

import java.util.Map;

/** What the wind-up game tests need from inside the skill package. */
public final class SkillWindupTestAccess {
    /** The looks table as the jar has it, read fresh. */
    public static Map<String, Object[]> table() {
        return Gestures.readTable();
    }

    /** Back to the table alone, forgetting what casts showed. */
    public static void forgetCasts() {
        Gestures.forget();
    }

    /** Forgets what this skill's casts showed, so the next cast says it again. */
    public static void clearLook(Skill s) {
        Gestures.clear(s);
    }

    /** What the last cast of the skill showed first ({look, variant}), or null if none went off since it was cleared. */
    public static Object[] cast(Skill s) {
        return Gestures.known(s);
    }

    private SkillWindupTestAccess() {}
}
