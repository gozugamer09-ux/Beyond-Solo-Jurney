package com.beyondsolo.client.quest;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.ObjectiveType;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestPenalty;
import com.beyondsolo.quest.QuestRewards;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Texts, icons and colours the quest screens share. */
public final class QuestText {
    /** One reward as the UI shows it. */
    public record Reward(ResourceLocation icon, String text, int color, long countTo) {}

    public static ResourceLocation icon(QuestCategory category) {
        return switch (category) {
            case SET -> Icons.SEAL;
            case DAILY -> Icons.HOURGLASS;
            case SURPRISE -> Icons.SPARKLE;
            case EMERGENCY -> Icons.of(SystemNotification.Kind.ALERT);
            case NPC -> Icons.MEDAL;
            case GATE -> Icons.DIAMOND;
            case SECRET -> Icons.KEY;
            case ASSOCIATION -> Icons.ID_CARD;
            case STORY -> Icons.HEART;
        };
    }

    public static ResourceLocation icon(ObjectiveType type) {
        return switch (type) {
            case KILL -> Icons.SKULL;
            case BREAK -> Icons.HEXAGON;
            case COLLECT -> Icons.DIAMOND;
            case TRAVEL -> Icons.RUN;
            case LEVEL -> Icons.STAR;
            case CUSTOM -> Icons.PIN;
        };
    }

    public static ResourceLocation icon(QuestEvent.FailReason reason) {
        return switch (reason) {
            case TIME, DAY_ENDED -> Icons.HOURGLASS;
            case DIED -> Icons.SKULL;
            case FLED -> Icons.RUN;
            case ABANDONED -> Icons.EYE_OFF;
            case LOST -> Icons.HEART_BREAK;
            case BROKEN -> Icons.CHAIN;
        };
    }

    public static int color(QuestCategory category) {
        return category.color();
    }

    public static List<Reward> rewards(Quest q) {
        QuestRewards r = q.rewards();
        List<Reward> list = new ArrayList<>();
        if (r.xp() > 0) list.add(new Reward(Icons.SPARKLE, "XP", SystemTheme.XP_B, r.xp()));
        if (r.statPoints() > 0) list.add(new Reward(Icons.STAR, Component.translatable("quest.beyondsolo.reward.points").getString(), SystemTheme.GOLD, r.statPoints()));
        if (r.mana() > 0) list.add(new Reward(Icons.DROP, "MP", SystemTheme.MP_B, Math.round(r.mana())));
        for (ItemStack stack : r.items()) {
            String name = stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
            list.add(new Reward(Icons.DIAMOND, name, SystemTheme.ACCENT_BRIGHT, -1));
        }
        if (!r.title().isEmpty()) {
            list.add(new Reward(Icons.CROWN, Component.translatable("quest.beyondsolo.reward.title", Component.translatable(r.title())).getString(), SystemTheme.GOLD, -1));
        }
        // an association bounty and its merit
        long gold = q.extra().getLong(com.beyondsolo.association.Association.REWARD_GOLD);
        if (gold > 0) list.add(new Reward(Icons.COINS, Component.translatable("quest.beyondsolo.reward.gold").getString(), SystemTheme.GOLD, gold));
        long merit = q.extra().getLong(com.beyondsolo.association.Association.REWARD_MERIT);
        if (merit > 0) list.add(new Reward(Icons.MEDAL, Component.translatable("quest.beyondsolo.reward.merit").getString(), 0xFF8FB4FF, merit));
        return list;
    }

    /** "+240 XP" style text of a reward, with the number counted up to {@code fraction}. */
    public static String rewardLabel(Reward r, float fraction) {
        if (r.countTo() < 0) return r.text();
        long shown = Math.round(r.countTo() * Math.min(1f, fraction));
        return "+" + shown + " " + r.text();
    }

    public static Component rewardsLine(Quest q) {
        List<String> parts = new ArrayList<>();
        for (Reward r : rewards(q)) parts.add(rewardLabel(r, 1f));
        return parts.isEmpty() ? Component.translatable("quest.beyondsolo.reward.none") : Component.literal(String.join("  ·  ", parts));
    }

    public static Component penaltyLine(Quest q) {
        return penaltyLine(q.penalty());
    }

    public static Component penaltyLine(QuestPenalty p) {
        if (p.isNone()) return Component.translatable("quest.beyondsolo.penalty.none");
        if (p.zone()) return Component.translatable("quest.beyondsolo.penalty.zone");
        return Component.translatable("quest.beyondsolo.penalty", Math.round(p.exhaustion()), p.debuffSeconds());
    }

    /**
     * An objective's counter as its display asks: "3/10", "35%", the seconds left of a hold ("0:42"), a health share,
     * or nothing at all (a plain line, a rule).
     */
    public static String count(QuestObjective o) {
        return switch (o.display()) {
            case COUNT -> o.progress() + "/" + o.required();
            case PERCENT, HEALTH -> Math.round(100f * o.progress() / Math.max(1, o.required())) + "%";
            case CLOCK -> clock(20L * Math.max(0, o.required() - o.progress()));
            case NONE, RULE -> "";
        };
    }

    /** The icon of an objective's line: a rule is a shield, a watched health a heart, a hold an hourglass. */
    public static ResourceLocation icon(QuestObjective o) {
        return switch (o.display()) {
            case RULE -> Icons.SHIELD;
            case HEALTH -> Icons.HEART;
            case CLOCK -> Icons.HOURGLASS;
            default -> icon(o.type());
        };
    }

    /** The colour a watched health bar is drawn in: green, then amber, then red as it falls. */
    public static int healthColor(float fraction) {
        if (fraction > 0.6f) return 0xFF6FE38A;
        if (fraction > 0.3f) return 0xFFFFC14D;
        return 0xFFFF5D6C;
    }

    /** m:ss, or h:mm:ss for long clocks. */
    public static String clock(long ticks) {
        long s = Math.max(0, (ticks + 19) / 20);
        if (s >= 3600) return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private QuestText() {}
}
