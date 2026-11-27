#!/usr/bin/env python3
"""Generates Omnispell's textures, models, names, recipes and enchantments.

Run from the repository root:  python3 tools/gen_assets.py
Wand woods and cores are read from WandParts.java so there is one list to edit.
"""
import io, json, os, re
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', 'omnispell')
DATA = os.path.join(ROOT, 'data', 'omnispell')

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        if isinstance(obj, str): f.write(obj)
        else: json.dump(obj, f, indent=2)

def image(name, rows, palette, folder='item'):
    """rows: 16 strings of 16 characters; palette: char -> (r,g,b,a)."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette: img.putpixel((x, y), palette[ch])
    path = os.path.join(ASSETS, 'textures', folder, name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

# ---------------------------------------------------------------- textures
# Wand: a diagonal shaft (tinted by the wood) with a gem at the tip (tinted by the core).
image('wand_wood', [
    '................',
    '................',
    '................',
    '..........ba....',
    '.........bab....',
    '........bab.....',
    '.......bab......',
    '......bab.......',
    '.....bab........',
    '....bab.........',
    '...bcb..........',
    '..bcb...........',
    '.bcb............',
    '.bb.............',
    '................',
    '................'], {'a': (255, 255, 255, 255), 'b': (150, 150, 150, 255), 'c': (205, 205, 205, 255)})
image('wand_core', [
    '................',
    '............ab..',
    '...........abcb.',
    '..........abcb..',
    '...........bb...',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................'], {'a': (255, 255, 255, 255), 'b': (170, 170, 170, 255), 'c': (225, 225, 225, 255)})
image('grimoire', [
    '................',
    '..dddddddddd....',
    '.dpppppppppgd...',
    '.dpllppppllgd...',
    '.dplppyyppllgd..',
    '.dpppyppypplgd..',
    '.dpppyppyppgd...',
    '.dppppyyypppgd..',
    '.dpppyppypppgd..',
    '.dplpyppyplpgd..',
    '.dpllppppllpgd..',
    '.dpppppppppgd...',
    '.dwwwwwwwwwwd...',
    '.dddddddddddd...',
    '................',
    '................'], {'d': (38, 20, 66, 255), 'p': (92, 52, 150, 255), 'l': (130, 88, 196, 255),
                         'g': (58, 30, 100, 255), 'y': (250, 214, 110, 255), 'w': (232, 224, 205, 255)})
image('word_scroll', [
    '................',
    '..bbbbbbbbbbb...',
    '.bhhhhhhhhhhhb..',
    '.bbbbbbbbbbbbb..',
    '..spppppppppps..',
    '..sprrprrrpps...',
    '..sppppppppps...',
    '..sprrrprrpps...',
    '..sppppppppps...',
    '..sprrprrrpps...',
    '..sppppppppps...',
    '..spprrrprpps...',
    '.bbbbbbbbbbbbb..',
    '.bhhhhhhhhhhhb..',
    '..bbbbbbbbbbb...',
    '................'], {'b': (112, 78, 46, 255), 'h': (168, 126, 78, 255), 'p': (236, 222, 184, 255),
                         's': (196, 178, 136, 255), 'r': (104, 62, 168, 255)})
image('arcane_potion', [
    '................',
    '......kkkk......',
    '......kcck......',
    '......gkkg......',
    '.....g....g.....',
    '....g..ww..g....',
    '...g.aaaaaa.g...',
    '..g.abbbbbba.g..',
    '..g.bbwbbbbb.g..',
    '..g.bbbbbmbb.g..',
    '..g.bmbbbbbb.g..',
    '..g.bbbbbbbb.g..',
    '...g.bbbbbb.g...',
    '....gggggggg....',
    '................',
    '................'], {'k': (120, 86, 52, 255), 'c': (160, 120, 78, 255), 'g': (190, 220, 235, 255),
                         'a': (150, 235, 255, 255), 'b': (64, 190, 235, 255), 'w': (255, 255, 255, 255),
                         'm': (172, 120, 255, 255)})
image('arcane_well_side', [
    'ssssssssssssssss',
    'sllllslllllsllls',
    'sddddsdddddsddds',
    'ssssssssssssssss',
    'llsllllrllllslll',
    'ddsdddrprdddsddd',
    'sssssrpppprsssss',
    'slllrpplpprrllls',
    'sdddrpppplprddds',
    'ssssssrpprssssss',
    'llslllrpprllslll',
    'ddsddddrrdddsddd',
    'ssssssssssssssss',
    'sllllslllllsllls',
    'sddddsdddddsddds',
    'ssssssssssssssss'], {'s': (54, 52, 66, 255), 'l': (108, 104, 124, 255), 'd': (84, 80, 98, 255),
                          'r': (118, 72, 190, 255), 'p': (180, 132, 255, 255)}, 'block')
image('arcane_well_top', [
    'ssssssssssssssss',
    'sllllllllllllls.'[:15] + 's',
    'slddddddddddddls'[:16],
    'sldwwwwwwwwwwdls',
    'sldwaaabbaaawdls',
    'sldwaabbbbaawdls',
    'sldwabbccbbawdls',
    'sldwbbcmmcbbwdls',
    'sldwbbcmmcbbwdls',
    'sldwabbccbbawdls',
    'sldwaabbbbaawdls',
    'sldwaaabbaaawdls',
    'sldwwwwwwwwwwdls',
    'slddddddddddddls',
    'slllllllllllllls',
    'ssssssssssssssss'], {'s': (54, 52, 66, 255), 'l': (108, 104, 124, 255), 'd': (84, 80, 98, 255),
                          'w': (40, 30, 80, 255), 'a': (56, 120, 210, 255), 'b': (64, 190, 235, 255),
                          'c': (150, 235, 255, 255), 'm': (236, 250, 255, 255)}, 'block')
image('arcane_well_bottom', ['s' * 16] + ['s' + 'ld' * 7 + 's'] * 14 + ['s' * 16],
      {'s': (54, 52, 66, 255), 'l': (96, 92, 110, 255), 'd': (84, 80, 98, 255)}, 'block')

# Waypoints: a carved stone with a glowing band and gem, in twenty colours.
java_wp = open(os.path.join(ROOT, '..', 'java', 'com', 'omnispell', 'waypoint', 'Waypoints.java')).read()
WP_COLORS = [int(c, 16) for c in re.findall(r'0x([0-9A-Fa-f]{6})', java_wp.split('COLORS = {')[1].split('}')[0])]
assert len(WP_COLORS) == 20, len(WP_COLORS)
def shade(c, f):
    return (min(255, int((c >> 16 & 255) * f)), min(255, int((c >> 8 & 255) * f)), min(255, int((c & 255) * f)), 255)
WP_SIDE = [
    'ssssssssssssssss',
    'sllllllllllllls.'[:15] + 's',
    'sldddddddddddlds'[:16],
    'ssssssssssssssss',
    'cccccccccccccccc',
    'bbbbbbbbbbbbbbbb',
    'ssssssssssssssss',
    'slllsllllllsllls',
    'sdddsdddbddsddds',
    'sssssssbcbssssss',
    'sllllsbcccbsllls',
    'sddddsdbcbdsddds',
    'ssssssssbsssssss',
    'slllsllllllsllls',
    'sdddsddddddsddds',
    'ssssssssssssssss']
WP_TOP = [
    'ssssssssssssssss',
    'sllllllllllllls.'[:15] + 's',
    'sldddddddddddlds'[:16],
    'sldssssssssssdls',
    'sldsbbbbbbbbsdls',
    'sldsbccccccbsdls',
    'sldsbcwwwwcbsdls',
    'sldsbcwmmwcbsdls',
    'sldsbcwmmwcbsdls',
    'sldsbcwwwwcbsdls',
    'sldsbccccccbsdls',
    'sldsbbbbbbbbsdls',
    'sldssssssssssdls',
    'slddddddddddddls',
    'slllllllllllllls',
    'ssssssssssssssss']
for i, c in enumerate(WP_COLORS):
    pal = {'s': (62, 62, 72, 255), 'l': (122, 122, 136, 255), 'd': (96, 96, 108, 255),
           'c': shade(c, 1.0), 'b': shade(c, 0.6), 'w': shade(c, 1.25), 'm': (255, 255, 255, 255)}
    image(f'waypoint_side_{i}', WP_SIDE, pal, 'block')
    image(f'waypoint_top_{i}', WP_TOP, pal, 'block')
image('waypoint_stone', ['................', '....ssssssss....', '...slllllllls...', '...sldddddds....'[:13] + 's...', '...ssssssssss...'] +
      ['...s........s...'] * 2 + ['...ssssssssss...', '...slllsllls....'[:12] + 's...', '...sdddsddds....'[:12] + 's...', '...ssssssssss...',
       '...slllllllls...', '...sdddddddds...', '...ssssssssss...', '................', '................'],
      {'s': (62, 62, 72, 255), 'l': (122, 122, 136, 255), 'd': (96, 96, 108, 255)})
image('waypoint_gem', ['................'] * 5 + ['....cccccccc....', '....bbbbbbbb....'] + ['................'] * 9,
      {'c': (255, 255, 255, 255), 'b': (170, 170, 170, 255)})

# Boss seal: dark stone with a glowing sigil, dark when spent.
SEAL = [
    'ssssssssssssssss',
    'sddddddddddddds.'[:15] + 's',
    'sdllllllllllllds'[:16],
    'sdlddddrrdddddls'[:16],
    'sdlddrrppprddlds'[:16],
    'sdldrpddddprdlds',
    'sdldrpdrrdprdlds',
    'sdlrpddrprddplds'[:16],
    'sdlrpddprrddplds'[:16],
    'sdldrpdrrdprdlds',
    'sdldrpddddprdlds',
    'sdlddrrppprddlds'[:16],
    'sdlddddrrdddddls'[:16],
    'sdllllllllllllds'[:16],
    'sdddddddddddddds',
    'ssssssssssssssss']
image('boss_altar', SEAL, {'s': (20, 18, 28, 255), 'd': (44, 40, 56, 255), 'l': (66, 60, 82, 255), 'r': (200, 60, 60, 255), 'p': (255, 150, 90, 255)}, 'block')
image('boss_altar_spent', SEAL, {'s': (20, 18, 28, 255), 'd': (44, 40, 56, 255), 'l': (66, 60, 82, 255), 'r': (58, 52, 70, 255), 'p': (74, 68, 88, 255)}, 'block')

# Brewed potions: a bottle with a liquid layer tinted by what the potion does.
image('brew_bottle', [
    '................',
    '......kkkk......',
    '......kcck......',
    '......gkkg......',
    '.....g....g.....',
    '....g..ww..g....',
    '...g........g...',
    '..g..........g..',
    '..g...w......g..',
    '..g..........g..',
    '..g..........g..',
    '..g..........g..',
    '...g........g...',
    '....gggggggg....',
    '................',
    '................'], {'k': (120, 86, 52, 255), 'c': (160, 120, 78, 255), 'g': (190, 220, 235, 255), 'w': (255, 255, 255, 255)})
image('brew_liquid', [
    '................', '................', '................', '................', '................', '................',
    '....aaaaaaaa....',
    '...abbbbbbbba...',
    '...bbb.bbbbbb...',
    '...bbbbbbbbbb...',
    '...bbbbbbcbbb...',
    '...bbbbbbbbbb...',
    '....bbbbbbbb....',
    '................', '................', '................'], {'a': (255, 255, 255, 255), 'b': (205, 205, 205, 255), 'c': (255, 255, 255, 255)})

# ----- the Enchanted Forest: faewood and the gate
def tex(name, fn, folder='block'):
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), fn(x, y))
    path = os.path.join(ASSETS, 'textures', folder, name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
def noise(x, y, k=1):
    return ((x * 73856093) ^ (y * 19349663) ^ (k * 83492791)) % 97 / 96.0
def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)
tex('faewood_log', lambda x, y: mix((58, 44, 92), (92, 70, 140), noise(x // 2, y, 3)) if (x + noise(0, y, 5) * 2) % 5 > 1 else (40, 30, 66, 255))
tex('faewood_log_top', lambda x, y: (40, 30, 66, 255) if x in (0, 15) or y in (0, 15) else mix((150, 120, 200), (196, 170, 236), ((max(abs(x - 7.5), abs(y - 7.5)) * 1.3) % 3) / 3))
tex('faewood_leaves', lambda x, y: mix((150, 70, 190), (240, 150, 230), noise(x, y, 7)) if noise(x, y, 11) > 0.18 else (96, 44, 140, 255))
tex('faewood_planks', lambda x, y: (70, 52, 108, 255) if y % 4 == 3 or (x == (5 if (y // 4) % 2 else 11)) else mix((128, 100, 176), (160, 132, 208), noise(x, y // 4, 13)))
def shelf(x, y):
    if y in (0, 7, 8, 15) or x in (0, 15): return (70, 52, 108, 255)
    spine = (x - 1) // 2
    cols = [(180, 90, 220), (90, 200, 220), (230, 190, 90), (120, 220, 160), (220, 110, 150), (140, 130, 240), (240, 240, 250)]
    c = cols[(spine + (0 if y < 8 else 3)) % len(cols)]
    return (c[0] // 2, c[1] // 2, c[2] // 2, 255) if x % 2 == 0 else c + (255,)
tex('runic_bookshelf', shelf)
def gate_side(x, y):
    d = abs(x - 7.5) + abs(y - 7.5)
    if d < 3: return (236, 210, 255, 255)
    if d < 5.5: return mix((150, 90, 230), (90, 220, 200), noise(x, y, 17))
    return mix((66, 96, 70), (108, 140, 100), noise(x // 2, y // 2, 19)) if noise(x, y, 23) > 0.2 else (48, 70, 52, 255)
tex('forest_gate', gate_side)
def withered_side(x, y):
    r_, g_, b_, a_ = gate_side(x, y)
    grey = (r_ + g_ + b_) // 3
    if noise(x, y, 113) > 0.88: return (40, 36, 34, 255)            # cracks
    return (int(grey * 0.75 + 30), int(grey * 0.7 + 26), int(grey * 0.6 + 20), 255)
tex('withered_gate', withered_side)
tex('fae_dust', lambda x, y: ((250, 220, 255, 255) if noise(x, y, 29) > 0.6 else (190, 120, 240, 255)) if (abs(x - 7.5) + abs(y - 9) < 5.5 and noise(x, y, 31) > 0.25) else (0, 0, 0, 0), 'item')

# Familiar eggs: a shell tinted by the primary leaning and a band tinted by the secondary.
def egg_shell(x, y):
    cx, cy = 7.5, 8.5
    rx = 4.6 if y >= 8 else 4.6 - (8 - y) * 0.38
    if abs(y - cy) > 6.2 or abs(x - cx) > rx: return (0, 0, 0, 0)
    if 8 <= y <= 10 and not (y == 9 and x % 3 == 0): return (0, 0, 0, 0)
    v = 200 + int(55 * noise(x, y, 37)) - (40 if x > 9 else 0) + (20 if x < 6 and y < 7 else 0)
    v = max(120, min(255, v))
    return (v, v, v, 255)
def egg_trim(x, y):
    cx = 7.5
    rx = 4.6
    if abs(x - cx) > rx: return (0, 0, 0, 0)
    if 8 <= y <= 10 and not (y == 9 and x % 3 == 0):
        v = 255 if y == 9 else 215
        return (v, v, v, 255)
    if (x, y) in ((7, 4), (8, 5), (6, 12), (9, 12), (7, 13)): return (235, 235, 235, 255)
    return (0, 0, 0, 0)
tex('familiar_egg_shell', egg_shell, 'item')
tex('familiar_egg_trim', egg_trim, 'item')
def orb(x, y):
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    if d > 6.5: return (0, 0, 0, 0)
    if d > 5.3: return (120, 120, 120, 255)
    v = int(255 - d * 18 + 20 * noise(x, y, 41))
    v = max(140, min(255, v))
    return (v, v, v, 255)
def orb_eyes(x, y):
    return (30, 24, 44, 255) if (x in (5, 6, 9, 10) and y in (7, 8)) else ((255, 255, 255, 255) if (x in (5, 9) and y == 7) else (0, 0, 0, 0))
tex('familiar_orb', orb, 'item')
def focus(x, y):
    d = abs(x - 7.5) + abs(y - 7.5)
    if d > 7: return (0, 0, 0, 0)
    if d > 5.6: return (214, 170, 60, 255) if (x + y) % 2 == 0 else (160, 118, 34, 255)
    if d < 1.6: return (250, 240, 255, 255)
    return mix((92, 60, 190), (190, 130, 255), noise(x, y, 43) * 0.6 + (5.6 - d) / 9)
tex('arcane_focus', focus, 'item')
# The Wizard's Hat: a tall cone with a wide brim (grey, tinted by its dye) and a gold band and stars (untinted).
def hat_cone(x, y):
    if y == 12 and 0 <= x < 16: return (255, 255, 255, 255)                     # brim, top
    if y == 13 and 0 <= x < 16: return (176, 176, 176, 255)                     # brim, underside
    half = (y + 1) * 0.42
    tip_x = 9 - y * 0.25
    if y < 12 and abs(x - tip_x) <= half:
        return (255, 255, 255, 255) if x <= tip_x - half * 0.2 else (214, 214, 214, 255)   # lit side and shaded side
    return (0, 0, 0, 0)
def hat_band(x, y):
    if y == 12 and x in (0, 15): return (236, 196, 84, 255)
    if y == 10 and abs(x - 6.5) <= 4.6: return (176, 130, 44, 255)
    if y == 11 and abs(x - 6.5) <= 4.6: return (226, 214, 255, 255) if x == 6 else (236, 196, 84, 255)
    if (x, y) in ((8, 4), (6, 7)): return (226, 214, 255, 255)
    return (0, 0, 0, 0)
tex('wizard_hat', hat_cone, 'item')
# Robe and boots icons: a grey shape the dye tints, with a gold hem drawn over it.
ROBE = ['................', '...gg......gg...', '..gggg....gggg..', '.ggggggggggggggg', '.gg.gggggggg.gg.', '.gg.gggggggg.gg.', '.tt.gggggggg.tt.', '....gggggggg....',
        '....gggggggg....', '...gggggggggg...', '...gggggggggg...', '..gggggggggggg..', '..gggggggggggg..', '..tttttttttttt..', '................', '................']
BOOTS = ['................', '................', '................', '..tttt....tttt..', '..gggg....gggg..', '..gggg....gggg..', '..gggg....gggg..', '..gggg....gggg..',
         '..gggg....gggg..', '..gggg....ggggg.', '.ggggg...gggggg.', 'gggggg...gggggg.', 'gggggg...gggggg.', 'tttttt...tttttt.', '................', '................']
for name, rows in (('wizard_robe', ROBE),):
    image(name, rows, {'g': (215, 215, 215, 255)})
    image(name + '_trim', rows, {'t': (236, 196, 84, 255)})
# Worn: one armour sheet (64x32) for robe and boots. The body and arms are the robe; the lower legs are the boots.
GOLD, GOLD_DARK, LEATHER, LEATHER_DARK, INNER, SOLE = (236, 196, 84, 255), (170, 124, 40, 255), (112, 72, 40, 255), (78, 48, 26, 255), (34, 28, 48, 255), (44, 32, 24, 255)
def robe_worn(x, y, trim):
    body = 16 <= x < 40 and 16 <= y < 32
    arms = 40 <= x < 56 and 16 <= y < 32
    feet = x < 16 and 16 <= y < 32
    if not (body or arms or feet): return (0, 0, 0, 0)
    if feet and y < 20: return (0, 0, 0, 0)                                    # boots cover only the lower leg
    front = body and 20 <= x < 28 and y >= 20                                  # the body's front face
    if trim:
        if body and y >= 31: return GOLD if (x + y) % 3 else GOLD_DARK               # a gold hem
        if arms and y >= 31: return GOLD if (x + y) % 3 else GOLD_DARK               # cuffs
        if feet and y == 20: return GOLD if x % 2 else GOLD_DARK                     # boot tops
        return (0, 0, 0, 0)
    shade = 1.0 - 0.18 * ((y - 16) / 15.0)                                     # darker toward the hem
    if body and (x % 4 == 1): shade *= 0.82                                    # folds
    if arms and (x % 4 == 2): shade *= 0.86
    v = int((200 + 45 * noise(x, y, 91)) * shade)
    return (v, v, v, 255)
# The robe as worn: "Spells and whatnot" by Qsenja (MIT licence, see tools/third_party/spells_and_whatnot/LICENSE).
# Its greens become a grey that the dye colours (keeping light and dark), while the red gem, the belt and the
# fringe keep their own colours. Its sheet is laid out like a skin's outer layer; WizardRobeLayer's models use
# the ordinary layout, so the body, arm and leg pieces are moved into place.
import colorsys
ROBE_UNDYED = 0x9FC997
THIRD = os.path.join(os.path.dirname(__file__), 'third_party', 'spells_and_whatnot')
EQUIP = os.path.join(ASSETS, 'textures', 'entity', 'equipment')
ENT = os.path.join(ASSETS, 'textures', 'entity')
def is_cloth(r, g, b):
    h, sat, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return 0.30 <= h <= 0.6 and sat > 0.08
def split_robe(img):
    base = Image.new('RGBA', img.size)
    trim = Image.new('RGBA', img.size)
    vmax = max(colorsys.rgb_to_hsv(*(c / 255 for c in img.getpixel((x, y))[:3]))[2] for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] > 127)
    for yy in range(img.height):
        for xx in range(img.width):
            r, g, b, a = img.getpixel((xx, yy))
            if a < 128: continue
            if is_cloth(r, g, b):
                v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[2]
                grey = int(255 * (0.2 + 0.8 * v / vmax))
                base.putpixel((xx, yy), (grey, grey, grey, 255))
            else:
                trim.putpixel((xx, yy), (r, g, b, 255))
    return base, trim
src = Image.open(os.path.join(THIRD, 'robe_armor.png')).convert('RGBA')
for img, suffix in zip(split_robe(src), ('', '_trim')):
    upper = Image.new('RGBA', (64, 64))
    upper.paste(img.crop((16, 32, 40, 48)), (16, 16))   # body
    upper.paste(img.crop((40, 32, 56, 36)), (40, 16))   # the sleeve's top and cuff faces
    # Its sleeves stop above the elbow (7 rows); carry the cloth down to the wrist and keep the fringe at the cuff.
    sides = [img.crop((40, 36 + r, 56, 37 + r)) for r in range(7)]
    for out_row, src_row in enumerate([0, 1, 2, 1, 2, 1, 2, 1, 3, 4, 5, 6]):   # plain cloth rows repeated, the whole fringe at the cuff
        upper.paste(sides[src_row], (40, 20 + out_row))
    upper.save(os.path.join(ENT, 'wizard_robe_upper' + suffix + '.png'))
    lower = Image.new('RGBA', (64, 64))
    leg = img.crop((0, 32, 16, 46))                      # a 10-high leg piece with its fringe; the last rows are left to the boots
    lower.paste(leg, (0, 16))
    lower.save(os.path.join(ENT, 'wizard_robe_lower' + suffix + '.png'))
ICON = split_robe(Image.open(os.path.join(THIRD, 'robe_item.png')).convert('RGBA'))   # saved over the pixel-art icon below
# ----- The Wizard's Boots: the netherite boots, recoloured. The dark metal becomes a grey base the dye colours;
# the brightest edges turn gold and the lowest row becomes a dark sole.
import colorsys
def game_file(path):
    import zipfile as _z, glob as _g
    jar = sorted(_g.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/*/minecraft-merged.jar')))[-1]
    with _z.ZipFile(jar) as z:
        return z.read(path)
def recolour_boots(img, sole_row):
    base = Image.new('RGBA', img.size)
    trim = Image.new('RGBA', img.size)
    values = sorted(colorsys.rgb_to_hsv(*(c / 255 for c in img.getpixel((x, y))[:3]))[2] for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] > 127)
    bright, lo, hi = values[int(len(values) * 0.96)], values[0], values[-1]
    for y in range(img.height):
        for x in range(img.width):
            r_, g_, b_, a_ = img.getpixel((x, y))
            if a_ < 128: continue
            v = colorsys.rgb_to_hsv(r_ / 255, g_ / 255, b_ / 255)[2]
            if sole_row(x, y): trim.putpixel((x, y), (46, 34, 26, 255))
            elif v >= bright: trim.putpixel((x, y), (236, 196, 84, 255) if (x + y) % 3 else (176, 130, 44, 255))
            else:
                grey = int(255 * (0.5 + 0.5 * (v - lo) / max(0.01, hi - lo)))
                base.putpixel((x, y), (grey, grey, grey, 255))
    return base, trim
worn = Image.open(io.BytesIO(game_file('assets/minecraft/textures/entity/equipment/humanoid/netherite.png'))).convert('RGBA')
legs = Image.new('RGBA', worn.size)
legs.paste(worn.crop((0, 16, 16, 32)), (0, 16))   # boots draw only from the legs' part of the sheet
boots_base, boots_trim = recolour_boots(legs, lambda x, y: y == 31)
os.makedirs(os.path.join(EQUIP, 'humanoid'), exist_ok=True)
boots_base.save(os.path.join(EQUIP, 'humanoid', 'wizard_boots.png'))
boots_trim.save(os.path.join(EQUIP, 'humanoid', 'wizard_boots_trim.png'))
icon = Image.open(io.BytesIO(game_file('assets/minecraft/textures/item/netherite_boots.png'))).convert('RGBA')
lowest = max(y for y in range(16) for x in range(16) if icon.getpixel((x, y))[3] > 127)
BOOTS_ICON = recolour_boots(icon, lambda x, y: y == lowest)   # saved after the older pixel-art icons are written below
write(os.path.join(ASSETS, 'equipment', 'wizard_boots.json'), {'layers': {'humanoid': [
    {'texture': 'omnispell:wizard_boots', 'dyeable': {'color_when_undyed': 0x5E8F74 - 0x1000000}},
    {'texture': 'omnispell:wizard_boots_trim'}]}})
# The game's own armour drawing shows nothing for the robe: WizardRobeLayer draws it, with its own shape.
write(os.path.join(ASSETS, 'equipment', 'wizard_robe.json'), {'layers': {'humanoid': [{'texture': 'omnispell:wizard_hat_none'}]}})
tex('wizard_hat_band', hat_band, 'item')
# Worn: the 3D hat's skin (see WizardHatLayer): a grey base the dye tints, and a gold trim drawn over it.
def box_uv(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'sides': (u, v + d, 2 * (w + d), h)}
HAT_BOXES = {'brim': (0, 0, 14, 1, 14), 'cone': (0, 15, 9, 4, 9), 'mid': (0, 29, 7, 4, 7), 'upper': (28, 29, 5, 4, 5), 'top': (0, 41, 3, 4, 3), 'tip': (14, 41, 1, 3, 1)}
base = Image.new('RGBA', (64, 64))
trim = Image.new('RGBA', (64, 64))
GOLD_, GOLD_EDGE, LILAC = (236, 196, 84, 255), (176, 130, 44, 255), (226, 214, 255, 255)
for name, (u, v, w, h, d) in HAT_BOXES.items():
    for face, (fu, fv, fw, fh) in box_uv(u, v, w, h, d).items():
        for yy in range(fv, fv + fh):
            for xx in range(fu, fu + fw):
                col = xx - fu
                if face == 'top': g = 255
                elif face == 'bottom': g = 150
                else:
                    front = d <= col < d + w
                    back = col >= 2 * d + w
                    g = 255 if front and (col - d) in (1, 2) else 236 if front else 196 if back else 218   # flat shading, a highlight down the front
                base.putpixel((xx, yy), (g, g, g, 255))
                row = yy - fv
                if name == 'cone' and face == 'sides' and row == fh - 1: trim.putpixel((xx, yy), LILAC if col % 4 == 1 else GOLD_)
                elif name == 'cone' and face == 'sides' and row == fh - 2: trim.putpixel((xx, yy), GOLD_EDGE)
                elif name == 'brim' and face == 'sides': trim.putpixel((xx, yy), GOLD_)
                elif name == 'tip': trim.putpixel((xx, yy), GOLD_)
                elif name == 'upper' and face == 'sides' and row == 1 and col % 5 == 2: trim.putpixel((xx, yy), LILAC)
for img, name in ((base, 'wizard_hat'), (trim, 'wizard_hat_trim')):
    path = os.path.join(ASSETS, 'textures', 'entity', name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
tex('familiar_eyes', orb_eyes, 'item')

# Familiar skins: one 64x64 sheet per species. The regions must match FamiliarRender.layer().
def region(u, v, w, h, d):
    return (u, v, u + 2 * (w + d), v + d + h)
FAM_REGIONS = {'body': region(0, 0, 6, 5, 10), 'head': region(0, 16, 5, 5, 5), 'snout': region(20, 16, 3, 2, 3), 'beak': region(32, 16, 2, 2, 3),
               'ears': region(44, 16, 2, 2, 1), 'horns': region(52, 16, 1, 4, 1), 'antlers': region(56, 16, 2, 5, 1), 'crest': region(20, 22, 1, 3, 4),
               'legs': region(0, 27, 2, 7, 2), 'wings': region(0, 37, 9, 1, 7), 'tail': region(32, 0, 2, 2, 7), 'shell': region(0, 46, 8, 4, 11),
               'orb': region(40, 28, 6, 6, 6)}
FAM_SKINS = {
    'griffin': {'base': (201, 162, 92), 'wings': (240, 234, 214), 'beak': (232, 170, 44), 'head': (236, 228, 206), 'ears': (236, 228, 206), 'tail': (170, 130, 70)},
    'phoenix': {'base': (226, 84, 36), 'wings': (250, 168, 40), 'tail': (255, 208, 70), 'beak': (250, 222, 96), 'crest': (255, 222, 84), 'legs': (240, 190, 70)},
    'drake': {'base': (62, 130, 96), 'wings': (38, 88, 70), 'horns': (232, 230, 208), 'snout': (80, 150, 110), 'tail': (50, 110, 82)},
    'wisp': {'base': (150, 220, 255), 'orb': (170, 232, 255)},
    'stag': {'base': (150, 105, 70), 'antlers': (236, 226, 200), 'snout': (176, 132, 96), 'legs': (120, 84, 56), 'tail': (240, 236, 226)},
    'kodama': {'base': (236, 240, 230), 'legs': (214, 222, 208)},
    'direwolf': {'base': (96, 100, 112), 'ears': (64, 66, 76), 'snout': (140, 144, 154), 'tail': (120, 124, 136), 'legs': (84, 88, 100)},
    'tortoise': {'base': (120, 150, 90), 'shell': (92, 72, 50), 'legs': (104, 134, 78)},
}
for species, skin in FAM_SKINS.items():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for part, (x0, y0, x1, y1) in FAM_REGIONS.items():
        col = skin.get(part, skin['base'])
        for y in range(y0, min(64, y1)):
            for x in range(x0, min(64, x1)):
                n = noise(x, y, 47 + len(species))
                shade = 0.82 + 0.26 * n
                if part == 'shell' and (x % 4 == 0 or y % 4 == 0): shade *= 0.7
                if part == 'wings' and x % 3 == 0: shade *= 0.86
                if part == 'body' and species == 'direwolf' and y < 10: shade *= 0.85
                img.putpixel((x, y), tuple(max(0, min(255, int(c * shade))) for c in col) + (255,))
    # eyes on the front of the head (u 5..9, v 21..25) and of the wisp's orb (u 46..51, v 34..39)
    eye = (20, 16, 30, 255) if species != 'phoenix' else (255, 244, 180, 255)
    big = species == 'kodama'
    for ex in (5, 9):
        img.putpixel((ex, 22), eye)
        if big: img.putpixel((ex, 23), eye)
    if species == 'kodama': img.putpixel((7, 24), eye)
    for ex in (47, 50):
        img.putpixel((ex, 36), (255, 255, 255, 255))
        img.putpixel((ex, 37), (255, 255, 255, 255))
    path = os.path.join(ASSETS, 'textures', 'entity', 'familiar', species + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

# The conjured serpent: pale scales, so the spell's colour can tint them, with darker bands and a light belly.
img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
for y in range(32):
    for x in range(64):
        n = noise(x, y, 91)
        v = 205 + int(40 * n)
        if (x + y) % 4 == 0: v -= 28          # scale lines
        if y >= 12 and (x // 5) % 3 == 0: v -= 22  # bands down the body
        if x >= 40 and y < 6: v = 235           # horns and fin
        v = max(60, min(255, v))
        img.putpixel((x, y), (v, v, v, 255))
for (ex, ey) in ((7, 7), (10, 7)):
    img.putpixel((ex, ey), (250, 220, 60, 255))
    img.putpixel((ex, ey + 1), (30, 20, 10, 255))
path = os.path.join(ASSETS, 'textures', 'entity', 'conjured_serpent.png')
os.makedirs(os.path.dirname(path), exist_ok=True)
img.save(path)
lang_extra = {'entity.omnispell.conjured_serpent': 'Conjured Serpent'}

# Corruption: dark veins over a player's skin, in four spreading stages, and purple hearts.
import random as _random
VEIN_REGIONS = [(16, 20, 40, 32), (40, 20, 56, 32), (0, 20, 16, 32), (16, 52, 32, 64), (32, 52, 48, 64), (0, 8, 32, 16)]
_rng = _random.Random(1313)
veins = []
for n in range(70):
    x0, y0, x1, y1 = VEIN_REGIONS[n % len(VEIN_REGIONS)] if n >= 8 else VEIN_REGIONS[n % 5]
    x, y = _rng.randrange(x0, x1), _rng.randrange(y0, y1)
    path = [(x, y)]
    for _ in range(_rng.randint(5, 12)):
        dx, dy = _rng.choice([(0, 1), (0, 1), (1, 1), (-1, 1), (1, 0), (-1, 0), (0, -1)])
        x, y = max(x0, min(x1 - 1, x + dx)), max(y0, min(y1 - 1, y + dy))
        path.append((x, y))
    veins.append(path)
for stage, count in enumerate((8, 18, 32, 48), start=1):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    def overlay(x, y):
        # The same spot on the skin's outer layer (jacket, sleeves, trousers, hat), so the veins show over clothes too.
        if 16 <= y < 32 and x < 56: return (x, y + 16)
        if 48 <= y < 64 and 16 <= x < 32: return (x - 16, y)
        if 48 <= y < 64 and 32 <= x < 48: return (x + 16, y)
        if y < 16 and x < 32: return (x + 32, y)
        return None
    for path in veins[:count]:
        for i, (x, y) in enumerate(path):
            img.putpixel((x, y), (36, 6, 54, 255) if i % 3 else (150, 60, 214, 255))
            o = overlay(x, y)
            if o: img.putpixel(o, (36, 6, 54, 255) if i % 3 else (150, 60, 214, 255))
            # from the third stage the veins swell to two pixels wide
            if stage >= 4 and i % 3 == 0:
                nx = x + 1 if (x + 1) % 8 else x - 1
                if 0 <= nx < 64 and img.getpixel((nx, y))[3] == 0: img.putpixel((nx, y), (56, 12, 80, 255))
    p_ = os.path.join(ASSETS, 'textures', 'entity', f'corruption_veins_{stage}.png')
    os.makedirs(os.path.dirname(p_), exist_ok=True)
    img.save(p_)
# Elemental markings for conjured creatures: veins of the element over the creature's own hide.
# Every creature's skin is laid out differently, so each pattern is spread evenly over the whole
# sheet and wraps at the edges; whichever parts a model uses, it wears some of it.
def element_overlays():
    rng = _random.Random(2727)
    def put(img, x, y, c):
        img.putpixel((x % 64, y % 64), c)
    def line(img, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            put(img, round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c)
    def walk(x, y, steps, moves):
        path = [(x, y)]
        for _ in range(steps):
            dx, dy = rng.choice(moves)
            x, y = x + dx, y + dy
            path.append((x, y))
        return path
    def veins(img, count, steps, moves, dark, bright, wide=False):
        for _ in range(count):
            path = walk(rng.randrange(64), rng.randrange(64), rng.randint(*steps), moves)
            for i, (x, y) in enumerate(path):
                put(img, x, y, bright if i % 3 == 0 else dark)
                if wide and i % 2 == 0: put(img, x + 1, y, dark)
    made = {}
    def new():
        return Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    # fire: flames licking upward, deep orange at the root to pale yellow at the tip, and embers
    img = new()
    for _ in range(30):
        path = walk(rng.randrange(64), rng.randrange(64), rng.randint(4, 9), [(0, -1), (0, -1), (1, -1), (-1, -1)])
        for i, (x, y) in enumerate(path):
            t = i / max(1, len(path) - 1)
            put(img, x, y, mix((210, 64, 12), (255, 236, 130), t))
            if i < len(path) // 2: put(img, x + 1, y, (190, 50, 10, 255))
    for _ in range(36): put(img, rng.randrange(64), rng.randrange(64), (255, 200, 80, 255))
    made['fire'] = img
    # water: rippling bands with bright crests, and a few bubbles
    img = new()
    import math as _m
    for k in range(9):
        y0, phase = 3 + 7 * k + rng.randrange(3), rng.random() * 6.28
        for x in range(64):
            if (x + k * 5) % 13 >= 9: continue
            y = y0 + round(1.6 * _m.sin(x * 0.35 + phase))
            put(img, x, y, (190, 232, 255, 255) if x % 4 == 0 else (54, 136, 226, 255))
    for _ in range(26): put(img, rng.randrange(64), rng.randrange(64), (170, 222, 255, 255))
    made['water'] = img
    # ice: straight frost cracks that fork, and glints of white
    img = new()
    dirs = [(1, 0), (0, 1), (1, 1), (1, -1), (-1, 1), (-1, 0), (0, -1), (-1, -1)]
    for _ in range(36):
        x, y = rng.randrange(64), rng.randrange(64)
        dx, dy = rng.choice(dirs)
        n = rng.randint(3, 8)
        line(img, x, y, x + dx * n, y + dy * n, (128, 200, 240, 255))
        put(img, x, y, (214, 246, 255, 255))
        if rng.random() < 0.6:
            mx, my = x + dx * (n // 2), y + dy * (n // 2)
            ex, ey = rng.choice([d for d in dirs if d != (dx, dy) and d != (-dx, -dy)])
            line(img, mx, my, mx + ex * rng.randint(2, 4), my + ey * rng.randint(2, 4), (180, 230, 250, 255))
    for _ in range(22): put(img, rng.randrange(64), rng.randrange(64), (255, 255, 255, 255))
    made['ice'] = img
    # wind: curling gusts, bright at the head and fading along the tail
    img = new()
    for _ in range(28):
        cx, cy, r = rng.randrange(64), rng.randrange(64), rng.randint(3, 8)
        a0, sweep = rng.random() * 6.28, rng.uniform(1.6, 3.4)
        steps = int(sweep * r * 1.3) + 1
        for i in range(steps):
            a = a0 + sweep * i / steps
            put(img, round(cx + r * _m.cos(a)), round(cy + r * _m.sin(a)), (244, 250, 252, 255) if i > steps * 0.6 else (186, 212, 222, 255))
    made['wind'] = img
    # storm: forked lightning, a white-gold core with a violet glow beside it
    img = new()
    for _ in range(18):
        x, y = rng.randrange(64), rng.randrange(64)
        for step in range(rng.randint(5, 9)):
            nx, ny = x + rng.choice([-2, -1, 1, 2]) * (1 if step % 2 else -1), y + rng.randint(1, 2)
            line(img, x + 1, y, nx + 1, ny, (164, 140, 255, 255))
            line(img, x, y, nx, ny, (255, 250, 190, 255))
            if rng.random() < 0.2:
                line(img, nx, ny, nx + rng.choice([-3, 3]), ny + 2, (220, 210, 255, 255))
            x, y = nx, ny
    made['storm'] = img
    # stone: cracks in the hide, dark seams with lighter edges, and flecks of grit
    img = new()
    veins(img, 50, (4, 10), [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1)], (58, 48, 38, 255), (150, 128, 100, 255), wide=True)
    for _ in range(40): put(img, rng.randrange(64), rng.randrange(64), (160, 142, 112, 255))
    made['stone'] = img
    # nature: creeping vines with leaves along them, and the odd flower
    img = new()
    for _ in range(28):
        path = walk(rng.randrange(64), rng.randrange(64), rng.randint(6, 12), [(0, 1), (1, 1), (-1, 1), (1, 0), (-1, 0)])
        for i, (x, y) in enumerate(path):
            put(img, x, y, (44, 118, 40, 255))
            if i % 3 == 1:
                side = 1 if (i // 3) % 2 else -1
                put(img, x + side, y, (112, 202, 72, 255))
                put(img, x + side, y - 1, (90, 180, 60, 255))
    for _ in range(9): put(img, rng.randrange(64), rng.randrange(64), rng.choice([(242, 170, 222, 255), (250, 232, 120, 255)]))
    made['nature'] = img
    # light: little stars and glints of gold
    img = new()
    for _ in range(58):
        x, y = rng.randrange(64), rng.randrange(64)
        arm = 2 if rng.random() < 0.3 else 1
        put(img, x, y, (255, 255, 232, 255))
        for d in range(1, arm + 1):
            for dx, dy in ((d, 0), (-d, 0), (0, d), (0, -d)):
                put(img, x + dx, y + dy, (255, 226, 140, 255))
    made['light'] = img
    # shadow: wide smoky veins, nearly black, with a violet sheen
    img = new()
    veins(img, 36, (5, 11), [(1, 0), (-1, 0), (0, 1), (0, -1), (1, -1), (-1, -1)], (26, 14, 38, 255), (92, 52, 132, 255), wide=True)
    made['shadow'] = img
    # void: rifts of deep purple edged in magenta, and pale stars inside the dark
    img = new()
    veins(img, 28, (4, 8), [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)], (58, 8, 98, 255), (206, 86, 255, 255), wide=True)
    for _ in range(60): put(img, rng.randrange(64), rng.randrange(64), rng.choice([(244, 226, 255, 255), (255, 255, 255, 255), (186, 140, 255, 255)]))
    made['void'] = img
    # blood: dark red veins that pulse bright at the knots
    img = new()
    veins(img, 52, (5, 12), [(0, 1), (0, 1), (1, 1), (-1, 1), (1, 0), (-1, 0), (0, -1)], (98, 0, 12, 255), (196, 22, 36, 255))
    made['blood'] = img
    # arcane: runes scattered over it, violet with bright hearts
    img = new()
    runes = [['.#.', '###', '.#.'], ['#.#', '.#.', '#.#'], ['##.', '#.#', '.##'], ['#..', '###', '..#'], ['.#.', '#.#', '.#.'], ['###', '#..', '###'], ['#.#', '###', '#.#']]
    for _ in range(60):
        rune, x0, y0 = rng.choice(runes), rng.randrange(64), rng.randrange(64)
        for dy, row in enumerate(rune):
            for dx, ch in enumerate(row):
                if ch == '#': put(img, x0 + dx, y0 + dy, (246, 212, 255, 255) if (dx, dy) == (1, 1) else (196, 116, 255, 255))
    made['arcane'] = img
    for name, img in made.items():
        p_ = os.path.join(ASSETS, 'textures', 'entity', 'overlay', name + '.png')
        os.makedirs(os.path.dirname(p_), exist_ok=True)
        img.save(p_)
element_overlays()
HEART = ['.##...##.', '#hA#.#AA#', '#hAA#AAA#', '#AAAAAAs#', '.#AAAAs#.', '..#AAs#..', '...#s#...', '....#....', '.........']
def heart(name, half, blink):
    pal = {'#': (40, 8, 58, 255), 'A': (196, 120, 255, 255) if blink else (150, 60, 214, 255), 'h': (240, 214, 255, 255), 's': (98, 30, 140, 255)}
    img = Image.new('RGBA', (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(HEART):
        for x, ch in enumerate(row):
            if half and x > 4: continue
            if ch in pal: img.putpixel((x, y), pal[ch])
    p_ = os.path.join(ASSETS, 'textures', 'gui', 'sprites', 'hud', 'heart', name + '.png')
    os.makedirs(os.path.dirname(p_), exist_ok=True)
    img.save(p_)
heart('corrupted_full', False, False)
heart('corrupted_half', True, False)
heart('corrupted_full_blinking', False, True)
heart('corrupted_half_blinking', True, True)

# The Reliquary: a leather pouch tied shut with a totem's golden clasp, a purple sigil on its side.
REL = [
    '................',
    '......oooo......',
    '.....oyyyyo.....',
    '.....oygGyo.....',
    '......oyyo......',
    '.....bbbbbb.....',
    '....bLLLLLLb....',
    '...bLLLLLLLLb...',
    '..bLLLLLLLLLLb..',
    '..bLLLLppLLLLb..',
    '..bLLLpLLpLLLb..',
    '..bLLLLppLLLLb..',
    '..bLLLLLLLLLLb..',
    '...bLLLLLLLLb...',
    '....bbbbbbbb....',
    '................',
]
assert all(len(r) == 16 for r in REL)
image('reliquary', REL, {'o': (92, 64, 20, 255), 'y': (240, 200, 70, 255), 'g': (60, 200, 110, 255), 'G': (40, 150, 80, 255),
                         'b': (70, 42, 22, 255), 'L': (150, 98, 52, 255), 'p': (170, 80, 230, 255)})

# ---------------------------------------------------------------- models
def flat_item(name, layers, parent='minecraft:item/generated'):
    write(os.path.join(ASSETS, 'models', 'item', name + '.json'),
          {'parent': parent, 'textures': {f'layer{i}': f'omnispell:item/{t}' for i, t in enumerate(layers)}})

flat_item('wand', ['wand_wood', 'wand_core'], 'minecraft:item/handheld')
for n in ('grimoire', 'word_scroll', 'arcane_potion', 'reliquary'):
    flat_item(n, [n])
    write(os.path.join(ASSETS, 'items', n + '.json'), {'model': {'type': 'minecraft:model', 'model': f'omnispell:item/{n}'}})
write(os.path.join(ASSETS, 'items', 'wand.json'), {'model': {
    'type': 'minecraft:model', 'model': 'omnispell:item/wand',
    'tints': [{'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0xB8945F},
              {'type': 'minecraft:custom_model_data', 'index': 1, 'default': 0x565656}]}})
write(os.path.join(ASSETS, 'models', 'block', 'arcane_well.json'), {
    'parent': 'minecraft:block/cube_bottom_top',
    'textures': {'top': 'omnispell:block/arcane_well_top', 'bottom': 'omnispell:block/arcane_well_bottom',
                 'side': 'omnispell:block/arcane_well_side'}})
# Hardened air: wind magic's conjured blocks. It looks like faint white glass.
write(os.path.join(ASSETS, 'models', 'block', 'hardened_air.json'), {
    'parent': 'minecraft:block/cube_all', 'textures': {'all': 'minecraft:block/white_stained_glass'}})
write(os.path.join(ASSETS, 'blockstates', 'hardened_air.json'), {'variants': {'': {'model': 'omnispell:block/hardened_air'}}})
write(os.path.join(ASSETS, 'blockstates', 'arcane_well.json'), {'variants': {'': {'model': 'omnispell:block/arcane_well'}}})
write(os.path.join(ASSETS, 'items', 'arcane_well.json'), {'model': {'type': 'minecraft:model', 'model': 'omnispell:block/arcane_well'}})

variants = {}
for i in range(20):
    write(os.path.join(ASSETS, 'models', 'block', f'waypoint_{i}.json'), {
        'parent': 'minecraft:block/cube_bottom_top',
        'textures': {'top': f'omnispell:block/waypoint_top_{i}', 'bottom': 'omnispell:block/arcane_well_bottom',
                     'side': f'omnispell:block/waypoint_side_{i}'}})
    variants[f'color={i}'] = {'model': f'omnispell:block/waypoint_{i}'}
write(os.path.join(ASSETS, 'blockstates', 'waypoint.json'), {'variants': variants})
flat_item('waypoint', ['waypoint_stone', 'waypoint_gem'])
write(os.path.join(ASSETS, 'items', 'waypoint.json'), {'model': {
    'type': 'minecraft:model', 'model': 'omnispell:item/waypoint',
    'tints': [{'type': 'minecraft:constant', 'value': -1},
              {'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0xB0B0B8}]}})

altar_variants = {}
for spent in ('false', 'true'):
    model = 'boss_altar_spent' if spent == 'true' else 'boss_altar'
    write(os.path.join(ASSETS, 'models', 'block', model + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'omnispell:block/{model}'}})
    altar_variants[f'spent={spent}'] = {'model': f'omnispell:block/{model}'}
write(os.path.join(ASSETS, 'blockstates', 'boss_altar.json'), {'variants': altar_variants})
write(os.path.join(ASSETS, 'items', 'boss_altar.json'), {'model': {'type': 'minecraft:model', 'model': 'omnispell:block/boss_altar'}})

flat_item('familiar_egg', ['familiar_egg_shell', 'familiar_egg_trim'])
write(os.path.join(ASSETS, 'items', 'familiar_egg.json'), {'model': {
    'type': 'minecraft:model', 'model': 'omnispell:item/familiar_egg',
    'tints': [{'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0xECE6DA},
              {'type': 'minecraft:custom_model_data', 'index': 1, 'default': 0x9A948A}]}})
flat_item('familiar', ['familiar_orb', 'familiar_eyes'])
write(os.path.join(ASSETS, 'items', 'familiar.json'), {'model': {
    'type': 'minecraft:model', 'model': 'omnispell:item/familiar',
    'tints': [{'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0xC9A0FF},
              {'type': 'minecraft:constant', 'value': -1}]}})
flat_item('arcane_focus', ['arcane_focus'])
flat_item('wizard_hat', ['wizard_hat', 'wizard_hat_band'])
ICON[0].save(os.path.join(ASSETS, 'textures', 'item', 'wizard_robe.png'))
ICON[1].save(os.path.join(ASSETS, 'textures', 'item', 'wizard_robe_trim.png'))
BOOTS_ICON[0].save(os.path.join(ASSETS, 'textures', 'item', 'wizard_boots.png'))
BOOTS_ICON[1].save(os.path.join(ASSETS, 'textures', 'item', 'wizard_boots_trim.png'))
for piece, undyed in (('wizard_robe', ROBE_UNDYED), ('wizard_boots', 0x5E8F74)):
    flat_item(piece, [piece, piece + '_trim'])
    write(os.path.join(ASSETS, 'items', piece + '.json'), {'model': {
        'type': 'minecraft:model', 'model': f'omnispell:item/{piece}',
        'tints': [{'type': 'minecraft:dye', 'default': undyed - 0x1000000}, {'type': 'minecraft:constant', 'value': -1}]}})
write(os.path.join(ASSETS, 'items', 'wizard_hat.json'), {'model': {
    'type': 'minecraft:model', 'model': 'omnispell:item/wizard_hat',
    'tints': [{'type': 'minecraft:dye', 'default': 0x5E8F74 - 0x1000000}, {'type': 'minecraft:constant', 'value': -1}]}})
write(os.path.join(ASSETS, 'items', 'arcane_focus.json'), {'model': {'type': 'minecraft:model', 'model': 'omnispell:item/arcane_focus'}})
flat_item('brew', ['brew_bottle', 'brew_liquid'])
write(os.path.join(ASSETS, 'items', 'brew.json'), {'model': {
    'type': 'minecraft:model', 'model': 'omnispell:item/brew',
    'tints': [{'type': 'minecraft:constant', 'value': -1},
              {'type': 'minecraft:custom_model_data', 'index': 0, 'default': 0xB06FF2}]}})
# The alchemy cauldron reuses the vanilla cauldron shapes, with a different liquid for each base.
CAULDRON_TEX = {'bottom': 'minecraft:block/cauldron_bottom', 'inside': 'minecraft:block/cauldron_inner',
                'particle': 'minecraft:block/cauldron_side', 'side': 'minecraft:block/cauldron_side', 'top': 'minecraft:block/cauldron_top'}
CONTENT = {1: 'minecraft:block/light_blue_stained_glass', 2: 'minecraft:block/honey_block_top', 3: 'minecraft:block/white_concrete_powder',
           4: 'minecraft:block/lava_still', 5: 'minecraft:block/purple_concrete_powder', 6: 'minecraft:block/powder_snow',
           7: 'minecraft:block/black_concrete_powder', 8: 'minecraft:block/lime_stained_glass', 9: 'minecraft:block/brown_concrete_powder'}
cauldron_variants = {'contents=0': {'model': 'minecraft:block/cauldron'}}
for n, tex in CONTENT.items():
    write(os.path.join(ASSETS, 'models', 'block', f'alchemy_cauldron_{n}.json'),
          {'parent': 'minecraft:block/template_cauldron_full', 'textures': dict(CAULDRON_TEX, content=tex)})
    cauldron_variants[f'contents={n}'] = {'model': f'omnispell:block/alchemy_cauldron_{n}'}
write(os.path.join(ASSETS, 'blockstates', 'alchemy_cauldron.json'), {'variants': cauldron_variants})
write(os.path.join(ASSETS, 'items', 'alchemy_cauldron.json'), {'model': {'type': 'minecraft:model', 'model': 'omnispell:block/alchemy_cauldron_5'}})

for name in ('faewood_leaves', 'faewood_planks', 'forest_gate', 'withered_gate'):
    write(os.path.join(ASSETS, 'models', 'block', name + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'omnispell:block/{name}'}})
    write(os.path.join(ASSETS, 'blockstates', name + '.json'), {'multipart': [{'apply': {'model': f'omnispell:block/{name}'}}]})
    write(os.path.join(ASSETS, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': f'omnispell:block/{name}'}})
write(os.path.join(ASSETS, 'models', 'block', 'faewood_log.json'), {'parent': 'minecraft:block/cube_column',
      'textures': {'end': 'omnispell:block/faewood_log_top', 'side': 'omnispell:block/faewood_log'}})
write(os.path.join(ASSETS, 'models', 'block', 'faewood_log_horizontal.json'), {'parent': 'minecraft:block/cube_column_horizontal',
      'textures': {'end': 'omnispell:block/faewood_log_top', 'side': 'omnispell:block/faewood_log'}})
write(os.path.join(ASSETS, 'blockstates', 'faewood_log.json'), {'variants': {
    'axis=y': {'model': 'omnispell:block/faewood_log'},
    'axis=z': {'model': 'omnispell:block/faewood_log_horizontal', 'x': 90},
    'axis=x': {'model': 'omnispell:block/faewood_log_horizontal', 'x': 90, 'y': 90}}})
write(os.path.join(ASSETS, 'items', 'faewood_log.json'), {'model': {'type': 'minecraft:model', 'model': 'omnispell:block/faewood_log'}})
write(os.path.join(ASSETS, 'models', 'block', 'runic_bookshelf.json'), {'parent': 'minecraft:block/cube_column',
      'textures': {'end': 'omnispell:block/faewood_planks', 'side': 'omnispell:block/runic_bookshelf'}})
write(os.path.join(ASSETS, 'blockstates', 'runic_bookshelf.json'), {'variants': {'': {'model': 'omnispell:block/runic_bookshelf'}}})
write(os.path.join(ASSETS, 'items', 'runic_bookshelf.json'), {'model': {'type': 'minecraft:model', 'model': 'omnispell:block/runic_bookshelf'}})
flat_item('fae_dust', ['fae_dust'])
write(os.path.join(ASSETS, 'items', 'fae_dust.json'), {'model': {'type': 'minecraft:model', 'model': 'omnispell:item/fae_dust'}})

# ---------------------------------------------------------------- the Enchanted Forest realm
# Biomes and trees are built from the game's own definitions (read from the game jar), then changed.
import zipfile, glob
GAME = sorted(glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/*/minecraft-merged.jar')))[-1]
def vanilla(path):
    with zipfile.ZipFile(GAME) as z:
        return json.loads(z.read('data/minecraft/' + path))
WG = os.path.join(DATA, 'worldgen')

tree = vanilla('worldgen/feature/cherry.json')
def swap_blocks(node):
    if isinstance(node, dict):
        if node.get('id') == 'minecraft:cherry_log':
            return dict(node, id='omnispell:faewood_log')
        if node.get('id') == 'minecraft:cherry_leaves':
            return {'id': 'omnispell:faewood_leaves'}
        return {k: swap_blocks(v) for k, v in node.items()}
    if isinstance(node, list):
        return [swap_blocks(v) for v in node]
    return node
tree = swap_blocks(tree)
tree['decorators'] = []
write(os.path.join(WG, 'feature', 'faewood.json'), tree)
def placed_trees(name, count):
    placed = vanilla('worldgen/placed_feature/trees_cherry.json')
    placed['feature'] = 'omnispell:faewood'
    for step in placed['placement']:
        if step['type'] == 'minecraft:count':
            step['count'] = count
    write(os.path.join(WG, 'placed_feature', name + '.json'), placed)
placed_trees('trees_faewood', 7)
placed_trees('trees_faewood_sparse', 2)

MONSTERS = [('minecraft:witch', 1, 40), ('minecraft:cave_spider', 3, 60), ('minecraft:enderman', 2, 40), ('minecraft:bogged', 3, 70),
            ('minecraft:stray', 3, 50), ('minecraft:spider', 3, 60), ('minecraft:skeleton', 3, 50), ('minecraft:creeper', 2, 40),
            ('minecraft:phantom', 1, 15), ('minecraft:vindicator', 1, 15)]
CREATURES = [('minecraft:fox', 2, 8), ('minecraft:rabbit', 3, 8), ('minecraft:wolf', 3, 5), ('minecraft:frog', 3, 6), ('minecraft:parrot', 2, 6)]
def forest_biome(name, source, swaps, grass, foliage, water, sky, fog, add=()):
    b = vanilla(f'worldgen/biome/{source}.json')
    veg = [swaps.get(f, f) for f in b['features'][9]] + list(add)
    b['features'][9] = [f for f in veg if f]
    b['effects'] = {'grass_color': grass, 'foliage_color': foliage, 'water_color': water}
    attrs = {k: v for k, v in b['attributes'].items() if k.startswith('minecraft:audio/')}
    attrs['minecraft:visual/sky_color'] = sky
    attrs['minecraft:visual/fog_color'] = fog
    attrs['minecraft:gameplay/natural_mob_spawns'] = {'modifier': 'overlay', 'argument': {'spawn_costs': {}, 'spawns_by_category': {
        'ambient': [{'type': 'minecraft:bat', 'count': 8, 'weight': 10}],
        'creature': [{'type': t, 'count': c, 'weight': w} for t, c, w in CREATURES],
        'monster': [{'type': t, 'count': c, 'weight': w} for t, c, w in MONSTERS]}}}
    b['attributes'] = attrs
    write(os.path.join(WG, 'biome', name + '.json'), b)
forest_biome('faewood_grove', 'cherry_grove', {'minecraft:trees_cherry': 'omnispell:trees_faewood'},
             '#5fd6a8', '#c79bff', '#8f6cff', '#5b3fb0', '#b9a0ff')
forest_biome('gloamwood', 'dark_forest', {}, '#2f7f6a', '#3f6f8f', '#3a4fd0', '#241a52', '#5a4a9a', add=['omnispell:trees_faewood_sparse'])
# The game needs every biome of a realm to list shared plants in the same order, so the borrowed
# lists are left as they are and faewood is only ever added at the very end.
forest_biome('glowcap_glade', 'mushroom_fields', {}, '#7fd0c0', '#9fe0d0', '#50e0d0', '#3a5fb0', '#9ad0e8',
             add=['omnispell:trees_faewood_sparse'])
forest_biome('palewood', 'pale_garden', {}, '#a8b8c8', '#c8c0e0', '#8a8fd0', '#4a4a78', '#a0a0c8', add=['omnispell:trees_faewood_sparse'])
forest_biome('ancient_spires', 'old_growth_spruce_taiga', {}, '#4fb090', '#6f90d0', '#6a6cff', '#3a2f80', '#8f86d8', add=['omnispell:trees_faewood_sparse'])

dim_type = vanilla('dimension_type/overworld.json')
dim_type['ambient_light'] = 0.08
dim_type['attributes']['minecraft:visual/sky_color'] = '#4a33a0'
dim_type['attributes']['minecraft:visual/fog_color'] = '#a590f0'
dim_type['attributes']['minecraft:visual/cloud_color'] = '#ccd8b0ff'
dim_type['attributes']['minecraft:visual/ambient_light_color'] = '#1a1030'
write(os.path.join(DATA, 'dimension_type', 'enchanted_forest.json'), dim_type)
def point(biome, temperature, humidity):
    return {'biome': f'omnispell:{biome}', 'parameters': {'temperature': temperature, 'humidity': humidity, 'continentalness': [-1.0, 1.0],
            'erosion': [-1.0, 1.0], 'weirdness': [-1.0, 1.0], 'depth': 0.0, 'offset': 0.0}}
write(os.path.join(DATA, 'dimension', 'enchanted_forest.json'), {
    'type': 'omnispell:enchanted_forest',
    'generator': {'type': 'minecraft:noise', 'settings': 'minecraft:overworld', 'biome_source': {'type': 'minecraft:multi_noise', 'biomes': [
        point('faewood_grove', [-0.2, 0.4], [-0.3, 0.4]),
        point('gloamwood', [-0.2, 0.4], [0.4, 1.0]),
        point('glowcap_glade', [0.4, 1.0], [-0.2, 1.0]),
        point('palewood', [-1.0, -0.2], [-1.0, 0.2]),
        point('ancient_spires', [-1.0, -0.2], [0.2, 1.0]),
        point('faewood_grove', [0.4, 1.0], [-1.0, -0.2]),
        point('gloamwood', [-0.2, 0.4], [-1.0, -0.3])]}}})
FOREST_BIOMES = ['omnispell:faewood_grove', 'omnispell:gloamwood', 'omnispell:glowcap_glade', 'omnispell:palewood', 'omnispell:ancient_spires']

# ---------------------------------------------------------------- worldgen
# Biomes where surface structures may stand: land biomes, vanilla and (optionally) other mods' by convention tags.
LAND = ['#minecraft:is_forest', '#minecraft:is_taiga', '#minecraft:is_savanna', '#minecraft:is_hill', '#minecraft:is_mountain',
        '#minecraft:is_badlands', '#minecraft:is_jungle', 'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:snowy_plains',
        'minecraft:desert', 'minecraft:meadow', 'minecraft:cherry_grove', 'minecraft:ice_spikes']
# Other mods file their biomes under these shared tags. All of them are land; none includes oceans, rivers or beaches.
SHARED = ['is_plains', 'is_forest', 'is_desert', 'is_snowy_plains', 'is_magical', 'is_birch_forest', 'is_dark_forest', 'is_flower_forest',
          'is_floral', 'is_old_growth', 'is_savanna', 'is_jungle', 'is_taiga', 'is_hill', 'is_badlands', 'is_plateau', 'is_windswept',
          'is_wasteland', 'is_dead', 'is_dense_vegetation/overworld', 'is_sparse_vegetation/overworld',
          'is_tree/coniferous', 'is_tree/deciduous', 'is_tree/jungle', 'is_tree/savanna',
          'primary_wood_type/oak', 'primary_wood_type/birch', 'primary_wood_type/spruce', 'primary_wood_type/acacia',
          'primary_wood_type/cherry', 'primary_wood_type/dark_oak', 'primary_wood_type/jungle', 'primary_wood_type/pale_oak',
          'primary_wood_type/poplar', 'primary_wood_type/bamboo']
LAND += [{'id': '#c:' + t, 'required': False} for t in SHARED]
LAND += FOREST_BIOMES
write(os.path.join(DATA, 'tags', 'worldgen', 'biome', 'has_structure', 'surface.json'), {'replace': False, 'values': LAND})

def structure(name, weight_unused=None):
    write(os.path.join(DATA, 'worldgen', 'template_pool', name + '.json'), {
        'elements': [{'weight': 1, 'element': {'element_type': 'minecraft:legacy_single_pool_element', 'location': f'omnispell:{name}',
                                               'processors': {'processors': []}, 'projection': 'rigid'}}],
        'fallback': 'minecraft:empty'})
    write(os.path.join(DATA, 'worldgen', 'structure', name + '.json'), {
        'type': 'minecraft:jigsaw', 'biomes': '#omnispell:has_structure/surface',
        'max_distance_from_center': 80, 'project_start_to_heightmap': 'WORLD_SURFACE_WG', 'size': 1,
        'spawn_overrides': {}, 'start_height': {'absolute': -1}, 'start_pool': f'omnispell:{name}',
        'step': 'surface_structures', 'terrain_adaptation': 'beard_thin', 'use_expansion_hack': False})

structure('arcane_shrine')
write(os.path.join(DATA, 'worldgen', 'structure_set', 'arcane_shrines.json'), {
    'placement': {'type': 'minecraft:random_spread', 'salt': 734102911, 'spacing': 22, 'separation': 9},
    'structures': [{'structure': 'omnispell:arcane_shrine', 'weight': 1}]})
LAIR_WEIGHTS = [30, 24, 18, 12, 8, 5, 3]   # hay is common, netherite is a rare find
for tier in range(7):
    structure(f'boss_lair_{tier}')
write(os.path.join(DATA, 'worldgen', 'structure_set', 'boss_lairs.json'), {
    'placement': {'type': 'minecraft:random_spread', 'salt': 1190445571, 'spacing': 30, 'separation': 12},
    'structures': [{'structure': f'omnispell:boss_lair_{t}', 'weight': LAIR_WEIGHTS[t]} for t in range(7)]})

def chest_table(name, items):
    write(os.path.join(DATA, 'loot_table', 'chests', name + '.json'), {
        'type': 'minecraft:chest',
        'pools': [{'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 6},
                   'entries': [{'type': 'minecraft:item', 'name': i, 'weight': w} for i, w in items]}]})
chest_table('arcane_shrine', [('minecraft:amethyst_shard', 10), ('minecraft:glass_bottle', 10), ('minecraft:lapis_lazuli', 8),
                              ('minecraft:book', 6), ('minecraft:glowstone_dust', 5), ('minecraft:ender_pearl', 2), ('omnispell:arcane_potion', 3)])
LAIR_LOOT = [
    [('minecraft:wheat', 10), ('minecraft:bone', 8), ('minecraft:string', 8), ('minecraft:amethyst_shard', 5), ('omnispell:arcane_potion', 3)],
    [('minecraft:bone', 8), ('minecraft:amethyst_shard', 8), ('minecraft:copper_ingot', 8), ('minecraft:iron_ingot', 4), ('omnispell:arcane_potion', 4)],
    [('minecraft:iron_ingot', 8), ('minecraft:amethyst_shard', 8), ('minecraft:lapis_lazuli', 8), ('minecraft:gold_ingot', 4), ('omnispell:arcane_potion', 4)],
    [('minecraft:iron_ingot', 10), ('minecraft:gold_ingot', 8), ('minecraft:ender_pearl', 4), ('minecraft:emerald', 5), ('omnispell:arcane_potion', 5)],
    [('minecraft:gold_ingot', 10), ('minecraft:emerald', 8), ('minecraft:ender_pearl', 6), ('minecraft:diamond', 3), ('omnispell:arcane_potion', 5)],
    [('minecraft:diamond', 6), ('minecraft:emerald', 8), ('minecraft:ender_pearl', 6), ('minecraft:echo_shard', 3), ('omnispell:arcane_potion', 6)],
    [('minecraft:diamond', 8), ('minecraft:echo_shard', 5), ('minecraft:netherite_scrap', 3), ('minecraft:ender_pearl', 6), ('omnispell:arcane_potion', 6)],
]
for tier, items in enumerate(LAIR_LOOT):
    chest_table(f'boss_lair_{tier}', items)

# ---------------------------------------------------------------- names
lang = {
    'item.omnispell.wand': 'Wand',
    'item.omnispell.grimoire': 'Book of Words',
    'item.omnispell.word_scroll': 'Word Scroll',
    'item.omnispell.arcane_potion': 'Arcane Potion',
    'block.omnispell.arcane_well': 'Arcane Well',
    'block.omnispell.hardened_air': 'Hardened Air',
    'item.omnispell.reliquary': 'Reliquary',
    'entity.omnispell.conjured_serpent': 'Conjured Serpent',
    'block.omnispell.waypoint': 'Waypoint',
    'block.omnispell.boss_altar': 'Boss Seal',
    'block.omnispell.alchemy_cauldron': 'Alchemy Cauldron',
    'block.omnispell.faewood_log': 'Faewood Log',
    'block.omnispell.faewood_leaves': 'Faewood Blossom',
    'block.omnispell.faewood_planks': 'Faewood Planks',
    'block.omnispell.runic_bookshelf': 'Runic Bookshelf',
    'block.omnispell.forest_gate': 'Forest Gate',
    'block.omnispell.withered_gate': 'Withered Forest Gate',
    'item.omnispell.fae_dust': 'Fae Dust',
    'biome.omnispell.faewood_grove': 'Faewood Grove',
    'biome.omnispell.gloamwood': 'Gloamwood',
    'biome.omnispell.glowcap_glade': 'Glowcap Glade',
    'biome.omnispell.palewood': 'Palewood',
    'biome.omnispell.ancient_spires': 'Ancient Spires',
    'item.omnispell.brew': 'Potion',
    'item.omnispell.familiar_egg': 'Familiar Egg',
    'entity.omnispell.familiar': 'Familiar',
    'item.omnispell.arcane_focus': 'Arcane Focus',
    'item.omnispell.wizard_hat': "Wizard's Hat",
    'item.omnispell.wizard_robe': "Wizard's Robe",
    'item.omnispell.wizard_boots': "Wizard's Boots",
    'item.omnispell.familiar': 'Familiar',
    'enchantment.omnispell.attunement': 'Attunement',
    'enchantment.omnispell.reservoir': 'Reservoir',
    'enchantment.omnispell.wellspring': 'Wellspring',
    'enchantment.omnispell.alacrity': 'Alacrity',
    'key.category.omnispell.omnispell': 'Omnispell',
    'key.omnispell.cast_1': 'Cast Spell 1',
    'key.omnispell.cast_2': 'Cast Spell 2',
    'key.omnispell.cast_3': 'Cast Spell 3',
    'key.omnispell.cast_4': 'Cast Spell 4',
    'key.omnispell.cast_5': 'Cast Spell 5',
    'key.omnispell.hud': 'Show / Hide Mana',
    'key.omnispell.book': 'Open Book of Words',
}
charm_java = open(os.path.join(ROOT, '..', 'java', 'com', 'omnispell', 'charm', 'Charms.java')).read()
CHARMS = re.findall(r'charm\("(\w+)", "([^"]+)", "([^"]+)"\)', charm_java)
assert len(CHARMS) >= 10, CHARMS
for cid, cname, _ in CHARMS:
    lang[f'enchantment.omnispell.{cid}'] = cname
write(os.path.join(ASSETS, 'lang', 'en_us.json'), lang)

# ---------------------------------------------------------------- recipes
java = open(os.path.join(ROOT, '..', 'java', 'com', 'omnispell', 'item', 'WandParts.java')).read()
woods = re.findall(r'new Wood\("(\w+)", "([^"]+)", 0x([0-9A-Fa-f]+)', java)
cores = re.findall(r'core\("(\w+)", "([^"]+)", 0x([0-9A-Fa-f]+), (\d)', java)
assert len(woods) >= 10 and len(cores) >= 10, (len(woods), len(cores))
recipe_dir = os.path.join(DATA, 'recipe')
for wood, wood_label, wood_color in woods:
    for core, core_label, core_color, _ in cores:
        write(os.path.join(recipe_dir, f'wand_{wood}_{core}.json'), {
            'type': 'minecraft:crafting_shaped', 'category': 'equipment',
            'key': {'W': f'minecraft:{wood}_planks', 'C': f'minecraft:{core}'},
            'pattern': ['W', 'C', 'W'],
            'result': {'id': 'omnispell:wand', 'components': {
                'minecraft:custom_data': {'omni_wood': wood, 'omni_core': core},
                'minecraft:item_name': f'{wood_label} Wand',
                'minecraft:custom_model_data': {'colors': [int(wood_color, 16), int(core_color, 16)]}}}})
write(os.path.join(recipe_dir, 'grimoire.json'), {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': ['minecraft:book', 'minecraft:amethyst_shard', 'minecraft:lapis_lazuli'],
    'result': {'id': 'omnispell:grimoire'}})
# Waypoints are made two at a time.
write(os.path.join(recipe_dir, 'waypoint.json'), {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'key': {'E': 'minecraft:ender_pearl', 'A': 'minecraft:amethyst_shard', 'S': 'minecraft:stone_bricks'},
    'pattern': [' E ', 'ASA', 'SSS'],
    'result': {'id': 'omnispell:waypoint', 'count': 2}})
# The Reliquary: a bundle bound to a totem.
write(os.path.join(recipe_dir, 'reliquary.json'), {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': ['minecraft:bundle', 'minecraft:totem_of_undying'],
    'result': {'id': 'omnispell:reliquary'}})
# The alchemy cauldron needs a blaze rod, so brewing waits for the Nether, as in vanilla.
write(os.path.join(recipe_dir, 'alchemy_cauldron.json'), {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': ['minecraft:cauldron', 'minecraft:blaze_rod', 'minecraft:amethyst_shard'],
    'result': {'id': 'omnispell:alchemy_cauldron'}})
write(os.path.join(DATA, 'loot_table', 'blocks', 'alchemy_cauldron.json'), {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
               'entries': [{'type': 'minecraft:item', 'name': 'omnispell:alchemy_cauldron'}]}]})
# The Enchanted Forest: the gate, and what its wood makes.
write(os.path.join(recipe_dir, 'forest_gate.json'), {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'key': {'M': 'minecraft:moss_block', 'E': 'minecraft:emerald', 'A': 'minecraft:amethyst_shard', 'P': 'minecraft:ender_pearl', 'G': 'minecraft:glow_berries'},
    'pattern': ['MEM', 'APA', 'MGM'],
    'result': {'id': 'omnispell:forest_gate'}})
write(os.path.join(recipe_dir, 'faewood_planks.json'), {
    'type': 'minecraft:crafting_shapeless', 'category': 'building',
    'ingredients': ['omnispell:faewood_log'], 'result': {'id': 'omnispell:faewood_planks', 'count': 4}})
write(os.path.join(recipe_dir, 'runic_bookshelf.json'), {
    'type': 'minecraft:crafting_shaped', 'category': 'building',
    'key': {'W': 'omnispell:faewood_planks', 'B': 'minecraft:book'},
    'pattern': ['WWW', 'BBB', 'WWW'], 'result': {'id': 'omnispell:runic_bookshelf'}})
# ----- Ruins of the other gates (see PortalRuins): rarer than the Nether's ruined portals; the forest gate rarest.
for name, chance in (('ruined_sky_portal', 1500), ('ruined_forest_gate', 900)):
    write(os.path.join(DATA, 'worldgen', 'feature', name + '.json'), {'type': f'omnispell:{name}'})
    write(os.path.join(DATA, 'worldgen', 'placed_feature', name + '.json'), {
        'feature': f'omnispell:{name}',
        'placement': [{'type': 'minecraft:rarity_filter', 'chance': chance}, {'type': 'minecraft:in_square'},
                      {'type': 'minecraft:heightmap', 'heightmap': 'WORLD_SURFACE_WG'}, {'type': 'minecraft:biome'}]})
write(os.path.join(DATA, 'tags', 'worldgen', 'biome', 'has_ruined_forest_gate.json'), {'replace': False, 'values': [
    '#minecraft:is_forest', 'minecraft:cherry_grove', 'minecraft:meadow', 'minecraft:old_growth_birch_forest', {'id': '#c:is_forest', 'required': False}]})
write(os.path.join(DATA, 'tags', 'worldgen', 'biome', 'has_ruined_sky_portal.json'), {'replace': False, 'values': [
    '#minecraft:is_mountain', '#minecraft:is_hill', 'minecraft:snowy_plains', 'minecraft:snowy_taiga', 'minecraft:plains', 'minecraft:savanna_plateau',
    {'id': '#c:is_mountain', 'required': False}, {'id': '#c:is_snowy', 'required': False}]})
write(os.path.join(DATA, 'loot_table', 'chests', 'ruined_forest_gate.json'), {
    'type': 'minecraft:chest',
    'pools': [{'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 6},
               'entries': [{'type': 'minecraft:item', 'name': i, 'weight': w, 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': c}}]}
                           for i, w, c in (('minecraft:moss_block', 10, 4), ('minecraft:glow_berries', 8, 4), ('minecraft:honey_bottle', 7, 2), ('minecraft:amethyst_shard', 6, 3),
                                           ('minecraft:emerald', 4, 2), ('minecraft:ender_pearl', 2, 1), ('minecraft:golden_apple', 2, 1), ('omnispell:arcane_potion', 3, 1))]}]})
for wood_block in ('faewood_log', 'faewood_planks', 'runic_bookshelf', 'forest_gate'):
    write(os.path.join(DATA, 'loot_table', 'blocks', wood_block + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                   'entries': [{'type': 'minecraft:item', 'name': f'omnispell:{wood_block}'}]}]})
# Faewood blossom sometimes comes away whole; otherwise it scatters.
write(os.path.join(DATA, 'loot_table', 'blocks', 'faewood_leaves.json'), {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.25}],
               'entries': [{'type': 'minecraft:item', 'name': 'omnispell:faewood_leaves'}]}]})
for tag, values in {'block/mineable/axe': ['omnispell:faewood_log', 'omnispell:faewood_planks', 'omnispell:runic_bookshelf'],
                    'block/mineable/hoe': ['omnispell:faewood_leaves'],
                    'block/logs': ['omnispell:faewood_log'], 'item/logs': ['omnispell:faewood_log'],
                    'block/planks': ['omnispell:faewood_planks'], 'item/planks': ['omnispell:faewood_planks'],
                    'block/enchantment_power_provider': ['omnispell:runic_bookshelf']}.items():
    write(os.path.join(ROOT, 'data', 'minecraft', 'tags', tag + '.json'), {'replace': False, 'values': values})
write(os.path.join(recipe_dir, 'wizard_hat_dyed.json'), {'type': 'minecraft:crafting_dye', 'dye': '#minecraft:dyes', 'group': 'dyed_armor',
                                                       'result': {'id': 'omnispell:wizard_hat'}, 'target': 'omnispell:wizard_hat'})
for piece in ('wizard_robe', 'wizard_boots'):
    write(os.path.join(recipe_dir, piece + '_dyed.json'), {'type': 'minecraft:crafting_dye', 'dye': '#minecraft:dyes', 'group': 'dyed_armor',
                                                         'result': {'id': 'omnispell:' + piece}, 'target': 'omnispell:' + piece})
write(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'item', 'cauldron_can_remove_dye.json'), {'replace': False, 'values': ['omnispell:wizard_hat', 'omnispell:wizard_robe', 'omnispell:wizard_boots']})
write(os.path.join(recipe_dir, 'arcane_focus.json'), {
    'type': 'minecraft:crafting_shaped', 'category': 'equipment',
    'key': {'G': 'minecraft:gold_ingot', 'A': 'minecraft:amethyst_shard', 'L': 'minecraft:lapis_lazuli'},
    'pattern': [' G ', 'ALA', ' G '], 'result': {'id': 'omnispell:arcane_focus'}})
# An abandoned alchemy cauldron over a campfire, rare, in the stranger biomes.
write(os.path.join(DATA, 'worldgen', 'feature', 'abandoned_cauldron.json'), {
    'type': 'minecraft:block_column',
    'allowed_placement': {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:air'},
    'direction': 'up', 'prioritize_tip': False,
    'layers': [{'height': 1, 'provider': {'id': 'minecraft:campfire', 'properties': {'facing': 'north', 'lit': 'true', 'signal_fire': 'false', 'waterlogged': 'false'}}},
               {'height': 1, 'provider': {'id': 'omnispell:alchemy_cauldron'}}]})
write(os.path.join(DATA, 'worldgen', 'placed_feature', 'abandoned_cauldron.json'), {
    'feature': 'omnispell:abandoned_cauldron',
    'placement': [{'type': 'minecraft:rarity_filter', 'chance': 90}, {'type': 'minecraft:in_square'},
                  {'type': 'minecraft:heightmap', 'heightmap': 'MOTION_BLOCKING_NO_LEAVES'}, {'type': 'minecraft:biome'},
                  {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:all_of', 'predicates': [
                      {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:air'},
                      {'type': 'minecraft:solid', 'offset': [0, -1, 0]}]}}]})
write(os.path.join(DATA, 'tags', 'worldgen', 'biome', 'has_abandoned_cauldron.json'), {'replace': False, 'values': [
    'minecraft:swamp', 'minecraft:mangrove_swamp', 'minecraft:dark_forest', 'minecraft:pale_garden', 'minecraft:mushroom_fields',
    'minecraft:old_growth_spruce_taiga', 'minecraft:soul_sand_valley', 'minecraft:warped_forest',
    'omnispell:gloamwood', 'omnispell:glowcap_glade', 'omnispell:palewood',
    {'id': '#c:is_swamp', 'required': False}, {'id': '#c:is_magical', 'required': False}, {'id': '#c:is_spooky', 'required': False},
    {'id': '#c:is_mushroom', 'required': False}, {'id': '#c:is_dead', 'required': False}]})
# ----- the Enchanted Forest's ruins and nests
for name, chance in (('forest_ruin', 28), ('familiar_nest', 40)):
    write(os.path.join(DATA, 'worldgen', 'feature', name + '.json'), {'type': f'omnispell:{name}'})
    write(os.path.join(DATA, 'worldgen', 'placed_feature', name + '.json'), {
        'feature': f'omnispell:{name}',
        'placement': [{'type': 'minecraft:rarity_filter', 'chance': chance}, {'type': 'minecraft:in_square'},
                      {'type': 'minecraft:heightmap', 'heightmap': 'WORLD_SURFACE_WG'}, {'type': 'minecraft:biome'}]})
write(os.path.join(DATA, 'tags', 'worldgen', 'biome', 'is_enchanted_forest.json'), {'replace': False, 'values': [
    'omnispell:faewood_grove', 'omnispell:gloamwood', 'omnispell:glowcap_glade', 'omnispell:palewood', 'omnispell:ancient_spires']})
def pool_table(name, rolls, items):
    write(os.path.join(DATA, 'loot_table', 'chests', name + '.json'), {
        'type': 'minecraft:chest',
        'pools': [{'rolls': {'type': 'minecraft:uniform', 'min': rolls[0], 'max': rolls[1]},
                   'entries': [{'type': 'minecraft:item', 'name': i, 'weight': w,
                                'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': c}}]} for i, w, c in items]}]})
pool_table('forest_ruin', (4, 7), [('omnispell:fae_dust', 10, 4), ('minecraft:amethyst_shard', 8, 5), ('minecraft:emerald', 6, 4), ('minecraft:book', 6, 3),
                                   ('minecraft:ender_pearl', 4, 2), ('minecraft:diamond', 3, 2), ('minecraft:echo_shard', 2, 2), ('omnispell:arcane_potion', 5, 2),
                                   ('omnispell:faewood_log', 6, 6), ('minecraft:experience_bottle', 4, 4)])
pool_table('familiar_nest', (2, 4), [('minecraft:feather', 10, 4), ('omnispell:fae_dust', 8, 3), ('minecraft:glow_berries', 6, 4), ('minecraft:moss_block', 4, 2)])

# ----- villager trades: scrolls for emeralds
def scroll(rarity):
    names = ['', 'Common', 'Uncommon', 'Rare', 'Ancient', 'Forbidden']
    looks = ['', 'common', 'common', 'uncommon', 'rare', 'epic']
    return {'id': 'omnispell:word_scroll', 'components': {
        'minecraft:custom_data': {'omni_rarity': rarity},
        'minecraft:item_name': names[rarity] + ' Word Scroll',
        'minecraft:rarity': looks[rarity],
        'minecraft:lore': [{'text': 'Read it to learn a word of power.', 'color': 'gray', 'italic': True}]}}
TRADES = {  # tag -> [(trade name, emeralds, what it gives, uses)]
    'librarian/level_1': [('librarian/1/scroll_common', 6, scroll(1), 4)],
    'librarian/level_2': [('librarian/2/scroll_common', 5, scroll(1), 6)],
    'librarian/level_3': [('librarian/3/scroll_uncommon', 12, scroll(2), 4)],
    'librarian/level_4': [('librarian/4/scroll_rare', 24, scroll(3), 1)],
    'librarian/level_5': [('librarian/5/scroll_ancient', 40, scroll(4), 1)],
    'cleric/level_2': [('cleric/2/arcane_potion', 4, {'id': 'omnispell:arcane_potion'}, 8)],
    'cleric/level_4': [('cleric/4/scroll_uncommon', 14, scroll(2), 3)],
    'cleric/level_5': [('cleric/5/scroll_rare', 28, scroll(3), 1)],
    'wandering_trader/uncommon': [('wandering_trader/scroll_rare', 20, scroll(3), 1), ('wandering_trader/scroll_ancient', 36, scroll(4), 1)],
    'wandering_trader/common': [('wandering_trader/scroll_uncommon', 10, scroll(2), 2)],
}
def charmed(gem, charm, level):
    return {'id': gem, 'components': {'minecraft:enchantments': {f'omnispell:{charm}': level}}}
def named_scroll(key, value, name):
    return {'id': 'omnispell:word_scroll', 'components': {'minecraft:custom_data': {key: value}, 'minecraft:item_name': name, 'minecraft:rarity': 'epic'}}
# The Arcanist (workstation: an Arcane Well). ('buy', item, count) trades pay one emerald for goods.
ARCANIST = {
    1: [('buy', 'minecraft:amethyst_shard', 8), ('buy', 'minecraft:paper', 20), ('sell', 5, scroll(1), 6), ('sell', 3, {'id': 'omnispell:arcane_potion'}, 8)],
    2: [('buy', 'minecraft:lapis_lazuli', 10), ('buy', 'minecraft:glass_bottle', 9), ('sell', 10, scroll(2), 5), ('sell', 6, {'id': 'minecraft:amethyst_shard', 'count': 4}, 8)],
    3: [('buy', 'omnispell:fae_dust', 4), ('sell', 20, scroll(3), 1), ('sell', 16, {'id': 'omnispell:arcane_focus'}, 1),
        ('sell', 18, charmed('minecraft:emerald', 'fortune', 1), 1), ('sell', 18, charmed('minecraft:amethyst_shard', 'flow', 2), 1)],
    4: [('buy', 'minecraft:ender_pearl', 2), ('sell', 34, scroll(4), 1), ('sell', 30, charmed('minecraft:diamond', 'warding', 2), 1),
        ('sell', 30, charmed('minecraft:echo_shard', 'leech', 2), 1), ('sell', 28, charmed('minecraft:quartz', 'potency', 2), 1)],
    5: [('sell', 56, scroll(5), 1), ('sell', 48, named_scroll('omni_type', 'conjuration', 'Treatise on Conjuration'), 1),
        ('sell', 52, named_scroll('omni_word', 'charge:Sparsus (Area of effect)', 'Scroll of Sparsus'), 1),
        ('sell', 44, charmed('minecraft:diamond', 'vigor', 3), 1)],
}
for level, offers in ARCANIST.items():
    names = []
    for n, offer in enumerate(offers):
        name = f'arcanist/{level}/{n}'
        names.append('omnispell:' + name)
        if offer[0] == 'buy':
            body = {'wants': {'id': offer[1], 'count': offer[2]}, 'gives': {'id': 'minecraft:emerald'}, 'max_uses': 16, 'xp': 2 * level}
        else:
            body = {'wants': {'id': 'minecraft:emerald', 'count': offer[1]}, 'gives': offer[2], 'max_uses': offer[3], 'xp': 5 * level}
        body['reputation_discount'] = 0.05
        write(os.path.join(DATA, 'villager_trade', name + '.json'), body)
    write(os.path.join(DATA, 'tags', 'villager_trade', 'arcanist', f'level_{level}.json'), {'replace': False, 'values': names})
    write(os.path.join(DATA, 'trade_set', 'arcanist', f'level_{level}.json'),
          {'amount': 2 if level < 5 else 3, 'random_sequence': f'omnispell:trade_set/arcanist/level_{level}', 'trades': f'#omnispell:arcanist/level_{level}'})
write(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'point_of_interest_type', 'acquirable_job_site.json'), {'replace': False, 'values': ['omnispell:arcanist']})
# ----- The same trades for VillagersTradingPlus (in the content pack), which replaces the game's villager trading
# with its own data and shows each level's trades in its Catalog. Without these, the Arcanist's later trades would
# not show there, and the scroll trades of librarians, clerics, alchemists and wandering traders would be lost.
LEVEL_NAMES = {1: 'novice', 2: 'apprentice', 3: 'journeyman', 4: 'expert', 5: 'master'}
def vtp_item(gives):
    out = {'item': gives['id'], 'count': gives.get('count', 1)}
    comps = gives.get('components', {})
    if 'minecraft:item_name' in comps: out['name'] = json.dumps({'text': comps['minecraft:item_name'], 'italic': False})
    data = comps.get('minecraft:custom_data')
    if data: out['nbt'] = '{' + ','.join(f'{k}:{json.dumps(v) if isinstance(v, str) else v}' for k, v in data.items()) + '}'
    ench = comps.get('minecraft:enchantments')
    if ench: out['enchantments'] = [{'id': k, 'lvl': v} for k, v in ench.items()]
    return out
def vtp_sell(price, gives, uses, xp):
    return {'type': 'villagertradingplus:sell_item', 'sell': vtp_item(gives), 'priceIn': {'item': 'minecraft:emerald', 'count': price},
            'max_uses': uses, 'villager_experience': xp, 'price_multiplier': 0.05}
def vtp_buy(item, count, xp):
    return {'type': 'villagertradingplus:buy_item', 'buy': {'item': item, 'count': count}, 'reward': {'item': 'minecraft:emerald', 'count': 1},
            'max_uses': 16, 'villager_experience': xp, 'price_multiplier': 0.05}
VTP = os.path.join(DATA)
arcanist = {'profession': 'omnispell:arcanist', 'trades': {}}
for level, offers in ARCANIST.items():
    arcanist['trades'][LEVEL_NAMES[level]] = [vtp_buy(o[1], o[2], 2 * level) if o[0] == 'buy' else vtp_sell(o[1], o[2], o[3], 5 * level) for o in offers]
write(os.path.join(VTP, 'default_villager_trades', 'arcanist.json'), arcanist)
extra = {'minecraft:librarian': {}, 'minecraft:cleric': {}}
for tag, trades in TRADES.items():
    who, lvl = tag.split('/')
    if who == 'wandering_trader': continue
    extra['minecraft:' + who].setdefault(LEVEL_NAMES[int(lvl[-1])], []).extend(vtp_sell(cost, gives, uses, 10) for _, cost, gives, uses in trades)
# The Villagers Plus alchemist deals in rare scrolls and arcane potions too.
extra['villagersplus:alchemist'] = {'apprentice': [vtp_sell(4, {'id': 'omnispell:arcane_potion'}, 8, 10)],
                                    'expert': [vtp_sell(22, scroll(3), 1, 15)], 'master': [vtp_sell(38, scroll(4), 1, 20)]}
for prof, levels in extra.items():
    write(os.path.join(VTP, 'villager_trades', prof.split(':')[1] + '.json'), {'profession': prof, 'trades': levels})
write(os.path.join(VTP, 'wandering_trader_trades', 'omnispell.json'), {'replace': False, 'trades': {
    'common': [vtp_sell(10, scroll(2), 2, 1)],
    'rare': [vtp_sell(20, scroll(3), 1, 1), vtp_sell(36, scroll(4), 1, 1)]}})
# The Arcanist's robe: violet with a gold hem, over the ordinary villager.
def robe(x, y):
    star = noise(x, y, 67) > 0.93
    gold = (236, 196, 84, 255)
    if 38 <= y < 64 and x < 28:          # the long outer robe: midnight blue, sewn with stars, gold at the hem
        if y >= 61 or y == 38: return gold
        return gold if star else mix((28, 30, 84), (52, 50, 128), noise(x, y, 53))
    if 20 <= y < 38 and 16 <= x < 44:    # chest: a gold collar and a clasp
        if y < 23 or (x in (29, 30) and y < 30): return gold
        return gold if star else mix((34, 34, 96), (58, 54, 136), noise(x, y, 59))
    if 22 <= y < 38 and 44 <= x < 60:    # sleeves
        return gold if y >= 35 else mix((28, 30, 84), (52, 50, 128), noise(x, y, 71))
    if y < 8 and 8 <= x < 24: return gold if star else mix((28, 30, 84), (52, 50, 128), noise(x, y, 61))   # the hood's crown
    if 8 <= y < 12 and x < 32: return gold if y == 11 else mix((28, 30, 84), (52, 50, 128), noise(x, y, 61))  # and its brim, all the way round
    return (0, 0, 0, 0)
for folder in ('villager', 'zombie_villager'):
    img = Image.new('RGBA', (64, 64))
    for y in range(64):
        for x in range(64):
            img.putpixel((x, y), robe(x, y))
    path = os.path.join(ASSETS, 'textures', 'entity', folder, 'profession', 'arcanist.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    write(path + '.mcmeta', {'villager': {'hat': 'full'}})

for tag, trades in TRADES.items():
    for name, cost, gives, uses in trades:
        write(os.path.join(DATA, 'villager_trade', name + '.json'), {
            'gives': gives, 'max_uses': uses, 'reputation_discount': 0.05, 'xp': 10,
            'wants': {'id': 'minecraft:emerald', 'count': cost}})
    write(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'villager_trade', tag + '.json'),
          {'replace': False, 'values': ['omnispell:' + name for name, _, _, _ in trades]})

# Until arcane wells generate in structures, one can be built.
write(os.path.join(recipe_dir, 'arcane_well.json'), {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'key': {'S': 'minecraft:stone_bricks', 'A': 'minecraft:amethyst_shard', 'B': 'minecraft:water_bucket'},
    'pattern': ['SAS', 'SBS', 'SSS'],
    'result': {'id': 'omnispell:arcane_well'}})
# An arcane well only comes away whole with Silk Touch. Otherwise it shatters into a shard or two of amethyst.
SILK = {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [
    {'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}
write(os.path.join(DATA, 'loot_table', 'blocks', 'arcane_well.json'), {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:alternatives', 'children': [
        {'type': 'minecraft:item', 'name': 'omnispell:arcane_well', 'conditions': [SILK]},
        {'type': 'minecraft:item', 'name': 'minecraft:amethyst_shard', 'conditions': [{'condition': 'minecraft:survives_explosion'}],
         'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}}]}]}]}]})
write(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'),
      {'replace': False, 'values': ['omnispell:arcane_well', 'omnispell:waypoint', 'omnispell:alchemy_cauldron', 'omnispell:forest_gate']})

# ---------------------------------------------------------------- enchantments
def enchantment(name, fallback, weight, max_level):
    write(os.path.join(DATA, 'enchantment', name + '.json'), {
        'description': {'translate': f'enchantment.omnispell.{name}', 'fallback': fallback},
        'supported_items': '#omnispell:enchantable/mana_gear',
        'weight': weight, 'max_level': max_level,
        'min_cost': {'base': 8, 'per_level_above_first': 9},
        'max_cost': {'base': 40, 'per_level_above_first': 9},
        'anvil_cost': 2, 'slots': ['armor']})
enchantment('reservoir', 'Reservoir', 4, 3)    # +10% to every mana reserve per level, per piece
enchantment('wellspring', 'Wellspring', 4, 3)  # +15% mana refill speed per level, per piece
enchantment('alacrity', 'Alacrity', 3, 3)      # -4% spell cooldown per level, per piece
enchantment('attunement', 'Attunement', 2, 2)  # +1 charm slot per level
# Charm enchantments only ever sit on gems. The mod itself decides which gem gets which.
write(os.path.join(DATA, 'tags', 'item', 'enchantable', 'mana_gear.json'), {'replace': False, 'values': ['#minecraft:enchantable/armor', 'omnispell:wizard_hat', 'omnispell:wizard_robe', 'omnispell:wizard_boots', 'omnispell:wand']})
GEMS = ['minecraft:totem_of_undying', 'minecraft:amethyst_shard', 'minecraft:lapis_lazuli', 'minecraft:quartz', 'minecraft:prismarine_crystals',
        'minecraft:emerald', 'minecraft:echo_shard', 'minecraft:diamond', 'minecraft:netherite_ingot']
write(os.path.join(DATA, 'tags', 'item', 'charm_gems.json'), {'replace': False, 'values': GEMS})
for cid, cname, _ in CHARMS:
    write(os.path.join(DATA, 'enchantment', cid + '.json'), {
        'description': {'translate': f'enchantment.omnispell.{cid}', 'fallback': cname},
        'supported_items': '#omnispell:charm_gems',
        'weight': 1, 'max_level': 3,
        'min_cost': {'base': 1, 'per_level_above_first': 10},
        'max_cost': {'base': 50, 'per_level_above_first': 10},
        'anvil_cost': 2, 'slots': ['any']})
for tag in ('in_enchanting_table', 'non_treasure'):
    path = os.path.join(ROOT, 'data', 'minecraft', 'tags', 'enchantment', tag + '.json')
    current = json.load(open(path)) if os.path.exists(path) else {'replace': False, 'values': []}
    for e in ('omnispell:reservoir', 'omnispell:wellspring', 'omnispell:alacrity', 'omnispell:attunement'):
        if e not in current['values']: current['values'].append(e)
    with open(path, 'w') as f: json.dump(current, f, indent='\t')

print(f'{len(woods)} woods x {len(cores)} cores = {len(woods) * len(cores)} wand recipes')
