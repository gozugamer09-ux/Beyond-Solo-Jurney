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
    "necromancer": ("Necromancer", "Speaks with the dead. Drains the life of its foes and raises their shadows as an army that grows with it."),
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
    "storm_lash": ("Storm Lash", "It never strikes just once.", "A bolt that deals %s damage and leaps to %s foes in all, a little weaker each jump."),
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
    "hawk_focus": ("Hawk's Focus", "Breathe out, release.", "+%s%% damage with projectiles."),
    "call_of_the_pack": ("Call of the Pack", "The wild answers.", "Summon %s wolves to fight at your side for 30 s."),
    "beast_bond": ("Beast Bond", "Run with the pack.", "+%s%% movement speed."),
    "snare_field": ("Snare Field", "Watch your step.", "Scatter snares for 7 s: foes inside are rooted and take %s damage per second."),
    "field_craft": ("Field Craft", "A slowed target is an easy one.", "+%s%% damage to slowed foes."),
    "tempest_volley": ("Tempest Volley", "Five arrows, five storms.", "Fire five charged shots in a fan, %s damage each, every hit arcing to a nearby foe."),
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
    "job.beyondsolo.trial.intro": "Defeat the Echoes. Face the Warden. Prove you deserve to advance.",
    "job.beyondsolo.trial.wave": "WAVE %s / %s",
    "job.beyondsolo.trial.wave_sub": "Defeat every Echo",
    "job.beyondsolo.trial.wave_clear": "WAVE %s / %s CLEARED",
    "job.beyondsolo.trial.wave_next": "More are coming...",
    "job.beyondsolo.trial.boss_near": "Something stirs at the far end of the hall.",
    "job.beyondsolo.trial.arbiter": "The Ascendant Warden",
    "job.beyondsolo.trial.arbiter_intro": "The Warden wakes.",
    "job.beyondsolo.trial.arbiter_sub": "Defeat the Warden",
    "job.beyondsolo.trial.arbiter_flawless": "Flawless so far: don't get hit, and the rare odds double",
    "job.beyondsolo.trial.arbiter_down": "The Warden falls.",
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
    "job.beyondsolo.reveal.shadows": "The shadows answer to you now. Raise your army.",
    "job.beyondsolo.reveal.skip": "Skip",
}

