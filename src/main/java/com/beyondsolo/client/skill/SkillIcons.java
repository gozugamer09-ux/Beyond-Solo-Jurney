package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.skill.Skill;
import net.minecraft.resources.ResourceLocation;

/** Each skill's icon: a distance-field texture made by tools/gen_ui_assets.py ("skill_" + path). */
public final class SkillIcons {
    public static final ResourceLocation TOME = BeyondSolo.id("textures/gui/icon/tome.png");

    public static ResourceLocation of(Skill skill) {
        return BeyondSolo.id("textures/gui/icon/skill_" + skill.path() + ".png");
    }

    private SkillIcons() {}
}
