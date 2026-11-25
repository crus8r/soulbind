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

# ---------------------------------------------------------------- names
lang = {
    'item.omnispell.wand': 'Wand',
    'item.omnispell.grimoire': 'Book of Words',
    'item.omnispell.word_scroll': 'Word Scroll',
    'item.omnispell.arcane_potion': 'Arcane Potion',
    'block.omnispell.arcane_well': 'Arcane Well',
    'enchantment.omnispell.reservoir': 'Reservoir',
    'enchantment.omnispell.wellspring': 'Wellspring',
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
      {'replace': False, 'values': ['omnispell:arcane_well']})

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
for tag in ('in_enchanting_table', 'non_treasure'):
    path = os.path.join(ROOT, 'data', 'minecraft', 'tags', 'enchantment', tag + '.json')
    current = json.load(open(path)) if os.path.exists(path) else {'replace': False, 'values': []}
    for e in ('omnispell:reservoir', 'omnispell:wellspring'):
        if e not in current['values']: current['values'].append(e)
    with open(path, 'w') as f: json.dump(current, f, indent='\t')

print(f'{len(woods)} woods x {len(cores)} cores = {len(woods) * len(cores)} wand recipes')