# the Warrior subclasses' own skill sets (WarriorPath.java)
SKILLS.update({
    # bloodrager
    "crimson_cleave": ("Crimson Cleave", "Wide, wet and red.", "Two sweeping cuts in front of you, %s damage each. Every cut builds Blood on what it hits."),
    "gore_rush": ("Gore Rush", "Straight through them.", "Rush forward through your foes for %s damage each. Every foe you pass through heals you a little."),
    "thick_blood": ("Thick Blood", "It takes a lot to drain you.", "+%s max health and +%s attack damage."),
    "blood_geyser": ("Blood Geyser", "The ground bleeds too.", "A geyser erupts where you aim: %s damage, hurls foes into the air and builds Blood."),
    "hemorrhage": ("Hemorrhage", "A wound that will not close.", "A deep cut for %s damage that keeps bleeding: %s more every second for 5 s."),
    "bloodscent": ("Bloodscent", "You can smell the weak.", "You deal %s%% more damage to foes below half health."),
    "reckless_slam": ("Reckless Slam", "It hurts you too. Worth it.", "Spend 8% of your health to smash the ground: %s damage to everything around you."),
    "blood_nova": ("Blood Nova", "Everything you have lost, given back.", "Spend 15% of your health in a burning blast: %s damage around you, up to twice as much the less health you have."),
    "pain_tolerance": ("Pain Tolerance", "You stopped noticing a while ago.", "Below half health you take %s%% less damage."),
    "last_stand": ("Last Stand", "Not yet.", "For %s s you cannot die: your health never drops below one point, and you hit much harder."),
    # warlord
    "rallying_cry": ("Rallying Cry", "On your feet!", "You and nearby allies are healed for %s and gain Regeneration and Speed for 5 s."),
    "spear_of_command": ("Spear of Command", "Where it lands, the army follows.", "Hurl a spear of light that pierces every foe in its path for %s damage and leaves them weakened."),
    "iron_discipline": ("Iron Discipline", "Drilled until it is instinct.", "+%s armor and +%s%% knockback resistance."),
    "vanguard_charge": ("Vanguard Charge", "First into the breach.", "Charge forward, throwing foes aside for %s damage. Nearby allies gain Speed II."),
    "banner_of_ruin": ("Banner of Ruin", "Nothing grows where it stood.", "Plant a burning banner where you aim for %s s: foes near it take %s damage every second and catch fire."),
    "war_drums": ("War Drums", "The beat never stops.", "You and allies within 8 blocks always have Haste %s."),
    "shield_wall": ("Shield Wall", "Lock shields.", "For %s s, you and nearby allies gain Resistance II and %s absorption hearts."),
    "execution_order": ("Execution Order", "That one. Now.", "A blade drops on your target for %s damage and marks it for 12 s: it takes %s%% more damage from everyone."),
    "spoils_of_war": ("Spoils of War", "To the victor.", "Every kill restores %s mana and %s health."),
    "conquerors_verdict": ("Conqueror's Verdict", "The war ends here.", "Call down a colossal blade where you aim: %s damage, and everything it hits is stunned."),
    # blademaster
    "iaido": ("Iaido", "The cut comes after the sword is sheathed.", "Flash through your foes. A moment later every one of them is cut for %s damage."),
    "crescent_wave": ("Crescent Wave", "The edge keeps going.", "Send a blade of wind 14 blocks ahead: %s damage to every foe it passes."),
    "keen_edge": ("Keen Edge", "Honed every single night.", "+%s attack damage."),
    "rising_moon": ("Rising Moon", "From the ground to the sky.", "An upward cut that throws foes into the air for %s damage, and you with them."),
    "blade_ward": ("Blade Ward", "Six swords, no hands.", "For %s s spectral blades circle you: Resistance, and foes that come close are cut for %s damage twice a second."),
    "riposte": ("Riposte", "Every attack is an opening.", "%s%% chance to turn a melee blow aside completely and strike back for %s damage."),
    "falling_petal": ("Falling Petal", "It lands softly. Nothing else does.", "Leap to where you aim and land in a whirl of cuts: %s damage around you."),
    "sword_rain": ("Sword Rain", "Look up.", "%s spectral swords fall on the area you aim at, %s damage each."),
    "wind_step": ("Wind Step", "Lighter on your feet than the breeze.", "+%s%% movement speed."),
    "severing_line": ("Severing Line", "One cut. Nothing more is needed.", "Focus, then cut a line 18 blocks long: %s damage, doubled against foes below a quarter of their health."),
    # juggernaut
    "seismic_stomp": ("Seismic Stomp", "The ground flinches.", "Stomp the ground: %s damage around you, tossing foes up and slowing them."),
    "boulder_toss": ("Boulder Toss", "Fetch.", "Hurl a boulder that crashes down where you aim: %s damage and a short stun."),
    "immovable": ("Immovable", "You have never taken a step back.", "+%s armor and +%s armor toughness."),
    "stone_skin": ("Stone Skin", "Harder than what hits it.", "For %s s your skin turns to stone: Resistance II and %s absorption hearts."),
    "grapple_slam": ("Grapple Slam", "Come here.", "Drag a foe to you, lift it and drive it into the ground: %s damage, and %s to everything near the impact."),
    "momentum": ("Momentum", "Hard to start. Impossible to stop.", "You deal %s%% more damage while sprinting."),
    "living_avalanche": ("Living Avalanche", "You are the rockslide.", "For 3 s you barrel forward much faster, battering everything you touch for %s damage four times a second."),
    "bulwark_taunt": ("Bulwark Taunt", "Hit me instead.", "Every creature within 10 blocks turns on you. You gain Resistance and %s absorption hearts."),
    "second_wind": ("Second Wind", "Down is not out.", "Below 30%% health you recover %s health every second."),
    "mountainfall": ("Mountainfall", "A mountain, dropped.", "Leap high and crash down where you aim: %s damage and a stun. The ground keeps shaking for 4 s, slowing foes and dealing %s damage a second."),
    # worldbreaker
    "tectonic_uppercut": ("Tectonic Uppercut", "The earth comes up with the fist.", "An uppercut that rips up the ground in front of you: %s damage, and foes are thrown sky-high."),
    "meteor_hurl": ("Meteor Hurl", "Tear out a hill and throw it.", "Hurl a burning boulder that crashes down where you aim: %s damage and sets everything ablaze."),
    "earthen_might": ("Earthen Might", "The weight of the world, in one arm.", "+%s attack damage, and your blows knock foes further back."),
    "gravity_well": ("Gravity Well", "Everything falls towards you.", "Drag every foe within 10 blocks to you, then crush them for %s damage."),
    "titan_stride": ("Titan Stride", "Each step is an earthquake.", "For %s s every second of walking sends out a shockwave: %s damage around you."),
    "aftershock": ("Aftershock", "The blow lands twice.", "Your melee hits send a shock through the ground: %s damage to other foes next to your target."),
    "continental_split": ("Continental Split", "The map needs redrawing.", "Three fissures tear outwards in front of you. Each foe caught takes %s damage and is launched."),
    "worldshaker_roar": ("Worldshaker Roar", "Mountains heard it.", "A roar that shakes everything within 12 blocks: %s damage and a 3 s stun."),
    "unbroken": ("Unbroken", "The world breaks first.", "You take %s%% less damage from everything."),
    "end_of_ages": ("End of Ages", "This is how the world ends.", "Gather the earth's fury for 2.5 s while pillars erupt around you for %s damage each, then shatter everything within 12 blocks for %s damage."),
})

