#!/usr/bin/env python3
"""
Writes the Crimson Citadel's texts (the endless tower and the boss rush) in English and Spanish to
src/main/resources/assets/beyondsolo_tower/lang/en_us.json and es_es.json. Every line is written in both languages
here, side by side, so neither can fall behind (TranslationGameTests checks; the build copies es_es to the Latin
American variants).

Run from the repo root:  python3 tools/tower_lang.py
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/beyondsolo_tower/lang")

LINES = [
    # ------------------------------------------------------------------ items, the lord, titles
    ("item.beyondsolo.citadel_key", "Key to the Crimson Citadel", "Llave de la Ciudadela Carmesí"),
    ("item.beyondsolo.citadel_key.tip", "Opens a citadel a hundred floors high, standing outside the world.",
     "Abre una ciudadela de cien plantas que se alza fuera del mundo."),
    ("item.beyondsolo.citadel_key.use", "Use it to bind it to you (it is used up).", "Úsala para vincularla a ti (se consume)."),
    ("item.beyondsolo.sigil_of_dominion", "Sigil of Dominion", "Sello de Dominio"),
    ("item.beyondsolo.sigil_of_dominion.tip", "The seal of the Citadel's fallen lord.", "El sello del señor caído de la Ciudadela."),
    ("item.beyondsolo.sigil_of_dominion.rush", "Opens the Sovereign Rush.", "Abre el Asalto Soberano."),
    ("item.beyondsolo.sigil_of_dominion.lore", "A crown of black iron round a crimson heart. It is warm, and it is waiting.",
     "Una corona de hierro negro en torno a un corazón carmesí. Está tibia, y espera."),
    ("itemGroup.beyondsolo.citadel", "Crimson Citadel", "Ciudadela Carmesí"),
    ("entity.beyondsolo.citadel_lord", "Vharos, the Crimson King", "Vharos, el Rey Carmesí"),
    ("title.beyondsolo.citadel_conqueror", "Conqueror of the Citadel", "Conquistador de la Ciudadela"),
    ("title.beyondsolo.rush_sovereign", "Sovereign of the Rush", "Soberano del Asalto"),
    ("screen.beyondsolo.tab.citadel", "CITADEL", "CIUDADELA"),

    # ------------------------------------------------------------------ floors, wings, gatekeepers, the lord
    ("tower.beyondsolo.floor", "Floor %s", "Planta %s"),
    ("tower.beyondsolo.spire", "Endless Spire", "Aguja Infinita"),
    ("tower.beyondsolo.wing.outer_gate", "The Outer Gate", "La Puerta Exterior"),
    ("tower.beyondsolo.wing.barracks", "The Barracks", "Los Cuarteles"),
    ("tower.beyondsolo.wing.ossuary", "The Ossuary", "El Osario"),
    ("tower.beyondsolo.wing.forge", "The Forge", "La Forja"),
    ("tower.beyondsolo.wing.archive", "The Archive", "El Archivo"),
    ("tower.beyondsolo.wing.cloister", "The Cloister", "El Claustro"),
    ("tower.beyondsolo.wing.garden", "The Withered Garden", "El Jardín Marchito"),
    ("tower.beyondsolo.wing.court", "The Crimson Court", "La Corte Carmesí"),
    ("tower.beyondsolo.wing.ramparts", "The Ramparts", "Las Murallas"),
    ("tower.beyondsolo.wing.throne", "The Throne Hall", "La Sala del Trono"),
    ("tower.beyondsolo.kind.battle", "Clear the hall", "Despeja la sala"),
    ("tower.beyondsolo.kind.elite", "A champion and its elites", "Un campeón y su élite"),
    ("tower.beyondsolo.kind.siege", "Hold the hall until the time runs out", "Resiste en la sala hasta que acabe el tiempo"),
    ("tower.beyondsolo.kind.keeper", "A gatekeeper", "Un guardián"),
    ("tower.beyondsolo.kind.lord", "The lord of the Citadel", "El señor de la Ciudadela"),
    ("tower.beyondsolo.keeper.10", "Garmhal, Hound of the Outer Gate", "Garmhal, Sabueso de la Puerta Exterior"),
    ("tower.beyondsolo.keeper.20", "Ostrak, Warden of the Barracks", "Ostrak, Guardián de los Cuarteles"),
    ("tower.beyondsolo.keeper.30", "Sithra, Mother of the Ossuary", "Sithra, Madre del Osario"),
    ("tower.beyondsolo.keeper.40", "Hrothgal, Master of the Forge", "Hrothgal, Maestro de la Forja"),
    ("tower.beyondsolo.keeper.50", "Ixara, Keeper of the Archive", "Ixara, Custodia del Archivo"),
    ("tower.beyondsolo.keeper.60", "Morghast, Hound of the Cloister", "Morghast, Sabueso del Claustro"),
    ("tower.beyondsolo.keeper.70", "Velsira, Queen of the Withered Garden", "Velsira, Reina del Jardín Marchito"),
    ("tower.beyondsolo.keeper.80", "Sir Caedren, Champion of the Court", "Sir Caedren, Campeón de la Corte"),
    ("tower.beyondsolo.keeper.90", "Azhvarr, Wyrm of the Ramparts", "Azhvarr, Sierpe de las Murallas"),
    ("tower.beyondsolo.keeper.title", "Gatekeeper of Floor %s", "Guardián de la Planta %s"),
    ("tower.beyondsolo.lord.name", "Vharos, the Crimson King", "Vharos, el Rey Carmesí"),
    ("tower.beyondsolo.lord.title", "Lord of the Crimson Citadel", "Señor de la Ciudadela Carmesí"),
    ("tower.beyondsolo.lord.defeated", "The lord of the Citadel has fallen", "El señor de la Ciudadela ha caído"),

    # ------------------------------------------------------------------ the boss rush
    ("tower.beyondsolo.rush.tier", "%s-Rank Boss Rush", "Asalto de Jefes de Rango %s"),
    ("tower.beyondsolo.rush.sovereign", "Sovereign Rush", "Asalto Soberano"),
    ("tower.beyondsolo.rush.subtitle", "%s bosses, one after another", "%s jefes, uno tras otro"),
    ("tower.beyondsolo.rush.bout", "Bout %s of %s", "Combate %s de %s"),

    # ------------------------------------------------------------------ why the System won't take them
    ("tower.beyondsolo.refuse.title", "The Citadel is closed to you", "La Ciudadela está cerrada para ti"),
    ("tower.beyondsolo.refuse.asleep", "Only an awakened hunter can enter the Crimson Citadel.",
     "Solo un cazador despertado puede entrar en la Ciudadela Carmesí."),
    ("tower.beyondsolo.refuse.no_key", "Bind the Key to the Crimson Citadel first.", "Primero vincula la Llave de la Ciudadela Carmesí."),
    ("tower.beyondsolo.refuse.inside", "You are already inside the Citadel.", "Ya estás dentro de la Ciudadela."),
    ("tower.beyondsolo.refuse.busy", "The System can't take you from where you are now.", "El Sistema no puede sacarte de donde estás ahora."),
    ("tower.beyondsolo.refuse.not_conquered", "The Endless Spire opens only above a fallen lord.",
     "La Aguja Infinita solo se abre sobre un señor caído."),
    ("tower.beyondsolo.refuse.rank", "Your rank is too low for that rush.", "Tu rango es demasiado bajo para ese asalto."),
    ("tower.beyondsolo.refuse.sigil", "Only the holder of the Sigil of Dominion may enter the Sovereign Rush.",
     "Solo quien posee el Sello de Dominio puede entrar en el Asalto Soberano."),
    ("tower.beyondsolo.refuse.closed", "The Citadel can't be reached in this world.", "No se puede llegar a la Ciudadela en este mundo."),

    # ------------------------------------------------------------------ System notices
    ("notify.beyondsolo.tower.told.title", "The Crimson Citadel", "La Ciudadela Carmesí"),
    ("notify.beyondsolo.tower.told.body",
     "A citadel a hundred floors high stands outside the world, its lord on the last. The Hunter Shop now sells its key.",
     "Una ciudadela de cien plantas se alza fuera del mundo, con su señor en la última. La Tienda del Cazador ya vende su llave."),
    ("notify.beyondsolo.tower.key_cold.title", "The key is cold", "La llave está fría"),
    ("notify.beyondsolo.tower.key_cold.body", "It answers only the System of an awakened hunter.",
     "Solo responde al Sistema de un cazador despertado."),
    ("notify.beyondsolo.tower.key_bound.title", "Key bound", "Llave vinculada"),
    ("notify.beyondsolo.tower.key_bound.body", "The Crimson Citadel will open to you: see the CITADEL page of the System window.",
     "La Ciudadela Carmesí se abrirá para ti: mira la página CIUDADELA de la ventana del Sistema."),
    ("notify.beyondsolo.tower.key_already.body", "You are already bound to the Citadel.", "Ya estás vinculado a la Ciudadela."),
    ("notify.beyondsolo.tower.conquered.title", "The Citadel is conquered", "La Ciudadela ha sido conquistada"),
    ("notify.beyondsolo.tower.reconquered.title", "The throne is yours again", "El trono es tuyo de nuevo"),
    ("notify.beyondsolo.tower.conquered.body", "The lord has fallen. The stair climbs on into the Endless Spire.",
     "El señor ha caído. La escalera sigue hacia la Aguja Infinita."),
    ("notify.beyondsolo.tower.sigil.body", "The lord has fallen, and his Sigil of Dominion is yours.",
     "El señor ha caído y su Sello de Dominio es tuyo."),
    ("notify.beyondsolo.tower.rush.title", "%s cleared", "%s superado"),
    ("notify.beyondsolo.tower.rush.body", "Time: %s", "Tiempo: %s"),
    ("notify.beyondsolo.tower.rush.best", "New best time: %s", "Nuevo mejor tiempo: %s"),
    ("notify.beyondsolo.tower.fallen.title", "Fallen in the Citadel", "Caído en la Ciudadela"),
    ("notify.beyondsolo.tower.fallen.body", "You fell on floor %s. Your climb resumes from floor %s.",
     "Caíste en la planta %s. Tu ascenso continúa desde la planta %s."),
    ("notify.beyondsolo.tower.fallen.spire", "You fell on floor %s of the Endless Spire. Your best: floor %s.",
     "Caíste en la planta %s de la Aguja Infinita. Tu récord: planta %s."),
    ("notify.beyondsolo.tower.fallen.rush", "You fell in bout %s of %s. The rush is lost.",
     "Caíste en el combate %s de %s. El asalto está perdido."),
    ("notify.beyondsolo.tower.left.title", "You left the Citadel", "Saliste de la Ciudadela"),
    ("notify.beyondsolo.tower.left.body", "Floors cleared: %s. Your next climb starts on floor %s.",
     "Plantas despejadas: %s. Tu próximo ascenso empieza en la planta %s."),
    ("notify.beyondsolo.tower.sigil_hums", "The sigil hums with the Citadel's power. One day something greater will answer it.",
     "El sello vibra con el poder de la Ciudadela. Algún día algo más grande le responderá."),

    # ------------------------------------------------------------------ the HUD
    ("tower.beyondsolo.hud.floor", "FLOOR %s", "PLANTA %s"),
    ("tower.beyondsolo.hud.bout", "Bout %s of %s", "Combate %s de %s"),
    ("tower.beyondsolo.hud.foes", "%s foes", "%s enemigos"),
    ("tower.beyondsolo.hud.wave_foes", "Wave %s/%s · %s foes", "Oleada %s/%s · %s enemigos"),
    ("tower.beyondsolo.hud.cleared_short", "CLEARED", "DESPEJADA"),
    ("tower.beyondsolo.hud.record", "NEW RECORD", "NUEVO RÉCORD"),
    ("tower.beyondsolo.hud.best_floor", "Best: floor %s", "Récord: planta %s"),
    ("tower.beyondsolo.hud.best_time", "Best %s", "Récord %s"),
    ("tower.beyondsolo.hud.system", "THE SYSTEM", "EL SISTEMA"),
    ("tower.beyondsolo.hud.pull", "THE CITADEL CALLS", "LA CIUDADELA LLAMA"),
    ("tower.beyondsolo.hud.pull.floor", "Floor %s awaits you", "La planta %s te espera"),
    ("tower.beyondsolo.hud.pull.spire", "The Endless Spire awaits you", "La Aguja Infinita te espera"),
    ("tower.beyondsolo.hud.pull.rush", "The %s begins", "Comienza el %s"),
    ("tower.beyondsolo.hud.rush_head", "BOSS RUSH", "ASALTO DE JEFES"),
    ("tower.beyondsolo.hud.cleared", "FLOOR CLEARED", "PLANTA DESPEJADA"),
    ("tower.beyondsolo.hud.cleared.sub", "The stair is open. To leave, stand on the sigil by the entrance.",
     "La escalera está abierta. Para salir, ponte sobre el sello de la entrada."),
    ("tower.beyondsolo.hud.cleared.exit", "To leave, stand on the sigil by the entrance.", "Para salir, ponte sobre el sello de la entrada."),
    ("tower.beyondsolo.hud.ascending", "ASCENDING", "ASCENDIENDO"),
    ("tower.beyondsolo.hud.victory", "THE THRONE IS TAKEN", "EL TRONO ES TUYO"),
    ("tower.beyondsolo.hud.victory.sub", "The lord of the Crimson Citadel has fallen", "El señor de la Ciudadela Carmesí ha caído"),
    ("tower.beyondsolo.hud.breather", "BREATHE", "RESPIRA"),
    ("tower.beyondsolo.hud.breather.sub", "Next: bout %s of %s", "Siguiente: combate %s de %s"),
    ("tower.beyondsolo.hud.rush_done", "RUSH CLEARED", "ASALTO SUPERADO"),
    ("tower.beyondsolo.hud.fallen", "FALLEN", "CAÍDO"),
    ("tower.beyondsolo.hud.fallen.castle", "The System pulls you out. Your climb resumes from floor %s.",
     "El Sistema te saca. Tu ascenso continúa desde la planta %s."),
    ("tower.beyondsolo.hud.fallen.spire", "The System pulls you out. Your best in the spire: floor %s.",
     "El Sistema te saca. Tu récord en la aguja: planta %s."),
    ("tower.beyondsolo.hud.fallen.rush", "The System pulls you out. The rush is lost.", "El Sistema te saca. El asalto está perdido."),
    ("tower.beyondsolo.hud.leaving", "LEAVING THE CITADEL", "SALIENDO DE LA CIUDADELA"),
    ("tower.beyondsolo.hud.exit_hold", "LEAVING", "SALIENDO"),

    # ------------------------------------------------------------------ banners
    ("tower.beyondsolo.ev.wave", "WAVE %s OF %s", "OLEADA %s DE %s"),
    ("tower.beyondsolo.ev.siege_end", "TEN SECONDS LEFT", "QUEDAN DIEZ SEGUNDOS"),
    ("tower.beyondsolo.ev.champion", "A CHAMPION ENTERS", "ENTRA UN CAMPEÓN"),
    ("tower.beyondsolo.ev.cache", "THE CACHE IS STILL SEALED", "EL COFRE SIGUE SELLADO"),
    ("tower.beyondsolo.ev.cache.sub", "Open it before you climb, or what it holds stays behind.",
     "Ábrelo antes de subir, o lo que guarda se quedará atrás."),
    ("tower.beyondsolo.ev.checkpoint", "CHECKPOINT", "PUNTO DE CONTROL"),
    ("tower.beyondsolo.ev.checkpoint.sub", "Your climbs now start on floor %s", "Tus ascensos empiezan ahora en la planta %s"),
    ("tower.beyondsolo.ev.record", "NEW RECORD", "NUEVO RÉCORD"),
    ("tower.beyondsolo.ev.record.spire", "Floor %s of the Endless Spire", "Planta %s de la Aguja Infinita"),
    ("tower.beyondsolo.ev.record.rush", "Your fastest rush yet", "Tu asalto más rápido"),
    ("tower.beyondsolo.ev.moon", "BLOOD MOON", "LUNA DE SANGRE"),
    ("tower.beyondsolo.ev.moon.sub", "The lord makes his last stand", "El señor lucha hasta el final"),
    ("tower.beyondsolo.ev.sigil", "SIGIL OF DOMINION", "SELLO DE DOMINIO"),
    ("tower.beyondsolo.ev.sigil.sub", "The lord's seal is yours. The Sovereign Rush is open.",
     "El sello del señor es tuyo. El Asalto Soberano está abierto."),

    # ------------------------------------------------------------------ the CITADEL page
    ("tower.beyondsolo.tab.name", "THE CRIMSON CITADEL", "LA CIUDADELA CARMESÍ"),
    ("tower.beyondsolo.tab.sealed", "The citadel opens only to a hunter bound to its key. The Hunter Shop sells it from rank %s.",
     "La ciudadela solo se abre a un cazador vinculado a su llave. La Tienda del Cazador la vende desde el rango %s."),
    ("tower.beyondsolo.tab.sealed_use", "You hold its key: use it to bind it to you.", "Tienes su llave: úsala para vincularla a ti."),
    ("tower.beyondsolo.tab.about",
     "A hundred floors climb to its lord's throne, a gatekeeper on every tenth. Past the lord, the Endless Spire; in its arena, the boss rush.",
     "Cien plantas suben hasta el trono de su señor, con un guardián cada diez. Tras el señor, la Aguja Infinita; en su arena, el asalto de jefes."),
    ("tower.beyondsolo.tab.conquered", "Conquered · the lord has fallen %s times", "Conquistada · el señor ha caído %s veces"),
    ("tower.beyondsolo.tab.progress", "Checkpoint: floor %s · highest: floor %s", "Punto de control: planta %s · máxima: planta %s"),
    ("tower.beyondsolo.tab.castle", "THE CASTLE · FLOORS 1-100", "EL CASTILLO · PLANTAS 1-100"),
    ("tower.beyondsolo.tab.wing", "Floors %s-%s: %s", "Plantas %s-%s: %s"),
    ("tower.beyondsolo.tab.next", "Next climb: floor %s, %s", "Próximo ascenso: planta %s, %s"),
    ("tower.beyondsolo.tab.leave", "LEAVE THE CITADEL", "SALIR DE LA CIUDADELA"),
    ("tower.beyondsolo.tab.throne", "CHALLENGE THE THRONE AGAIN", "DESAFIAR EL TRONO DE NUEVO"),
    ("tower.beyondsolo.tab.begin", "BEGIN THE CLIMB", "EMPEZAR EL ASCENSO"),
    ("tower.beyondsolo.tab.resume", "CLIMB FROM FLOOR %s", "SUBIR DESDE LA PLANTA %s"),
    ("tower.beyondsolo.tab.spire_locked", "Opens once the lord has fallen", "Se abre cuando el señor haya caído"),
    ("tower.beyondsolo.tab.spire_best", "Best: floor %s · %s climbs", "Récord: planta %s · %s ascensos"),
    ("tower.beyondsolo.tab.spire_new", "No climb yet: how high can you go?", "Sin ascensos aún: ¿hasta dónde llegarás?"),
    ("tower.beyondsolo.tab.enter", "ENTER", "ENTRAR"),
    ("tower.beyondsolo.tab.rec.lord", "LORD SLAIN", "SEÑOR ABATIDO"),
    ("tower.beyondsolo.tab.rec.floors", "FLOORS", "PLANTAS"),
    ("tower.beyondsolo.tab.rec.falls", "FALLS", "CAÍDAS"),
    ("tower.beyondsolo.tab.rec.sigil", "SIGIL", "SELLO"),
    ("tower.beyondsolo.tab.rec.sigil_yes", "Held", "Tuyo"),
    ("tower.beyondsolo.tab.rec.sigil_no", "None", "No"),
    ("tower.beyondsolo.tab.rush", "BOSS RUSH", "ASALTO DE JEFES"),
    ("tower.beyondsolo.tab.rush_hint", "Every boss back to back, a breather between", "Todos los jefes seguidos, con un respiro entre ellos"),
    ("tower.beyondsolo.tab.rush_needs_rank", "Needs rank %s", "Requiere rango %s"),
    ("tower.beyondsolo.tab.rush_needs_sigil", "Needs the Sigil of Dominion", "Requiere el Sello de Dominio"),
    ("tower.beyondsolo.tab.rush_bouts", "%s bosses · cleared %s times", "%s jefes · superado %s veces"),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    keys = [k for k, _, _ in LINES]
    assert len(keys) == len(set(keys)), "a key is listed twice"
    for lang, col in (("en_us", 1), ("es_es", 2)):
        with open(os.path.join(OUT, f"{lang}.json"), "w", encoding="utf-8") as f:
            json.dump({row[0]: row[col] for row in LINES}, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print(f"{len(LINES)} lines in English and Spanish")


if __name__ == "__main__":
    main()
