#!/usr/bin/env python3
"""Builds the Omnispell content pack: a Modrinth pack file (.mrpack) that lists
Omnispell's companion mods for the Modrinth app, Prism Launcher and similar.

The pack only points at each mod's own download on Modrinth; nothing is copied
or changed, so every mod stays under its own licence.

  python3 tools/make_pack.py            resolve versions, write pack/omnispell-pack.mrpack
  python3 tools/make_pack.py --download also download every jar to pack/mods (for testing)

Edit pack/mods.txt to add or remove mods (one Modrinth slug per line, # for notes).
"""
import hashlib, json, os, sys, time, urllib.parse, urllib.request, zipfile

GAME, LOADER, LOADER_VERSION = '26.3', 'fabric', '0.20.1'
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
PACK = os.path.join(ROOT, 'pack')
API = 'https://api.modrinth.com/v2/'

def get(path):
    req = urllib.request.Request(API + path, headers={'User-Agent': 'modpack-builder/1.0'})
    for attempt in range(4):
        try:
            return json.load(urllib.request.urlopen(req, timeout=40))
        except Exception as e:
            err = e
            time.sleep(2 + attempt * 2)
    raise err

def best_version(project):
    q = urllib.parse.urlencode({'loaders': json.dumps([LOADER]), 'game_versions': json.dumps([GAME])})
    versions = get(f'project/{urllib.parse.quote(project)}/version?{q}')
    if not versions:
        return None
    rank = {'release': 0, 'beta': 1, 'alpha': 2}
    versions.sort(key=lambda v: (rank.get(v['version_type'], 3), v['date_published']), reverse=False)
    newest_release = [v for v in versions if v['version_type'] == versions[0]['version_type']]
    return max(newest_release, key=lambda v: v['date_published'])

def main():
    wanted = [l.split('#')[0].strip() for l in open(os.path.join(PACK, 'mods.txt'))]
    wanted = [w for w in wanted if w]
    chosen, missing, queue, seen = {}, [], list(wanted), set()
    while queue:
        slug = queue.pop(0)
        if slug in seen:
            continue
        seen.add(slug)
        try:
            project = get('project/' + urllib.parse.quote(slug))
        except Exception:
            missing.append(slug + ' (not on Modrinth)')
            continue
        if project['id'] in chosen:
            continue
        version = best_version(project['id'])
        if version is None:
            missing.append(f"{project['title']} (no {LOADER} {GAME} version)")
            continue
        chosen[project['id']] = (project, version)
        for dep in version['dependencies']:
            if dep['dependency_type'] == 'required' and dep.get('project_id') and dep['project_id'] not in chosen:
                queue.append(dep['project_id'])
        time.sleep(0.15)

    files, report = [], []
    for project, version in sorted(chosen.values(), key=lambda pv: pv[0]['title'].lower()):
        if project['slug'] == 'fabric-api':
            pass
        f = next((x for x in version['files'] if x['primary']), version['files'][0])
        files.append({'path': 'mods/' + f['filename'], 'hashes': {'sha1': f['hashes']['sha1'], 'sha512': f['hashes']['sha512']},
                      'env': {'client': 'required', 'server': 'required'}, 'downloads': [f['url']], 'fileSize': f['size']})
        report.append(f"{project['title']} | {version['version_number']} | {project['license']['id']} | {project['slug']}")

    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': '0.1.0', 'name': 'Omnispell World',
             'summary': 'Omnispell with its companion structure, biome and creature mods.',
             'files': files, 'dependencies': {'minecraft': GAME, 'fabric-loader': LOADER_VERSION}}
    os.makedirs(PACK, exist_ok=True)
    out = os.path.join(PACK, 'omnispell-pack.mrpack')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('modrinth.index.json', json.dumps(index, indent=2))
        libs = os.path.join(ROOT, 'build', 'libs')
        jars = sorted(j for j in os.listdir(libs) if j.startswith('omnispell-') and j.endswith('.jar') and 'sources' not in j) if os.path.isdir(libs) else []
        if jars:
            z.write(os.path.join(libs, jars[-1]), 'overrides/mods/' + jars[-1])
    with open(os.path.join(PACK, 'mods-resolved.txt'), 'w') as f:
        f.write('\n'.join(report) + '\n')
        if missing:
            f.write('\nNOT INCLUDED:\n' + '\n'.join(missing) + '\n')
    print(f'{len(files)} mods in {out}')
    for m in missing:
        print('  missing:', m)

    if '--download' in sys.argv:
        mods = os.path.join(PACK, 'mods')
        os.makedirs(mods, exist_ok=True)
        keep = set()
        for entry in files:
            name = os.path.basename(entry['path'])
            keep.add(name)
            target = os.path.join(mods, name)
            if os.path.exists(target) and hashlib.sha1(open(target, 'rb').read()).hexdigest() == entry['hashes']['sha1']:
                continue
            req = urllib.request.Request(entry['downloads'][0], headers={'User-Agent': 'modpack-builder/1.0'})
            data = urllib.request.urlopen(req, timeout=120).read()
            assert hashlib.sha1(data).hexdigest() == entry['hashes']['sha1'], name
            open(target, 'wb').write(data)
            time.sleep(0.2)
        for name in os.listdir(mods):
            if name not in keep:
                os.remove(os.path.join(mods, name))
        print('downloaded to', mods)

if __name__ == '__main__':
    main()
