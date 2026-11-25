#!/usr/bin/env python3
"""Makes the skills' own sounds: assets/beyondsolo/sounds/skill/*.ogg and assets/beyondsolo/sounds.json.

Every sound is layered from public-domain (CC0) recordings and a little synthesis (sub-bass drops, sweeps, electric
crackle, string plucks), so the same recipe always gives the same files. What each one is for:

    skill.impact.<element>   a skill's blow landing, in its element (10 elements, 3 takes each)
    skill.impact.heavy       the thump laid under a heavy blow
    skill.impact.massive     the boom laid under a massive one
    skill.impact.kill        the ringing crack of a creature felled by a skill
    skill.cast.<element>     a skill leaving the hunter, in its element (2 takes each)
    skill.ultimate           an ultimate called: a rush rising into a struck chord
    skill.swing / blast / slam / shot / thunder / charge
                             the skills' own versions of the vanilla sweep, TNT blast, anvil, crossbow, thunder and
                             beacon they used to borrow (swapped in by SkillSounds while a skill runs)

The recordings come from OpenGameArt.org, all released under CC0 (pages listed in sounds/skill/credits.txt). Download
them from https://opengameart.org/sites/default/files/<name> and unpack them into one folder, keeping each archive's
own folder name, and point --src at it:

    100-CC0-SFX_0.zip, sfx_100_v2.zip, 80-CC0-RPG-SFX_0.zip, 50-CC0-retro-synth-SFX.zip, sci-fi-sfx.zip,
    25-CC0-bang-sfx.zip, sfx_breaking_and_falling.zip, independent_nu_ljudbank-hits_and_punches.7z,
    sword_clash_-_starninjas_0.zip, qubodupImpact.7z, qubodupPunch.7z, qubodup-PowerDrain.7z, swoshes.7z,
    and in a folder single/: Chunky_Explosion.mp3 ("Chunky Explosion.mp3"), synthetic_explosion_1.flac,
    rain-thunder.ogg, magical_1_0.ogg, magical_2.ogg, magical_3.ogg, magical_4.ogg, magical_5.ogg, magical_7_0.ogg

Usage: python3 tools/gen_skill_sounds.py --src <packs> [--out src/main/resources/assets/beyondsolo] [--preview <dir>]
Needs numpy, scipy and ffmpeg (with libvorbis).
"""
import argparse
import json
import os
import subprocess
import sys

import numpy as np
from scipy import signal

SR = 48000
ELEMENTS = ['physical', 'fire', 'water', 'lightning', 'air', 'earth', 'shadow', 'light', 'nature', 'arcane']

SRC = None
_cache = {}


# ============================================================================ sources

def src(key):
    """A source recording, mono at 48 kHz, as floats."""
    if key in _cache:
        return _cache[key].copy()
    path = os.path.join(SRC, key)
    if not os.path.exists(path):
        sys.exit('missing source: ' + path)
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-ac', '1', '-ar', str(SR), '-f', 'f32le', '-'],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.float32).astype(np.float64)
    x -= np.mean(x)
    _cache[key] = x
    return x.copy()


HITS = 'independent_nu_ljudbank-hits_and_punches/hits/hit%02d.mp3.flac'
PUNCH = 'qubodupPunch/qubodupPunch/qubodupPunch%02d.flac'
IMPACT = 'qubodupImpact/qubodupImpact/qubodupImpact%s.flac'
SWOSH = 'swoshes/swosh-%02d.flac'
CLASH = 'sword_clash_-_starninjas_0/sword_clash.%d.ogg'
RPG = '80-CC0-RPG-SFX_0/%s.ogg'
CC0 = '100-CC0-SFX_0/%s.ogg'
V2 = 'sfx_100_v2/sfx100v2_%s.ogg'
BFH = 'sfx_breaking_and_falling/bfh1_%s.ogg'
BANG = '25-CC0-bang-sfx/%s.ogg'
SCIFI = 'sci-fi-sfx/%s.ogg'
SYNTH = '50-CC0-retro-synth-SFX/%s.ogg'
SINGLE = 'single/%s'
DRAIN = 'qubodup-PowerDrain/qubodup-PowerDrain/flac/qubodup-PowerDrain.flac'


# ============================================================================ tools

def secs(n):
    return int(round(n * SR))


