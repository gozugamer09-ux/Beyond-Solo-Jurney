# Translating

The System is written in English and translated to Spanish. Every asset namespace has its own pair of files:

```
src/main/resources/assets/<namespace>/lang/en_us.json   English (some are written by tools/*_lang.py)
src/main/resources/assets/<namespace>/lang/es_es.json   Spanish, kept by hand
```

The build copies each `es_es.json` to `es_mx`, `es_ar`, `es_cl`, `es_ec`, `es_uy`, `es_ve` and `esan`, so players in
Spain and Latin America all get it. Never edit or commit those copies.

## When you add or change English

1. Add the same key to the `es_es.json` next to it, in the same place in the file if you can.
2. Keep every `%s` (or `%1$s`, `%2$s`). To change their order, number them: `"%2$s de grado %1$s"`.
   A literal percent sign is `%%` when the line has arguments. Never glue `%` to a letter (`50%más` breaks the line).
3. Run the game tests (`./gradlew runGameTestServer`). `TranslationGameTests` lists any key without Spanish.

If you change an English line's meaning, fix the Spanish too: the test only checks that the line exists.

## Tone

- Talk to the player as **tú** ("Recibes", "Pulsa H"), and avoid **vosotros/ustedes** forms so it reads right on both
  sides of the Atlantic: rephrase "you and your allies take" as "recibes ..., igual que los aliados ...".
- The player is masculine when Spanish forces a gender ("Registrado como cazador").
- **Title Case** for names of things (skills, items, sets, bosses: "Lluvia de Meteoros", "Yelmo del Rompemundos").
  Sentence case for interface text ("Faltan materiales").
- Interface labels in CAPITALS stay in capitals ("TIENDA", "INFUSIÓN COMPLETA"). Accents stay on capitals ("MÁX.").
- Skill text reads like the English: short, second person, present tense. "%s de daño", "durante %s s",
  "en un radio de 8 bloques", "un %s%% más de daño", "un %s%% menos de daño". Decimals use a comma ("1,5 s").
- Keep adjectives that would need a gender out of templates. When a part changes with the creature or item, give it
  a form that never agrees ("de Escarcha", "en su forma ascendente") or a noun ("Blindaje", "Rapidez").

## Word list

