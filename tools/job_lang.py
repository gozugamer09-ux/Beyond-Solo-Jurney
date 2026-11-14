#!/usr/bin/env python3
"""
Writes the job change module's English texts to src/main/resources/assets/beyondsolo_jobs/lang/en_us.json.
(Minecraft loads lang files from every namespace, so the module keeps its texts apart from the shared file.)

Run from the repo root:  python3 tools/job_lang.py
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/beyondsolo_jobs/lang/en_us.json")

SUBCLASSES = {
    # warrior
    "bloodrager": ("Bloodrager", "Trades blood for fury: the more it bleeds, the harder it hits, and every strike drinks some life back."),
    "warlord": ("Warlord", "A commander on the battlefield. Plants banners that steel allies and break the enemy's will."),
    "blademaster": ("Blademaster", "Pure sword discipline. A storm of cuts faster than the eye can follow."),
    "juggernaut": ("Juggernaut", "Cannot be stopped, cannot be moved. Charges through anything in its path."),
    "worldbreaker": ("Worldbreaker", "One in a hundred warriors. Its fists split the earth and its blood runs like a titan's."),
    # assassin
    "nightblade": ("Nightblade", "Strikes from behind in the dark and finishes what is already wounded."),
    "toxin_weaver": ("Toxin Weaver", "Weaves poison into the air and into every cut."),
    "mirage_dancer": ("Mirage Dancer", "Never where the enemy aims. Blinks from foe to foe in a flurry of afterimages."),
    "bounty_reaper": ("Bounty Reaper", "Marks a target, hunts it down, and is paid in full when it falls."),
    "eclipse_sovereign": ("Eclipse Sovereign", "One in a hundred assassins. Swallows the battlefield in darkness and rends everything inside it."),
    # mage
    "pyromancer": ("Pyromancer", "Calls pillars of fire from the ground and sets every foe it touches alight."),
    "stormcaller": ("Stormcaller", "Lightning that leaps from foe to foe, and a body that hums with mana."),
    "tidecaller": ("Tidecaller", "Commands crashing waves that sweep enemies away, and a deep well of mana."),
    "necromancer": ("Necromancer", "Speaks with the dead. Drains the life of its foes, and one day will raise an army of shadows."),
    "starborn_archon": ("Starborn Archon", "One in a hundred mages. Pulls stars from the sky and folds space into a crushing void."),
    # guardian
    "paladin": ("Paladin", "A holy shield. Smites with light, mends itself, and is deadly to the undead."),
    "ironwall_sentinel": ("Ironwall Sentinel", "Roots itself like a fortress and turns every blow back on the attacker."),
    "spellward_warden": ("Spellward Warden", "A barrier against magic that turns the damage it takes into mana."),
    "vengeance_knight": ("Vengeance Knight", "Stores up every wound and returns it twice over in a burst of fire."),
    "everlasting_colossus": ("Everlasting Colossus", "One in a hundred guardians. Grows to a giant's size and refuses to fall."),
    # healer
    "lightbinder": ("Lightbinder", "Bursts of radiant light that heal allies and burn foes."),
    "grovekeeper": ("Grovekeeper", "Grows healing gardens that root enemies in place."),
    "spring_oracle": ("Spring Oracle", "A cleansing tide that washes away poison and curses, and a step ahead of every blow."),
    "hemomancer": ("Hemomancer", "Heals by bleeding others: siphons life straight from the enemy's veins."),
    "dawn_saint": ("Dawn Saint", "One in a hundred healers. A miracle in human form: restores everyone and judges the dead."),
    # ranger
    "sharpshooter": ("Sharpshooter", "One shot, one kill. Piercing shots from impossible range."),
    "wildcaller": ("Wildcaller", "Fights alongside a pack of wolves that answers its call."),
    "snare_artisan": ("Snare Artisan", "Lays fields of snares and punishes anything caught in them."),
    "storm_archer": ("Storm Archer", "Volleys of arrows charged with lightning that arcs between foes."),
    "heavenpiercer": ("Heavenpiercer", "One in a hundred rangers. An arrow that splits the sky, and a storm of a thousand more."),
}

# path: (name, flavour, description with %s numbers)
SKILLS = {
    "blood_frenzy": ("Blood Frenzy", "Pain is fuel.", "Spend 10% of your health to rage for %s s: Strength %s, Speed I, and melee hits heal you for %s%% of the damage."),
    "rage_engine": ("Rage Engine", "The closer to death, the stronger.", "Below half health you deal %s%% more damage."),
    "banner_of_conquest": ("Banner of Conquest", "Hold the line.", "Plant a war banner for %s s: allies nearby gain Strength and Resistance, foes are weakened and slowed."),
    "commanding_presence": ("Commanding Presence", "Soldiers stand taller beside you.", "+%s max health and +%s armor toughness."),
    "thousand_cuts": ("Thousand Cuts", "Five strikes in one breath.", "Five rapid slashes in front of you, %s damage each."),
    "flowing_blade": ("Flowing Blade", "The sword never rests.", "+%s%% attack speed."),
    "unstoppable_charge": ("Unstoppable Charge", "Get out of the way.", "Charge %s blocks forward, hurling everything in your path into the air for %s damage."),
    "iron_hide": ("Iron Hide", "Skin like plate.", "+%s max health and +%s%% knockback resistance."),
    "cataclysm_fist": ("Cataclysm Fist", "The ground remembers this blow.", "Leap and crash down: %s damage in a huge circle, foes are launched and stunned, then two aftershocks roll out."),
    "fault_line": ("Fault Line", "The earth splits on command.", "The ground erupts in a line ahead of you, %s damage to everything it throws up."),
    "titan_blood": ("Titan Blood", "Born to break worlds.", "+%s%% damage with everything and +%s max health."),
    "umbral_execution": ("Umbral Execution", "From the shadow behind them.", "Blink behind the creature you look at and strike for %s damage; 2.5x on targets under 30% health."),
    "night_hunter": ("Night Hunter", "The dark is your ally.", "+%s%% damage while standing in darkness."),
    "miasma_cloud": ("Miasma Cloud", "Breathe deep.", "A poison cloud for %s s: %s damage per second and heavy poison buildup."),
    "virulence": ("Virulence", "Every cut festers.", "Melee hits add %s poison buildup."),
    "mirage_flurry": ("Mirage Flurry", "Which one is real?", "Blink between up to %s nearby foes, striking each for %s damage."),
    "evasive_step": ("Evasive Step", "Already gone.", "%s%% chance to dodge an attack completely."),
    "death_sentence": ("Death Sentence", "The contract is signed.", "Mark a creature for 10 s: it takes %s%% more damage from you. If it dies, Death Sentence is ready again and you gain 20 mana."),
    "opening_strike": ("Opening Strike", "The first cut decides.", "+%s%% damage to targets at 90% health or more."),
    "total_eclipse": ("Total Eclipse", "The sun goes out.", "Darkness swallows everything within 10 blocks for 8 s: foes are blinded, slowed and cut for %s damage each second; you vanish and deal 40% more."),
    "void_rend": ("Void Rend", "Eight cuts, one heartbeat.", "Tear through up to 8 foes nearby, striking each for %s damage, then return to where you stood."),
    "sovereign_of_dusk": ("Sovereign of Dusk", "Night bows to you.", "+%s%% damage and +%s%% movement speed."),
    "inferno_pillar": ("Inferno Pillar", "The ground becomes a furnace.", "A pillar of fire erupts where you look: %s damage, heavy burn buildup, and foes are thrown up."),
    "kindled_soul": ("Kindled Soul", "Everything you touch burns.", "Your hits add %s burn buildup."),
    "chain_lightning": ("Chain Lightning", "It never strikes just once.", "A bolt that deals %s damage and leaps to %s foes in all, a little weaker each jump."),
    "static_charge": ("Static Charge", "Mana crackles in your veins.", "Restore %s mana every second."),
    "tidal_crash": ("Tidal Crash", "The sea answers.", "A wave crashes ahead: %s damage, foes swept back and slowed, and you recover a little health."),
    "deep_current": ("Deep Current", "A well without a bottom.", "+%s max mana."),
    "soul_harvest": ("Soul Harvest", "Their lives are yours.", "Drain every foe around you for %s damage; heal %s and restore 3 mana for each one hit."),
    "grave_pact": ("Grave Pact", "The dead pay their dues.", "Each kill heals %s and restores %s mana."),
    "astral_cataclysm": ("Astral Cataclysm", "Bring down the heavens.", "%s stars fall where you look, %s damage each."),
    "singularity": ("Singularity", "Space folds inward.", "A point of void pulls in every foe within 9 blocks for 4 s, then collapses for %s damage."),
    "celestial_core": ("Celestial Core", "A star burns inside you.", "+%s max mana, mana regeneration, and +%s%% damage with spells."),
    "holy_judgment": ("Holy Judgment", "Light passes sentence.", "Smite the creature you look at for %s damage (more against the undead) and heal yourself."),
    "devotion": ("Devotion", "Faith is armor.", "+%s armor; below half health you regenerate %s health per second."),
    "fortress_stance": ("Fortress Stance", "Not one step back.", "Root yourself for %s s: Resistance III, and attackers take 30% of their damage back."),
    "unyielding": ("Unyielding", "Stone does not bend.", "+%s armor toughness and +%s armor."),
    "mana_barrier": ("Mana Barrier", "Their magic feeds you.", "A barrier of %s absorption health for 12 s; half the damage you take while it lasts comes back as mana."),
    "arcane_ward": ("Arcane Ward", "Spells slide off you.", "Take %s%% less damage from magic."),
    "retribution": ("Retribution", "Every wound returned.", "For 6 s you store the damage you take, then explode for twice that plus %s, setting foes alight."),
    "burning_spite": ("Burning Spite", "Touch me and burn.", "Creatures that hit you get %s burn buildup."),
    "colossus_form": ("Colossus Form", "Rise.", "Grow to a giant for %s s: +%s max health, much stronger blows, and nothing can push you."),
    "earthen_domain": ("Earthen Domain", "This ground is mine.", "A quake through everything within 12 blocks: %s damage, a long stun, and every foe turns on you while you shrug it off."),
    "undying": ("Undying", "Not today.", "A blow that would kill you leaves you at half health instead (once every %s s). +%s max health."),
    "radiant_burst": ("Radiant Burst", "Light in every direction.", "Heal allies around you for %s and burn foes for the same (more against the undead)."),
    "blessed_aura": ("Blessed Aura", "Those near you mend.", "You and allies near you regenerate %s health per second."),
    "verdant_bloom": ("Verdant Bloom", "Life grows where you stand.", "A garden for %s s: allies inside heal %s per second, foes are rooted."),
    "photosynthesis": ("Photosynthesis", "Sunlight is medicine.", "Regenerate %s health per second under the open sky by day."),
    "cleansing_tide": ("Cleansing Tide", "Wash it all away.", "A tide that cleanses poison, curses and burns from allies around you and heals them for %s, pushing foes back."),
    "foresight": ("Foresight", "You saw it coming.", "+%s max mana and %s%% chance to dodge an attack."),
    "blood_siphon": ("Blood Siphon", "Your blood, my strength.", "Drain the creature you look at for %s damage and blood buildup, healing half of it."),
    "crimson_vigor": ("Crimson Vigor", "Blood calls to blood.", "+%s max health; your hits add %s blood buildup."),
    "miracle_of_dawn": ("Miracle of Dawn", "A new day for everyone.", "Fully heals and cleanses every ally within 16 blocks and shields them; the undead nearby take %s damage."),
    "judgment_of_dawn": ("Judgment of Dawn", "The light finds them all.", "%s pillars of light strike the foes around you, %s damage each."),
    "eternal_grace": ("Eternal Grace", "Blessed beyond death.", "Regenerate %s health per second, and survive a killing blow once every %s s."),
    "deadeye_shot": ("Deadeye Shot", "Don't blink.", "A piercing shot along your sight, %s damage to everything in the line."),
    "steady_aim": ("Steady Aim", "Breathe out, release.", "+%s%% damage with projectiles."),
    "call_of_the_pack": ("Call of the Pack", "The wild answers.", "Summon %s wolves to fight at your side for 30 s."),
    "beast_bond": ("Beast Bond", "Run with the pack.", "+%s%% movement speed."),
    "snare_field": ("Snare Field", "Watch your step.", "Scatter snares for 7 s: foes inside are rooted and take %s damage per second."),
    "field_craft": ("Field Craft", "A slowed target is an easy one.", "+%s%% damage to slowed foes."),
    "thunder_volley": ("Thunder Volley", "Five arrows, five storms.", "Fire five charged shots in a fan, %s damage each, every hit arcing to a nearby foe."),
    "charged_quiver": ("Charged Quiver", "Every arrow hums.", "+%s%% projectile damage and %s%% chance for a hit to spark for extra damage."),
    "heavens_arrow": ("Heaven's Arrow", "The sky splits in two.", "One arrow that pierces everything for 80 blocks, %s damage."),
    "storm_of_a_thousand": ("Storm of a Thousand", "Look up.", "%s arrows rain down on a wide area where you look."),
    "eagle_sovereign": ("Eagle Sovereign", "Master of the sky.", "+%s%% projectile damage and +%s%% movement speed."),
}

TEXT = {
    "item.beyondsolo.ascension_key": "Key of Ascension",
    "item.beyondsolo.ascension_key.lore": "A key the System handed you. It opens a door that is not there.",
    "item.beyondsolo.ascension_key.use": "Use: begin the Trial of Ascension",
    "entity.beyondsolo.echo": "Echo",
    "quest.beyondsolo.job_ascension": "Job Change: Trial of Ascension",
    "quest.beyondsolo.job_ascension.desc": "You have grown enough to advance. Use the Key of Ascension and clear the trial alone. What you become will be decided there.",
    "quest_objective.beyondsolo.job_trial": "Clear the Trial of Ascension",
    "notify.beyondsolo.job_unlocked.title": "[Job Change]",
    "notify.beyondsolo.job_unlocked.body": "You qualify for a job change. Use the Key of Ascension when you are ready.",
    "notify.beyondsolo.job_countdown.title": "Trial of Ascension",
    "notify.beyondsolo.job_countdown.body": "Entering the trial. You will face it alone.",
    "notify.beyondsolo.job_changed.title": "Job change complete",
    "notify.beyondsolo.job_changed.body": "%s has advanced to %s. New skills unlocked.",
    "notify.beyondsolo.job_done.title": "Job change",
    "notify.beyondsolo.job_done.body": "You have already changed jobs.",
    "notify.beyondsolo.job_locked.title": "Job change",
    "notify.beyondsolo.job_locked.body": "Reach level %s first.",
    "notify.beyondsolo.job_wait.title": "Trial of Ascension",
    "notify.beyondsolo.job_wait.body": "The trial is sealed for %s:%s more.",
    "notify.beyondsolo.job_not_here.title": "Trial of Ascension",
    "notify.beyondsolo.job_not_here.body": "Not inside a gate.",
    "notify.beyondsolo.job_returned.title": "Trial of Ascension",
    "notify.beyondsolo.job_returned.body": "You left the trial. You can try again.",
    "chat.beyondsolo.job_rare": "The System trembles: %s has become a %s!",
    "job.beyondsolo.trial.system": "THE SYSTEM",
    "job.beyondsolo.trial.title": "TRIAL OF ASCENSION",
    "job.beyondsolo.trial.intro": "Defeat the Echoes. Face the Arbiter. Prove you deserve to advance.",
    "job.beyondsolo.trial.wave": "WAVE %s / %s",
    "job.beyondsolo.trial.wave_sub": "Defeat every Echo",
    "job.beyondsolo.trial.wave_clear": "WAVE %s / %s CLEARED",
    "job.beyondsolo.trial.wave_next": "More are coming...",
    "job.beyondsolo.trial.boss_near": "Something stirs at the far end of the hall.",
    "job.beyondsolo.trial.arbiter": "The Arbiter of Ascension",
    "job.beyondsolo.trial.arbiter_intro": "The Arbiter wakes.",
    "job.beyondsolo.trial.arbiter_sub": "Defeat the Arbiter",
    "job.beyondsolo.trial.arbiter_flawless": "Flawless so far: don't get hit, and the rare odds double",
    "job.beyondsolo.trial.arbiter_down": "The Arbiter falls.",
    "job.beyondsolo.trial.failed": "TRIAL FAILED",
    "job.beyondsolo.trial.failed_sub": "The System casts you out. You may try again in %s minutes.",
    "job.beyondsolo.reveal.title": "JOB CHANGE",
    "job.beyondsolo.reveal.sub": "%s: choosing your path",
    "job.beyondsolo.reveal.subclass": "Subclass",
    "job.beyondsolo.reveal.rare": "EXTREMELY RARE",
    "job.beyondsolo.reveal.rare_chip": "Extremely rare",
    "job.beyondsolo.reveal.flawless": "Flawless trial",
    "job.beyondsolo.reveal.skills": "New skills",
    "job.beyondsolo.reveal.active": "Active",
    "job.beyondsolo.reveal.passive": "Passive",
    "job.beyondsolo.reveal.shadows": "The shadows answer to you now. Their army will come.",
    "job.beyondsolo.reveal.skip": "Skip",
}


def main():
    out = dict(TEXT)
    for key, (name, desc) in SUBCLASSES.items():
        out[f"subclass.beyondsolo.{key}"] = name
        out[f"subclass.beyondsolo.{key}.desc"] = desc
    for path, (name, flavour, desc) in SKILLS.items():
        out[f"skill.beyondsolo.{path}"] = name
        out[f"skill.beyondsolo.{path}.flavour"] = flavour
        out[f"skill.beyondsolo.{path}.desc"] = desc
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(len(out), "strings")


if __name__ == "__main__":
    main()
