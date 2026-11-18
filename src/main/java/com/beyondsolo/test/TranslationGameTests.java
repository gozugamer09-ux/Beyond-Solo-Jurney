package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The System is playable in Spanish, and stays that way: every line in a namespace's {@code en_us.json} must also be in
 * its {@code es_es.json}, with the same {@code %s} arguments, and the build must hand the same Spanish to every Spanish
 * the game offers (Latin America included). A feature that adds English text without Spanish fails here: add the
 * line to {@code assets/<namespace>/lang/es_es.json} too (docs/TRANSLATING.md has the word list).
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TranslationGameTests {
    private static final String ARENA = "evaluation_zone";
    /** The language files we translate; the others are made from it by the build (build.gradle). */
    static final String SPANISH = "es_es";
    static final List<String> SPANISH_COPIES = List.of("es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve", "esan");
    /** Namespaces with their own language files. New ones are found on their own when the assets folder can be listed. */
    private static final List<String> KNOWN = List.of("beyondsolo", "beyondsolo_armory", "beyondsolo_breaks", "beyondsolo_crafting", "beyondsolo_depths",
            "beyondsolo_economy", "beyondsolo_enemies", "beyondsolo_gear", "beyondsolo_jobs", "beyondsolo_keys", "beyondsolo_penalty",
            "beyondsolo_redgates", "beyondsolo_runes", "beyondsolo_shadows", "beyondsolo_situations");
    /** Minecraft's own pattern for a translation argument ({@code TranslatableContents}). */
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    @GameTest(template = ARENA)
    public static void everyEnglishLineHasSpanish(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (String ns : namespaces()) {
            JsonObject en = read(ns, "en_us");
            if (en == null) {
                if (ns.equals(BeyondSolo.MOD_ID)) problems.add("can't read assets/" + ns + "/lang/en_us.json");
                continue;
            }
            JsonObject es = read(ns, SPANISH);
            if (es == null) {
                problems.add("assets/" + ns + "/lang/" + SPANISH + ".json is missing: translate " + en.size() + " lines");
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, JsonElement> e : en.entrySet()) {
                checked++;
                JsonElement tr = es.get(e.getKey());
                if (tr == null || !tr.isJsonPrimitive()) {
                    missing.add(e.getKey());
                    continue;
                }
                String bad = argProblem(e.getValue().getAsString(), tr.getAsString());
                if (bad != null) problems.add(ns + " " + SPANISH + " \"" + e.getKey() + "\": " + bad);
            }
            if (!missing.isEmpty()) {
                problems.add(missing.size() + " English line(s) of " + ns + " have no Spanish in assets/" + ns + "/lang/" + SPANISH
                        + ".json: " + String.join(", ", missing.subList(0, Math.min(12, missing.size()))) + (missing.size() > 12 ? ", ..." : ""));
            }
            for (String copy : SPANISH_COPIES) {
                JsonObject c = read(ns, copy);
                if (c == null || !c.equals(es)) problems.add("assets/" + ns + "/lang/" + copy + ".json should be the build's copy of "
                        + SPANISH + ".json (see generateSpanishVariants in build.gradle)");
            }
        }
        if (!problems.isEmpty()) {
            helper.fail("Spanish is incomplete (" + problems.size() + " problem(s)):\n  " + String.join("\n  ", problems.subList(0, Math.min(25, problems.size()))));
        }
        helper.assertTrue(checked > 2000, "the language files were all read (" + checked + " lines)");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void argumentsAreCheckedLikeMinecraft(GameTestHelper helper) {
        helper.assertTrue(argProblem("%s damage", "%s de daño") == null, "same argument");
        helper.assertTrue(argProblem("%s at %s", "%2$s con %1$s") == null, "reordered arguments");
        helper.assertTrue(argProblem("+%s%% speed", "+%s%% de velocidad") == null, "a literal percent");
        helper.assertTrue(argProblem("heals 40% of it", "cura un 40% de ella") == null, "a percent before a space is plain text");
        helper.assertTrue(argProblem("%s damage", "de daño") != null, "a lost argument");
        helper.assertTrue(argProblem("%s damage", "%s de daño %s") != null, "an extra argument");
        helper.assertTrue(argProblem("50%% more", "un 50%más") != null, "a percent glued to a word breaks the line");
        helper.succeed();
    }

    /** Why a translation would break where the English works, or null if its arguments match. */
    static @Nullable String argProblem(String en, String tr) {
        Args a = args(en), b = args(tr);
        if (b.bad != null && a.bad == null) return b.bad;
        if (!a.used.equals(b.used)) return "arguments " + b.used + " where the English has " + a.used;
        return null;
    }

    private record Args(Set<Integer> used, @Nullable String bad) {}

    private static Args args(String s) {
        Set<Integer> used = new TreeSet<>();
        String bad = null;
        int next = 0;
        Matcher m = ARG.matcher(s);
        while (m.find()) {
            String type = m.group(2);
            if (type.equals("%")) continue;
            if (!type.equals("s")) {
                bad = "'" + m.group() + "' is not an argument Minecraft understands (write %s, or %% for a percent sign)";
                continue;
            }
            used.add(m.group(1) != null ? Integer.parseInt(m.group(1)) : ++next);
        }
        return new Args(used, bad);
    }

    private static List<String> namespaces() {
        Set<String> out = new TreeSet<>(KNOWN);
        try {
            URL url = BeyondSolo.class.getResource("/assets/" + BeyondSolo.MOD_ID + "/lang/en_us.json");
            if (url != null) {
                Path assets = Path.of(url.toURI()).getParent().getParent().getParent();
                try (Stream<Path> dirs = Files.list(assets)) {
                    dirs.filter(d -> Files.exists(d.resolve("lang").resolve("en_us.json")))
                            .forEach(d -> out.add(d.getFileName().toString().replace("/", "")));
                }
            }
        } catch (Exception ignored) {
            // a packed jar that can't be listed: the known namespaces are still checked
        }
        return List.copyOf(out);
    }

    private static @Nullable JsonObject read(String ns, String locale) {
        try (InputStream in = BeyondSolo.class.getResourceAsStream("/assets/" + ns + "/lang/" + locale + ".json")) {
            if (in == null) return null;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private TranslationGameTests() {}
}