# the Assassin subclasses' own skill sets (AssassinPath.java)
SKILLS.update({
    # nightblade
    "shadow_fang": ("Shadow Fang", "They feel it before they see it.", "Dash through your foes for %s damage each, half again as much from behind or against a foe that has not noticed you."),
    "dusk_shroud": ("Dusk Shroud", "The dark is a cloak.", "Vanish for %s s: foes lose track of you, and your first strike out of the shroud deals %s%% more damage."),
    "killer_instinct": ("Killer Instinct", "Strike first.", "You deal %s%% more damage to creatures that are not hunting you."),
    "throat_slit": ("Throat Slit", "Quick and quiet.", "A precise cut for %s damage that weakens and slows. Doubled against a foe that has not noticed you."),
    "umbral_decoy": ("Umbral Decoy", "That was never you.", "Leave a shadow of yourself and slip 5 blocks ahead unseen. After 2 s the shadow bursts: %s damage and blindness."),
    "nightfall_step": ("Nightfall Step", "Soft as dusk.", "+%s%% movement speed and +%s%% attack speed."),
    "black_lotus": ("Black Lotus", "It blooms once.", "Shadow blades whirl around you three times, %s damage each, blinding what they cut."),
    "night_terror": ("Night Terror", "Every nightmare at once.", "Foes within 8 blocks take %s damage, are thrown back in terror, weakened, and forget who they were hunting."),
    "finisher": ("Finisher", "No one limps away.", "You deal %s%% more damage to foes below 30%% health."),
    "endless_night": ("Endless Night", "Seven cuts, then the dark.", "Strike your target from seven sides in an instant for %s damage each, then drop a blade of night on it for %s."),
    # toxin weaver
    "venom_spit": ("Venom Spit", "Do not get it in your eyes.", "Spit venom at the foe in your sights: %s damage and a heavy dose of poison."),
    "caustic_slash": ("Caustic Slash", "The blade is the least of it.", "A coated cut in front of you for %s damage. Every foe hit gains a stack of Venom."),
    "toxic_blood": ("Toxic Blood", "Biting you is a mistake.", "Creatures that hit you in melee are poisoned (%s buildup)."),
    "spore_burst": ("Spore Burst", "Hold your breath.", "A cloud of spores bursts around you: %s damage, poison and a slow."),
    "paralytic_dart": ("Paralytic Dart", "It only stings for a second.", "A dart for %s damage that leaves the target unable to move for %s s."),
    "potency": ("Potency", "A stronger brew.", "You deal %s%% more damage to poisoned foes."),
    "plague_trail": ("Plague Trail", "Do not follow.", "For 5 s you move faster and leave pools of poison behind you: %s damage a second to foes standing in them."),
    "neurotoxin": ("Neurotoxin", "It takes three seconds to notice.", "Inject a foe. After 3 s the toxin erupts for %s damage, doubled if the foe is poisoned, and poisons everything near it."),
    "antibodies": ("Antibodies", "You have tried all of them on yourself.", "Poison no longer takes hold of you, and you take %s%% less magic damage."),
    "pandemic": ("Pandemic", "It spreads.", "Flood the area you aim at with plague for %s s: %s damage a second, and every second adds Venom and weakness."),
    # mirage dancer
    "phantom_step": ("Phantom Step", "You left something behind.", "Blink 8 blocks ahead. The afterimage you leave slashes everything near it for %s damage."),
    "gale_kick": ("Gale Kick", "The wind does the rest.", "A spinning kick for %s damage that blasts foes far away from you."),
    "featherfall": ("Featherfall", "Gravity is a suggestion.", "+%s%% movement speed, and you can fall %s blocks further unharmed."),
    "mirror_image": ("Mirror Image", "Which one is real?", "For %s s mirror images dance around you: %s%% chance that an attack hits one of them instead."),
    "cyclone_dance": ("Cyclone Dance", "The eye of the storm.", "Whirl for 1.5 s, dragging foes in and cutting them for %s damage five times."),
    "tailwind": ("Tailwind", "Always at your back.", "+%s%% attack speed."),
    "updraft": ("Updraft", "Going up.", "A blast of wind for %s damage hurls nearby foes into the air and lifts you with them. You float down slowly."),
    "afterimage_barrage": ("Afterimage Barrage", "Five of you, one after another.", "Five afterimages strike in a row ahead of you, %s damage each."),
    "slipstream": ("Slipstream", "Hard to hit what will not stay still.", "You take %s%% less damage while sprinting."),
    "tempest_waltz": ("Tempest Waltz", "The last dance.", "Flit between foes within 12 blocks, cutting twelve times for %s damage, then return in a burst of wind for %s."),
    # bounty reaper
    "chain_hook": ("Chain Hook", "No one outruns a contract.", "Hook the foe in your sights and drag it to you: %s damage and a slow."),
    "reaping_arc": ("Reaping Arc", "The harvest.", "A sweeping scythe cut and its backswing: %s damage, then half again. Marked foes take 50%% more."),
    "bounty_ledger": ("Bounty Ledger", "One name crossed off.", "Every kill gives you Speed II for %s s."),
    "crippling_shot": ("Crippling Shot", "They do not run after this.", "A thrown blade for %s damage that all but stops the target, weakens it and marks it."),
    "pursuit": ("Pursuit", "Right behind you.", "Appear behind the foe in your sights and strike for %s damage. You gain Speed II."),
    "appraise_weakness": ("Appraise Weakness", "Everyone has a price.", "You deal %s%% more damage to marked (glowing) foes."),
    "bola_snare": ("Bola Snare", "Wanted alive. Mostly.", "Throw a snare where you aim: %s damage, and everything caught is bound for 3 s and marked."),
    "collect_dues": ("Collect Dues", "Payment is due.", "A blade falls on every foe within 14 blocks for %s damage. Marked foes pay double."),
    "dead_or_alive": ("Dead or Alive", "Either way you get paid.", "+%s attack damage and +%s armor."),
    "final_contract": ("Final Contract", "Signed in blood.", "Seal a contract on your target: %s damage, and for 15 s you deal %s%% more to it. If it dies in that time you are fully healed and every skill is ready again."),
    # eclipse sovereign
    "umbral_lance": ("Umbral Lance", "A line of nothing.", "A lance of darkness pierces everything in its path for %s damage."),
    "nightfall": ("Nightfall", "The dark arrives.", "Appear where you aim in a burst of shadow: %s damage and blindness to everything around you."),
    "void_touched": ("Void Touched", "Something looked back.", "+%s attack damage and +%s%% movement speed."),
    "black_sun": ("Black Sun", "It gives no light.", "Hang a black sun where you aim for 4 s: it drags foes in for %s damage a second, then collapses for %s."),
    "moonless_veil": ("Moonless Veil", "No moon, no shadow, no you.", "For %s s you are unseen and swift, and half of all attacks pass straight through you."),
    "penumbra": ("Penumbra", "The edge of the shadow cuts too.", "%s%% chance on any hit to stun and blind the target for a moment."),
    "crescent_reaver": ("Crescent Reaver", "Two moons, crossing.", "Two enormous crossing slashes, %s damage each, 7 blocks wide."),
    "starless_prison": ("Starless Prison", "No stars to steer by.", "Chains of shadow bind every foe within 9 blocks for 3 s and deal %s damage."),
    "lord_of_night": ("Lord of Night", "The dark kneels.", "You take %s%% less damage from everything."),
    "world_without_light": ("World Without Light", "And then there was none.", "Drown everything within 14 blocks in darkness for 3 s while blades of night fall for %s damage each, then end it: %s damage to all."),
})

