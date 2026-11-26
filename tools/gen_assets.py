#!/usr/bin/env python3
"""Generates Omnispell's textures, models, names, recipes and enchantments.

Run from the repository root:  python3 tools/gen_assets.py
Wand woods and cores are read from WandParts.java so there is one list to edit.
"""
import json, os, re
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

# ---------------------------------------------------------------- models
def flat_item(name, layers, parent='minecraft:item/generated'):
    write(os.path.join(ASSETS, 'models', 'item', name + '.json'),
          {'parent': parent, 'textures': {f'layer{i}': f'omnispell:item/{t}' for i, t in enumerate(layers)}})

flat_item('wand', ['wand_wood', 'wand_core'], 'minecraft:item/handheld')
for n in ('grimoire', 'word_scroll', 'arcane_potion'):
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
    'block.omnispell.waypoint': 'Waypoint',
    'block.omnispell.boss_altar': 'Boss Seal',
    'block.omnispell.alchemy_cauldron': 'Alchemy Cauldron',
    'item.omnispell.brew': 'Potion',
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
# The alchemy cauldron needs a blaze rod, so brewing waits for the Nether, as in vanilla.
write(os.path.join(recipe_dir, 'alchemy_cauldron.json'), {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': ['minecraft:cauldron', 'minecraft:blaze_rod', 'minecraft:amethyst_shard'],
    'result': {'id': 'omnispell:alchemy_cauldron'}})
write(os.path.join(DATA, 'loot_table', 'blocks', 'alchemy_cauldron.json'), {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
               'entries': [{'type': 'minecraft:item', 'name': 'omnispell:alchemy_cauldron'}]}]})
# Until arcane wells generate in structures, one can be built.
write(os.path.join(recipe_dir, 'arcane_well.json'), {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'key': {'S': 'minecraft:stone_bricks', 'A': 'minecraft:amethyst_shard', 'B': 'minecraft:water_bucket'},
    'pattern': ['SAS', 'SBS', 'SSS'],
    'result': {'id': 'omnispell:arcane_well'}})
write(os.path.join(DATA, 'loot_table', 'blocks', 'arcane_well.json'), {
    'type': 'minecraft:block',
    'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
               'entries': [{'type': 'minecraft:item', 'name': 'omnispell:arcane_well'}]}]})
write(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'),
      {'replace': False, 'values': ['omnispell:arcane_well', 'omnispell:waypoint', 'omnispell:alchemy_cauldron']})

# ---------------------------------------------------------------- enchantments
def enchantment(name, fallback, weight, max_level):
    write(os.path.join(DATA, 'enchantment', name + '.json'), {
        'description': {'translate': f'enchantment.omnispell.{name}', 'fallback': fallback},
        'supported_items': '#minecraft:enchantable/armor',
        'weight': weight, 'max_level': max_level,
        'min_cost': {'base': 8, 'per_level_above_first': 9},
        'max_cost': {'base': 40, 'per_level_above_first': 9},
        'anvil_cost': 2, 'slots': ['armor']})
enchantment('reservoir', 'Reservoir', 4, 3)    # +10% to every mana reserve per level, per piece
enchantment('wellspring', 'Wellspring', 4, 3)  # +15% mana refill speed per level, per piece
enchantment('alacrity', 'Alacrity', 3, 3)      # -4% spell cooldown per level, per piece
for tag in ('in_enchanting_table', 'non_treasure'):
    path = os.path.join(ROOT, 'data', 'minecraft', 'tags', 'enchantment', tag + '.json')
    current = json.load(open(path)) if os.path.exists(path) else {'replace': False, 'values': []}
    for e in ('omnispell:reservoir', 'omnispell:wellspring', 'omnispell:alacrity'):
        if e not in current['values']: current['values'].append(e)
    with open(path, 'w') as f: json.dump(current, f, indent='\t')

print(f'{len(woods)} woods x {len(cores)} cores = {len(woods) * len(cores)} wand recipes')