| English | Spanish |
|---|---|
| System | Sistema |
| Player / Hunter | Jugador / Cazador |
| Gate | Portal |
| Dungeon | Mazmorra |
| Boss | Jefe |
| Rank (E-S) | Rango ("Rango %s", "de rango %s") |
| Level, Lv. | Nivel, Nv. |
| Class / Subclass / Job change | Clase / Subclase / Cambio de clase |
| Warrior, Assassin, Mage, Guardian, Healer, Ranger | Guerrero, Asesino, Mago, Guardián, Sanador, Explorador |
| Necromancer | Nigromante |
| Stats STR, AGI, VIT, INT, SENSE | FUE, AGI, VIT, INT, PER |
| HP / MP / XP | HP / MP / XP (kept) |
| Mana | Maná |
| Fatigue / Exhaustion / Collapse | Fatiga / Agotamiento / Colapso |
| Quest / Daily Quest / villager request | Misión / Misión diaria / Encargo |
| Penalty / Penalty Zone | Castigo / Zona de Castigo |
| Pop-up | Aviso |
| Log | Registro |
| Evaluation / Re-evaluation | Evaluación / Reevaluación |
| Awakening / awakened | Despertar / despertado |
| Skill / Active / Passive | Habilidad / Activa / Pasiva |
| Cooldown | Enfriamiento |
| Mastery / Stack(s) | Dominio / Acumulación(es) |
| Signature art / loot | Arte insignia / Botín insignia |
| Rune Stone / Engraving | Piedra Rúnica / Grabado |
| Monster Core / Mana Stone | Núcleo de Monstruo / Piedra de Maná |
| Dungeon Key / run / chain / re-run | Llave de Mazmorra / incursión / cadena / repetir |
| Dungeon Break / Unstable Gate / Break Imminent | Ruptura de Mazmorra / Portal Inestable / Ruptura Inminente |
| Suppressed (a break) / held from inside | Contenida / contenido desde dentro |
| Red Gate / the Red Winter / seal (sealed) / the way out | Portal Rojo / el Invierno Rojo / sello (sellado) / la salida |
| master of a red gate's land / camp / blizzard / chill, freezing | señor (de esta tierra) / campamento / ventisca / frío, congelarse |
| sense a gate (Pulse Scan) | detectar un portal |
| Directions: north, northeast, ... ("to the %s") | norte, noreste, ... ("al %s", from `npc.beyondsolo.dir.*`) |
| Shadow Army / extract / raise | Ejército de Sombras / extraer / alzar |
| Gold (G) | Oro (G) |
| Storage / Shop / Hunter License | Almacén / Tienda / Licencia de Cazador |
| Forge / Refine / Infusion | Forja / Refinar / Infusión |
| Set / piece / 2 pieces | Conjunto / pieza / 2 piezas |
| Grades: Common, Rare, Epic, Legendary, Mythic, Divine | Común, Raro, Épico, Legendario, Mítico, Divino |
| Elements: Physical, Fire, Water, Lightning, Air, Earth, Shadow, Light, Nature, Arcane | Físico, Fuego, Agua, Rayo, Aire, Tierra, Sombra, Luz, Naturaleza, Arcano |
| Burn / Deep Burn | Quemadura / Quemadura Profunda |
| Venom / Heavy Venom | Ponzoña / Ponzoña Letal |
| Crimson / Crimson Brand | Carmesí / Marca Carmesí |
| Blood / bleeding | Sangre / sangrado |
| Frostbite | Congelación |
| Stun / slow / knockback | aturdimiento / ralentización / retroceso |
| Haste / Speed / Resistance / Regeneration / Strength | Prisa / Velocidad / Resistencia / Regeneración / Fuerza |
| Absorption hearts | corazones de absorción |
| Veteran / Elite / Champion | Veterano / Élite / Campeón |
| Ascended / Ascendant | Ascendente |
| Hollow / Ashen / Gloom / Mire / Ember | Hueco / Ceniciento / Umbrío / Cenagal / Ascuas |
| Gloomfang, Broodling, Shardback, Hollow Acolyte, Ashen Sentinel, Emberwisp | Sabueso Umbrío, Cría de la Prole, Lomo de Esquirlas, Acólito Hueco, Centinela Ceniciento, Ascua Fatua |
| Gutterkin, Grave Thrall, Bone Archer, Rime Stalker, Tuskbrute, Tusk Shaman, Deepkin | Trasgo del Albañal, Siervo Sepulcral, Arquero de Hueso, Acechador Gélido, Bruto Colmilludo, Chamán Colmilludo, Morador Abisal |
| Duskwing, Hive Drone, Brimling, Wailing Wraith, Static Drifter, Rimeclaw | Ala del Ocaso, Zángano de la Colmena, Diablillo de Azufre, Espectro Plañidero, Errante Estático, Zarpa Gélida |
| Pit Ravager, Coilfang, Cragcrawler, Hive Reaver, Rune Colossus, Thorn Spitter | Devastador del Foso, Colmillo Enroscado, Reptador de Riscos, Segador de la Colmena, Coloso Rúnico, Escupespinas |
| Monarch / crown / the empty throne / Domain (a monarch's power) / Dread / Crown Form / Authority | Monarca / corona / el trono vacío / Dominio del Monarca ("Dominio" alone is Mastery and a world boss's domain) / Pavor / Forma Coronada / Autoridad |
| Night, Winter, Cinders, Thunder (the crowns) | Noche, Invierno, Cenizas, Trueno ("Monarca de la Noche") |
| Shift + click | Mayús + clic |

Subclasses, sets, bosses and skills already have names in `es_es.json`; search there before naming something new so
the same English word keeps the same Spanish word.