def env(x, win=0.004):
    w = max(1, secs(win))
    return np.sqrt(np.convolve(x * x, np.ones(w) / w, mode='same'))


def onset(x, db=-24.0):
    """Where the sound starts: the first point within db of its loudest."""
    e = env(x)
    peak = e.max() + 1e-12
    idx = np.where(e > peak * 10 ** (db / 20))[0]
    return int(idx[0]) if len(idx) else 0


def peak_at(x):
    return int(np.argmax(env(x)))


def cut(x, start, length, fade_in=0.002, fade_out=0.04):
    """length seconds of x from sample start, faded at both ends (padded with silence if short)."""
    start = max(0, int(start))
    n = secs(length)
    y = x[start:start + n].copy()
    if len(y) < n:
        # the recording ends first: fade its own end so it doesn't click off
        f = min(secs(0.03), len(y) // 2)
        if f > 0:
            y[-f:] *= np.linspace(1, 0, f)
        y = np.concatenate([y, np.zeros(n - len(y))])
    fi, fo = min(secs(fade_in), n // 2), min(secs(fade_out), n // 2)
    if fi > 0:
        y[:fi] *= np.linspace(0, 1, fi)
    if fo > 0:
        y[-fo:] *= np.linspace(1, 0, fo) ** 2
    return y


def hit_of(key, length, pre=0.003, db=-24.0):
    """A recording cut from just before it starts (a whoosh: from the first breath of it)."""
    x = src(key)
    return cut(x, onset(x, db) - secs(pre), length)


def strike_of(key, length, pre=0.004):
    """A blow cut from just before its loudest moment, so it lands at once (some recordings swing in first)."""
    x = src(key)
    return cut(x, peak_at(x) - secs(pre), length)


def speed(x, ratio):
    """Plays x faster (ratio > 1: higher and shorter) or slower."""
    if abs(ratio - 1) < 1e-4:
        return x.copy()
    n = int(len(x) / ratio)
    return np.interp(np.arange(n) * ratio, np.arange(len(x)), x)


def sos(kind, freq, order=4):
    return signal.butter(order, freq, btype=kind, fs=SR, output='sos')


def lp(x, f, order=4):
    return signal.sosfilt(sos('lowpass', f, order), x)


def hp(x, f, order=4):
    return signal.sosfilt(sos('highpass', f, order), x)


def bp(x, lo, hi, order=4):
    return signal.sosfilt(sos('bandpass', [lo, hi], order), x)


def decay(n_or_len, tau, attack=0.002):
    n = n_or_len if isinstance(n_or_len, int) else secs(n_or_len)
    t = np.arange(n) / SR
    e = np.exp(-t / tau)
    a = min(secs(attack), n // 2)
    if a > 0:
        e[:a] *= np.linspace(0, 1, a)
    # always taper the very end, so whatever it shapes never stops on a click
    z = min(secs(0.02), n // 4)
    if z > 0:
        e[-z:] *= np.linspace(1, 0, z)
    return e


def sweep(f0, f1, length, tau, shape=0.25):
    """A sine falling (or rising) from f0 to f1 and dying away with time constant tau: the weight under a blow."""
    n = secs(length)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t / max(1e-4, shape * length))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * decay(n, tau, 0.003)


def noise(length, seed):
    return np.random.default_rng(seed).standard_normal(secs(length))


def crackle(length, seed, density=180.0, lo=1800, hi=9000):
    """Electric crackle: short bursts of bright noise switching on and off at random."""
    rng = np.random.default_rng(seed)
    n = secs(length)
    gate = np.zeros(n)
    t = 0
    while t < n:
        t += int(rng.exponential(SR / density))
        w = int(rng.uniform(0.0008, 0.006) * SR)
        gate[t:t + w] = rng.uniform(0.3, 1.0)
    return bp(rng.standard_normal(n), lo, hi) * lp(gate, 2500, 2)


def pluck(f0, length, seed, damping=0.996):
    """A plucked string (Karplus-Strong): a bowstring let go."""
    rng = np.random.default_rng(seed)
    period = int(SR / f0)
    buf = rng.uniform(-1, 1, period)
    out = np.zeros(secs(length))
    for i in range(len(out)):
        j = i % period
        out[i] = buf[j]
        buf[j] = damping * 0.5 * (buf[j] + buf[(j + 1) % period])
    return out


def reverb(x, length=0.6, tau=0.18, mix=0.25, seed=7, tone=5000):
    """A short, dark room after the sound."""
    ir = lp(noise(length, seed), tone, 2) * decay(secs(length), tau, 0.001)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    wet = signal.fftconvolve(x, ir)[:len(x) + secs(length)]
    dry = np.concatenate([x, np.zeros(len(wet) - len(x))])
    return dry + mix * wet


def sat(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


def norm(x, peak=1.0):
    m = np.max(np.abs(x)) + 1e-12
    return x * (peak / m)


def mix(length, *layers):
    """Lays (sound, at seconds, gain) layers into one of the given length."""
    out = np.zeros(secs(length))
    for x, at, gain in layers:
        a = secs(at)
        if a >= len(out):
            continue
        y = norm(x)[:len(out) - a].copy()
        if len(y) < len(x):
            # cut short by the end of the mix: fade it rather than clip it off
            fo = min(secs(0.06), len(y) // 2)
            y[-fo:] *= np.linspace(1, 0, fo) ** 2
        out[a:a + len(y)] += gain * y
    fo = secs(0.02)
    out[-fo:] *= np.linspace(1, 0, fo)
    return out


def finish(x, peak_db=-1.0, loud_db=None, tail=0.03):
    """Ready to ship: no rumble below hearing, no DC, faded out, loudness matched, peaks held under the ceiling."""
    x = hp(x, 32, 2)
    if loud_db is not None:
        head = x[:secs(0.3)]
        rms = np.sqrt(np.mean(head ** 2)) + 1e-12
        x = x * (10 ** (loud_db / 20) / rms)
    ceiling = 10 ** (peak_db / 20)
    # a soft knee instead of hard clipping on the loudest crests
    x = np.where(np.abs(x) > ceiling * 0.7, np.sign(x) * (ceiling * 0.7 + ceiling * 0.3 * np.tanh((np.abs(x) - ceiling * 0.7) / (ceiling * 0.3))), x)
    x = x * (ceiling / max(ceiling, np.max(np.abs(x)) + 1e-12))
    # trim trailing silence and fade the end
    e = env(x, 0.01)
    alive = np.where(e > 10 ** (-60 / 20))[0]
    end = min(len(x), (alive[-1] if len(alive) else len(x)) + secs(0.02))
    x = x[:max(end, secs(0.05))]
    fo = min(secs(tail), len(x) // 4)
    x[-fo:] *= np.linspace(1, 0, fo) ** 2
    return x


# ============================================================================ the sounds

def core(v, weight=1.0):
    """The body every impact shares: a punch's thud, its crack on top, and a sub drop under it."""
    hits = [10, 14, 3, 11, 15, 1]
    body = lp(strike_of(HITS % hits[v % len(hits)], 0.45), 4200)
    punch = strike_of(PUNCH % (1 + v % 5), 0.35)
    click = hp(strike_of(PUNCH % (1 + (v + 2) % 5), 0.03, pre=0.001), 2500)
    sub = sweep(150 - 10 * v, 48, 0.32, 0.09)
    return [(body, 0, 0.9 * weight), (punch, 0, 0.55), (click, 0, 0.35), (sub, 0, 0.75 * weight)]


def impact(element, v):
    seed = 100 + ELEMENTS.index(element) * 10 + v
    L = 0.75
    layers = core(v)
    if element == 'physical':
        clash = hp(strike_of(CLASH % [2, 4, 8][v], 0.35), 1800)
        slice_ = bp(noise(0.06, seed), 3000, 9000) * decay(secs(0.06), 0.015)
        metal = strike_of(BFH % ['metal_hit_04', 'metal_hit_02', 'metal_hit_05'][v], 0.3)
        layers += [(clash, 0.004, 0.45), (slice_, 0, 0.45), (metal, 0, 0.3)]
    elif element == 'fire':
        fx = src(RPG % ['spell_fire_02', 'spell_fire_06', 'spell_fire_01'][v])
        burst = cut(fx, peak_at(fx) - secs(0.03), 0.55, fade_in=0.01, fade_out=0.25)
        roar = lp(noise(0.5, seed), 900) * decay(secs(0.5), 0.12, 0.01)
        sparks = crackle(0.45, seed, density=90, lo=2500, hi=10000) * decay(secs(0.45), 0.16)
        layers += [(burst, 0, 0.7), (roar, 0, 0.5), (sparks, 0.02, 0.35)]
    elif element == 'water':
        sp = src(CC0 % ['splash_01', 'splash_02', 'splash_01'][v])
        splash = cut(speed(sp, [1.0, 1.08, 0.9][v]), onset(sp, -30) - secs(0.005), 0.55, fade_out=0.2)
        bloop = sweep(420, 140, 0.18, 0.05, shape=0.4)
        hiss = bp(noise(0.3, seed), 4000, 11000) * decay(secs(0.3), 0.07)
        layers += [(splash, 0, 0.85), (bloop, 0, 0.35), (hiss, 0, 0.2)]
    elif element == 'lightning':
        zap = crackle(0.4, seed, density=320, lo=1500, hi=11000) * decay(secs(0.4), 0.11)
        chirp = sweep(2600, 380, 0.08, 0.03, shape=0.3)
        snap = hp(strike_of(BFH % ['glass_hit_02', 'glass_hit_01', 'metal_hit_01'][v], 0.2), 3000)
        layers += [(zap, 0, 0.75), (chirp, 0, 0.35), (snap, 0, 0.4)]
    elif element == 'air':
        puff = strike_of(V2 % 'air_03', 0.3)
        whip = hp(hit_of(SWOSH % [11, 12, 15][v], 0.3, db=-30), 900)
        rush = bp(noise(0.4, seed), 700, 5000) * decay(secs(0.4), 0.09, 0.005)
        layers = core(v, weight=0.75) + [(puff, 0, 0.6), (whip, 0, 0.55), (rush, 0, 0.35)]
    elif element == 'earth':
        rock = hit_of(BFH % ['rock_breaking_01', 'rock_falling_02', 'rock_breaking_03'][v], 0.6, db=-30)
        stone = strike_of(IMPACT % 'Stone', 0.4)
        rumble = lp(noise(0.6, seed), 220) * decay(secs(0.6), 0.2, 0.01)
        layers = core(v, weight=1.2) + [(rock, 0.01, 0.6), (stone, 0, 0.6), (rumble, 0, 0.55), (sweep(90, 35, 0.5, 0.16), 0, 0.6)]
    elif element == 'shadow':
        drain = src(DRAIN)
        smear = lp(cut(drain, secs(0.3 + 0.4 * v), 0.6, fade_in=0.01, fade_out=0.3), 1800)
        whoomp = lp(speed(hit_of(SWOSH % [3, 13, 1][v], 0.6, db=-30), 0.7), 900)
        hush = bp(noise(0.5, seed), 300, 1400) * decay(secs(0.5), 0.15, 0.02)
        layers = [(lp(x, 2600), at, g) for x, at, g in core(v, weight=1.1)] + [(smear, 0, 0.5), (whoomp, 0, 0.6), (hush, 0.01, 0.3)]
    elif element == 'light':
        ping = strike_of(V2 % ['glass_05', 'glass_03', 'glass_06'][v], 0.5)
        bell = hp(strike_of(CC0 % ['bell_02', 'bell_02', 'bell_01'][v], 0.6), 1500)
        sparkle = bp(noise(0.4, seed), 6000, 14000) * decay(secs(0.4), 0.12)
        tone = sum(np.sin(2 * np.pi * f * np.arange(secs(0.5)) / SR) / (k + 1) for k, f in enumerate([1320, 1980, 2640])) * decay(secs(0.5), 0.14)
        layers = core(v, weight=0.85) + [(ping, 0, 0.45), (bell, 0, 0.4), (sparkle, 0, 0.3), (tone, 0, 0.3)]
    elif element == 'nature':
        wood = strike_of(V2 % ['wood_02', 'wood_hit_03', 'wood_04'][v], 0.4)
        snap = strike_of(RPG % ['wood_01', 'wood_03', 'wood_05'][v], 0.4)
        rustle = bp(noise(0.45, seed), 2500, 9000) * np.abs(lp(noise(0.45, seed + 1), 30, 2)) * 4 * decay(secs(0.45), 0.14)
        layers += [(wood, 0, 0.6), (snap, 0, 0.45), (rustle, 0.01, 0.35)]
    elif element == 'arcane':
        mg = src(SINGLE % ['magical_2.ogg', 'magical_3.ogg', 'magical_5.ogg'][v])
        glow = cut(mg, peak_at(mg) - secs(0.02), 0.6, fade_in=0.005, fade_out=0.3)
        laser = strike_of(SYNTH % ['synth_laser_04', 'synth_laser_01', 'synth_laser_02'][v], 0.3)
        ring = hp(strike_of(V2 % ['glass_01', 'glass_05', 'glass_03'][v], 0.4), 2000)
        layers += [(glow, 0, 0.55), (lp(laser, 6000), 0, 0.3), (ring, 0, 0.35)]
    return finish(reverb(mix(L, *layers), 0.4, 0.09, 0.18, seed), loud_db=-13)


def heavy(v):
    body = lp(speed(strike_of(HITS % [2, 21][v], 0.6), 0.85), 1600)
    thump = lp(strike_of(PUNCH % (2 + v), 0.4), 700)
    sub = sweep(105, 36, 0.55, 0.16)
    knock = lp(noise(0.25, 300 + v), 400) * decay(secs(0.25), 0.05)
    return finish(sat(mix(0.7, (body, 0, 1.0), (thump, 0, 0.6), (sub, 0, 1.1), (knock, 0, 0.4)), 1.8), loud_db=-11)


def massive(v):
    if v == 0:
        x = src(SINGLE % 'Chunky_Explosion.mp3')
    else:
        x = src(SINGLE % 'synthetic_explosion_1.flac')
    boom = lp(cut(x, onset(x) - secs(0.004), 1.6, fade_out=0.6), 3500)
    crack = hp(strike_of(BANG % ['bang_04', 'cannon_02'][v], 0.4), 1200)
    sub = sweep(72, 26, 1.4, 0.45, shape=0.2)
    debris = hit_of(BFH % ['rock_falling_09', 'rock_falling_01'][v], 0.9, db=-30)
    out = mix(1.8, (boom, 0, 1.0), (crack, 0, 0.45), (sub, 0, 1.0), (hp(debris, 1500), 0.12, 0.25))
    return finish(reverb(sat(out, 1.4), 1.0, 0.35, 0.3, 400 + v), loud_db=-10)


def kill(v):
    clash = strike_of(CLASH % [2, 4][v], 0.6)
    glass = strike_of(BFH % ['glass_hit_02', 'glass_hit_01'][v], 0.3)
    n = secs(0.7)
    t = np.arange(n) / SR
    ring = sum(np.sin(2 * np.pi * f * t) * a for f, a in [(1760, 1.0), (2637, 0.5), (3520, 0.3)]) * decay(n, 0.2)
    snap = hp(strike_of(PUNCH % (4 + v % 2), 0.08, pre=0.001), 2000)
    return finish(reverb(mix(0.8, (clash, 0, 0.8), (glass, 0, 0.4), (ring, 0.005, 0.35), (snap, 0, 0.5)), 0.5, 0.15, 0.2, 500 + v), loud_db=-14)


def cast(element, v):
    seed = 600 + ELEMENTS.index(element) * 10 + v
    sw = [15, 17, 20, 22][(v + ELEMENTS.index(element)) % 4]
    whoosh = hit_of(SWOSH % sw, 0.5, db=-30)
    if element == 'physical':
        blade = hit_of(RPG % ['blade_01', 'blade_03'][v], 0.35)
        out = mix(0.6, (whoosh, 0, 1.0), (hp(blade, 2500), 0.02, 0.4))
    elif element == 'fire':
        fx = src(RPG % ['spell_fire_02', 'spell_fire_06'][v])
        burst = cut(fx, onset(fx, -30), 0.8, fade_in=0.01, fade_out=0.35)
        out = mix(0.85, (burst, 0, 1.0), (lp(whoosh, 1500), 0, 0.5), (lp(noise(0.5, seed), 600) * decay(secs(0.5), 0.15, 0.04), 0, 0.35))
    elif element == 'water':
        sp = src(CC0 % ['splash_02', 'splash_01'][v])
        splash = lp(cut(speed(sp, 0.85), onset(sp, -30), 0.6, fade_out=0.3), 6000)
        out = mix(0.7, (whoosh, 0, 0.8), (splash, 0.03, 0.7), (bp(noise(0.4, seed), 1500, 6000) * decay(secs(0.4), 0.1, 0.03), 0, 0.25))
    elif element == 'lightning':
        zap = crackle(0.45, seed, density=260) * decay(secs(0.45), 0.14, 0.01)
        laser = hit_of(SYNTH % ['synth_laser_04', 'synth_laser_05'][v], 0.5)
        out = mix(0.6, (zap, 0, 1.0), (lp(laser, 7000), 0, 0.35), (whoosh, 0, 0.45))
    elif element == 'air':
        gust = bp(noise(0.7, seed), 500, 4500)
        gust *= np.sin(np.linspace(0, np.pi, len(gust))) ** 2
        out = mix(0.75, (gust, 0, 0.8), (hit_of(SWOSH % [24, 26][v], 0.6, db=-30), 0, 0.8))
    elif element == 'earth':
        rock = hit_of(BFH % ['rock_falling_02', 'rock_falling_04'][v], 0.5, db=-30)
        rumble = lp(noise(0.7, seed), 160) * np.sin(np.linspace(0, np.pi, secs(0.7))) ** 1.5
        out = mix(0.75, (lp(whoosh, 1200), 0, 0.7), (rock, 0.05, 0.55), (rumble, 0, 0.8), (sweep(70, 40, 0.5, 0.2), 0, 0.4))
    elif element == 'shadow':
        rev = lp(hit_of(SWOSH % [13, 3][v], 0.6, db=-30)[::-1], 1400)
        drain = src(DRAIN)
        smear = lp(cut(drain, secs(0.2 + 0.6 * v), 0.7, fade_in=0.05, fade_out=0.35), 2000)
        out = mix(0.8, (rev, 0, 0.8), (smear, 0.05, 0.55), (sweep(80, 45, 0.6, 0.25), 0.08, 0.35))
    elif element == 'light':
        mg = src(SINGLE % ['magical_5.ogg', 'magical_7_0.ogg'][v])
        shine = hp(cut(mg, onset(mg, -30), 0.8, fade_in=0.01, fade_out=0.4), 900)
        n = secs(0.7)
        t = np.arange(n) / SR
        chord = sum(np.sin(2 * np.pi * f * t + k) for k, f in enumerate([1046.5, 1318.5, 1568, 2093])) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t)) * decay(n, 0.22, 0.03)
        out = mix(0.8, (shine, 0, 0.8), (chord, 0, 0.3), (hp(whoosh, 1500), 0, 0.4))
    elif element == 'nature':
        creak = hit_of(V2 % ['wood_03', 'wood_01'][v], 0.5)
        rustle = bp(noise(0.6, seed), 2500, 9000) * np.abs(lp(noise(0.6, seed + 1), 25, 2)) * 4 * np.sin(np.linspace(0, np.pi, secs(0.6)))
        out = mix(0.7, (whoosh, 0, 0.8), (rustle, 0, 0.5), (lp(creak, 5000), 0.04, 0.35))
    else:  # arcane
        mg = src(SINGLE % ['magical_2.ogg', 'magical_3.ogg'][v])
        glow = cut(mg, onset(mg, -30), 0.8, fade_in=0.01, fade_out=0.4)
        tp = hit_of(SCIFI % 'teleport_01', 0.45, db=-30)
        out = mix(0.8, (glow, 0, 0.9), (lp(tp, 5000), 0, 0.35), (whoosh, 0, 0.4))
    return finish(reverb(out, 0.5, 0.12, 0.2, seed), loud_db=-16)


def ultimate(v):
    L = 2.2
    rise = 0.95
    n = secs(rise)
    t = np.arange(n) / SR
    # a rush rising into the call: noise opening up, a cluster of tones climbing, a sub swelling
    up = np.zeros(n)
    rng = np.random.default_rng(700 + v)
    raw = rng.standard_normal(n)
    for i in range(0, n, 2400):
        k = i / n
        seg = raw[i:i + 2400]
        up[i:i + 2400] = bp(seg, 300 + 3000 * k ** 2, 900 + 9000 * k ** 2, 2)
    # it breathes in just before the call: the rush cuts off a beat early, so the hit lands out of a hush
    gap = np.clip((rise - 0.045 - t) / 0.03, 0, 1)
    up *= (t / rise) ** 2.2 * gap
    tones = sum(np.sin(2 * np.pi * np.cumsum(f0 * (1 + 1.2 * (t / rise) ** 2)) / SR) for f0 in [110, 164.8, 220, 330]) * (t / rise) ** 3 * gap
    swell = sweep(38, 60, rise, 10.0, shape=1.0) * (t / rise) ** 2 * gap
    # the call itself: a great drum, a gong and a bright clash, ringing out
    drum = lp(strike_of(HITS % [2, 7][v], 0.9), 2000)
    gong = strike_of(CC0 % ['gong_01', 'gong_02'][v], 1.2)
    clash = hp(strike_of(CLASH % [9, 1][v], 0.8), 1500)
    sub = sweep(60, 28, 1.2, 0.4, shape=0.2)
    out = mix(L, (up, 0, 0.28), (tones, 0, 0.12), (swell, 0, 0.3), (drum, rise, 1.0), (gong, rise, 0.6), (clash, rise, 0.45), (sub, rise, 1.0))
    return finish(reverb(sat(out, 1.3), 1.2, 0.4, 0.3, 710 + v), peak_db=-1.0)


def swing(v):
    whoosh = hit_of(SWOSH % [11, 12, 17][v], 0.4, db=-30)
    blade = hp(hit_of(RPG % ['blade_02', 'blade_01', 'blade_03'][v], 0.3), 3000)
    return finish(mix(0.45, (speed(whoosh, 1.1), 0, 1.0), (blade, 0.03, 0.22)), loud_db=-15)


def blast(v):
    x = src(BANG % ['bang_06', 'cannon_04', 'bang_09'][v])
    boom = cut(x, onset(x) - secs(0.003), 1.2, fade_out=0.5)
    sub = sweep(85, 30, 1.0, 0.3, shape=0.2)
    debris = hp(hit_of(BFH % ['rock_falling_09', 'rock_falling_05', 'rock_breaking_03'][v], 0.8, db=-30), 1200)
    fire = lp(noise(0.9, 800 + v), 1200) * decay(secs(0.9), 0.25, 0.01)
    return finish(reverb(sat(mix(1.3, (boom, 0, 1.0), (sub, 0, 0.9), (debris, 0.1, 0.25), (fire, 0, 0.35)), 1.5), 0.8, 0.25, 0.25, 810 + v), loud_db=-11)


def slam(v):
    stone = strike_of(IMPACT % 'Stone', 0.4)
    body = lp(strike_of(HITS % [22, 35][v], 0.6), 1500)
    crash = strike_of(CC0 % ['slam_03', 'slam_02'][v], 0.55)
    debris = hp(hit_of(BFH % ['rock_breaking_01', 'rock_falling_08'][v], 0.8, db=-30), 900)
    sub = sweep(95, 32, 0.8, 0.22)
    return finish(reverb(sat(mix(1.0, (stone, 0, 0.7), (body, 0, 0.9), (crash, 0, 0.5), (debris, 0.04, 0.35), (sub, 0, 1.0)), 1.6), 0.6, 0.2, 0.22, 900 + v), loud_db=-11)


def shot(v):
    string = pluck([98, 116, 131][v], 0.35, 1000 + v, 0.993) * decay(secs(0.35), 0.08, 0.0005)
    snap = hp(hit_of(PUNCH % (1 + v), 0.04, pre=0.0005), 1800)
    whoosh = hp(hit_of(SWOSH % [11, 15, 20][v], 0.35, db=-30), 600)
    thwack = lp(hit_of(V2 % 'wood_hit_02', 0.2), 2500)
    return finish(mix(0.45, (string, 0, 0.7), (snap, 0, 0.5), (speed(whoosh, 1.25), 0.015, 0.55), (thwack, 0, 0.35)), loud_db=-14)


def thunder(v):
    crack = crackle(0.5, 1100 + v, density=500, lo=900, hi=12000) * decay(secs(0.5), 0.12, 0.002)
    snap = hp(noise(0.05, 1110 + v), 2000) * decay(secs(0.05), 0.01)
    x = src(SINGLE % 'rain-thunder.ogg')
    pk = peak_at(x)
    roll = lp(cut(x, pk - secs(0.05), 1.8, fade_in=0.02, fade_out=0.8), 900)
    sub = sweep(70, 30, 1.4, 0.5, shape=0.3)
    return finish(reverb(mix(2.0, (crack, 0, 0.9), (snap, 0, 0.6), (speed(roll, [1.0, 0.85][v]), 0.04, 0.9), (sub, 0.02, 0.6)), 1.0, 0.35, 0.25, 1120 + v), loud_db=-11)


def charge(v):
    mg = src(SINGLE % ['magical_1_0.ogg', 'magical_4.ogg'][v])
    swell = cut(mg, 0, 1.3, fade_in=0.02, fade_out=0.4)
    n = secs(1.0)
    t = np.arange(n) / SR
    rising = sum(np.sin(2 * np.pi * np.cumsum(np.full(n, f) * (1 + 0.5 * t)) / SR) for f in [220, 330, 440]) * (t / 1.0) ** 1.5 * decay(n, 3.0)
    hum = lp(noise(1.0, 1200 + v), 300) * (t / 1.0)
    return finish(reverb(mix(1.3, (swell, 0, 1.0), (rising, 0, 0.3), (hum, 0, 0.3)), 0.8, 0.3, 0.25, 1210 + v), loud_db=-16)


# ============================================================================ out

def write_ogg(x, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = np.clip(x, -1, 1).astype(np.float32).tobytes()
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', '1', '-i', '-',
                    '-c:a', 'libvorbis', '-q:a', '5', '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', path],
                   input=data, check=True)


def build():
    """Every sound event: name -> (subtitle key, [(file stem, samples)])."""
    events = {}

    def add(name, makers):
        events[name] = [(name.replace('skill.', '').replace('.', '_') + '_%d' % (i + 1), m) for i, m in enumerate(makers)]

    for e in ELEMENTS:
        add('skill.impact.' + e, [lambda e=e, v=v: impact(e, v) for v in range(3)])
        add('skill.cast.' + e, [lambda e=e, v=v: cast(e, v) for v in range(2)])
    add('skill.impact.heavy', [lambda v=v: heavy(v) for v in range(2)])
    add('skill.impact.massive', [lambda v=v: massive(v) for v in range(2)])
    add('skill.impact.kill', [lambda v=v: kill(v) for v in range(2)])
    add('skill.ultimate', [lambda v=v: ultimate(v) for v in range(2)])
    add('skill.swing', [lambda v=v: swing(v) for v in range(3)])
    add('skill.blast', [lambda v=v: blast(v) for v in range(3)])
    add('skill.slam', [lambda v=v: slam(v) for v in range(2)])
    add('skill.shot', [lambda v=v: shot(v) for v in range(3)])
    add('skill.thunder', [lambda v=v: thunder(v) for v in range(2)])
    add('skill.charge', [lambda v=v: charge(v) for v in range(2)])
    return events


# how far each is heard (blocks at volume 1): the default 16, more for the big ones
REACH = {'skill.impact.massive': 32, 'skill.ultimate': 32, 'skill.blast': 32, 'skill.thunder': 48, 'skill.slam': 24}


def main():
    global SRC
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--out', default='src/main/resources/assets/beyondsolo')
    ap.add_argument('--preview', default=None, help='also write each sound as .wav here, to listen to')
    ap.add_argument('--only', default=None, help='only the events whose name contains this')
    a = ap.parse_args()
    SRC = a.src
    sounds_dir = os.path.join(a.out, 'sounds', 'skill')
    table = {}
    for name, takes in build().items():
        if a.only and a.only not in name:
            continue
        entry = []
        for stem, make in takes:
            x = make()
            write_ogg(x, os.path.join(sounds_dir, stem + '.ogg'))
            if a.preview:
                os.makedirs(a.preview, exist_ok=True)
                import wave
                with wave.open(os.path.join(a.preview, stem + '.wav'), 'wb') as w:
                    w.setnchannels(1)
                    w.setsampwidth(2)
                    w.setframerate(SR)
                    w.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())
            item = {'name': 'beyondsolo:skill/' + stem}
            if name in REACH:
                item['attenuation_distance'] = REACH[name]
            entry.append(item)
            print('%-24s %-22s %.2fs' % (name, stem, len(x) / SR))
        table[name] = {'subtitle': 'subtitles.beyondsolo.' + name, 'sounds': entry}
    if not a.only:
        with open(os.path.join(a.out, 'sounds.json'), 'w') as f:
            json.dump(table, f, indent=2)
            f.write('\n')


if __name__ == '__main__':
    main()
