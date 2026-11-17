package com.beyondsolo.client.depths;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.depths.GateWeather;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.TrapKind;
import com.beyondsolo.skill.SkillElement;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Distance-field icons of the depths (see {@code client.gfx.Icons}), built by tools/gen_depths_assets.py: one for each
 * element, weather, trap and hidden chamber, and a few marks.
 */
public final class DepthsIcons {
    public static final ResourceLocation WARNING = icon("depths_warning");
    public static final ResourceLocation KEYHOLE = icon("depths_keyhole");
    public static final ResourceLocation WEAK = icon("depths_weak");
    public static final ResourceLocation CHECK = icon("check");

    public static ResourceLocation element(SkillElement e) {
        return icon("element_" + e.name().toLowerCase(Locale.ROOT));
    }

    public static ResourceLocation weather(GateWeather w) {
        return icon("weather_" + w.id());
    }

    public static ResourceLocation trap(TrapKind k) {
        return icon("trap_" + k.id());
    }

    public static ResourceLocation hidden(HiddenKind k) {
        return icon("hidden_" + k.id());
    }

    private static ResourceLocation icon(String name) {
        return BeyondSolo.id("textures/gui/icon/" + name + ".png");
    }

    private DepthsIcons() {}
}