SKILLS.update({
    # pyromancer
    "fire_lance": ("Fire Lance", "Straight through.", "A lance of flame pierces everything in its path for %s damage and sets it alight."),
    "flame_wall": ("Flame Wall", "Nobody crosses.", "Raise a wall of fire in front of you for 5 s. Foes in it take %s damage twice a second and burn."),
    "pyromania": ("Pyromania", "It is prettier when it burns.", "You deal %s%% more damage to burning foes."),
    "combustion": ("Combustion", "Everything is fuel.", "Your target erupts for %s damage. If it was already burning it takes 60%% more and the blast spreads to foes around it."),
    "phoenix_dive": ("Phoenix Dive", "Rise. Fall. Burn.", "Leap to where you aim and land in flames: %s damage, fire and a knockback to everything around you."),
    "molten_skin": ("Molten Skin", "Do not touch.", "Fire hurts you %s%% less, and anyone who strikes you in melee catches fire."),
    "meteor_shower": ("Meteor Shower", "Look up.", "Seven meteors fall around the spot you aim at, each for %s damage in a small blast."),
    "living_bomb": ("Living Bomb", "Three. Two. One.", "Turn your target into a bomb. After 3 s it explodes for %s damage to itself and %s to everything within 5 blocks."),
    "ember_heart": ("Ember Heart", "Every death feeds the fire.", "Each kill restores %s mana."),
    "sun_fall": ("Sun Fall", "The last sunrise.", "Call down a sun where you aim. After 2 s it lands for %s damage within 10 blocks, and the ground keeps burning for 4 s."),
    # stormcaller
    "thunder_spear": ("Thunder Spear", "Faster than the sound of it.", "A bolt pierces everything in its path for %s damage and stuns it for a moment."),
    "ball_lightning": ("Ball Lightning", "It wanders. It bites.", "Send a slow orb of lightning forward for 3 s. It shocks every foe near it for %s damage four times a second."),
    "overcharge": ("Overcharge", "Too much is the right amount.", "Your hits have a %s%% chance to stun for 1 s."),
    "thunderclap": ("Thunderclap", "Cover your ears.", "A clap of thunder around you: %s damage, a knockback and a 1.5 s stun."),
    "lightning_dash": ("Lightning Dash", "You were the bolt.", "Become lightning and flash 9 blocks forward: %s damage and a stun to everything you pass through."),
    "conductivity": ("Conductivity", "The shortest path.", "You deal %s%% more damage to foes that are slowed, stunned or wet."),
    "storm_cage": ("Storm Cage", "Stay.", "Trap the spot you aim at in a cage of lightning for 4 s. Foes inside take %s damage twice a second, are slowed and cannot leave."),
    "tempest_call": ("Tempest Call", "The sky answers.", "Nine thunderbolts strike foes around you over 3 s, each for %s damage and a short stun."),
    "storm_surge": ("Storm Surge", "Always charged.", "You regain %s mana every second."),
    "judgment_of_thunder": ("Judgment of Thunder", "The verdict is loud.", "Gather the storm for 1.5 s, then drop it where you aim: %s damage and a 3 s stun within 8 blocks, and arcs for %s damage to foes farther out."),
    # tidecaller
    "water_whip": ("Water Whip", "Come here.", "Lash the foes in front of you for %s damage and drag them towards you."),
    "bubble_prison": ("Bubble Prison", "Float a while.", "Lift your target in a bubble for %s s, helpless. It takes %s damage when caught and again when the bubble bursts."),
    "undertow": ("Undertow", "The water remembers.", "Your hits slow foes for %s s."),
    "riptide": ("Riptide", "Ride it.", "Surge forward on a wave: %s damage to everything you pass, thrown aside."),
    "healing_rain": ("Healing Rain", "Let it pour.", "Rain falls around you for 6 s. Allies in it heal %s health a second and stop burning; foes are slowed."),
    "high_tide": ("High Tide", "It keeps rising.", "+%s maximum mana."),
    "maelstrom": ("Maelstrom", "Round and round.", "Open a whirlpool where you aim for 4 s. It drags foes in and deals %s damage twice a second."),
    "tsunami": ("Tsunami", "The sea visits.", "A great wave rolls 18 blocks forward: %s damage, and everything it meets is carried away."),
    "ebb_and_flow": ("Ebb and Flow", "Bend, do not break.", "You take %s%% less damage."),
    "abyssal_deluge": ("Abyssal Deluge", "The deep comes up.", "A vast whirlpool drags in everything within 12 blocks for 3 s, then erupts: %s damage and foes are hurled skyward. Allies in it heal %s."),
    # necromancer
    "bone_spear": ("Bone Spear", "Sharpened patience.", "A spear of bone pierces everything in its path for %s damage and withers it."),
    "withering_curse": ("Withering Curse", "Rot.", "Curse your target for %s s: it withers, weakens, and takes %s damage now."),
    "necrotic_might": ("Necrotic Might", "A little death in every touch.", "Your hits wither foes for %s s."),
    "grave_burst": ("Grave Burst", "They want out.", "Bones erupt from the ground where you aim: %s damage, and foes are thrown up and slowed."),
    "life_drain": ("Life Drain", "Yours is mine.", "Drain your target for 2 s: %s damage four times a second, and each pulse heals you for %s."),
    "bone_armor": ("Bone Armor", "Borrowed ribs.", "+%s armor and +%s armor toughness."),
    "spirit_swarm": ("Spirit Swarm", "They are hungry.", "Six spirits fly at foes around you, each for %s damage and withering."),
    "curse_of_frailty": ("Curse of Frailty", "Brittle things break.", "Curse the foes where you aim for 8 s: they are weakened and take %s%% more damage from everyone."),
    "deaths_embrace": ("Death's Embrace", "Closer to it, stronger for it.", "While below half health you deal %s%% more damage."),
    "requiem": ("Requiem", "The last song.", "Sing for 2 s, then every foe within 12 blocks takes %s damage, doubled against those below 20%% health. Each one struck heals you and restores mana."),
    # starborn archon
    "star_bolt": ("Star Bolt", "Three wishes.", "Three bolts of starlight strike your target for %s damage each."),
    "comet": ("Comet", "A visitor.", "A comet lands where you aim: %s damage within 4 blocks, and foes are thrown up."),
    "stellar_mind": ("Stellar Mind", "Room for a galaxy.", "+%s maximum mana."),
    "event_horizon": ("Event Horizon", "No way back.", "Blink 10 blocks forward and leave a collapsing star behind. It drags foes in for 1 s, then bursts for %s damage."),
    "constellation": ("Constellation", "Connect the dots.", "Link up to six foes around you with starlight. A moment later each takes %s damage, plus %s for every foe in the link."),
    "cosmic_flow": ("Cosmic Flow", "It never runs dry.", "You regain %s mana every second."),
    "supernova": ("Supernova", "Bright, then brighter.", "Gather light for a moment, then burst: %s damage, blindness and a hard knockback within 9 blocks."),
    "astral_aegis": ("Astral Aegis", "Starlight for armor.", "For %s s you take %s%% less damage and everyone who strikes you takes %s damage back."),
    "zenith": ("Zenith", "At the highest point.", "You deal %s%% more damage."),
    "genesis_ray": ("Genesis Ray", "Let there be light.", "Pour out a beam for 2.5 s that follows your gaze: %s damage four times a second to everything in it, then the far end bursts for %s."),
})


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
