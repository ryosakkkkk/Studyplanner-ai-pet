#!/bin/bash
# zone masks for the new GLBs, copy into the v22 viewer test copy, inject zone stats
cd /home/claude/w/rig4
for p in panda zorro gato buho pinguino conejo; do timeout 900 python3 /home/claude/w/rig2/zonemask.py $p glb/$p.glb zonas > out/${p}_zonas.log 2>&1 & done; wait
for p in panda zorro gato buho pinguino conejo; do cp -f glb/$p.glb /home/claude/w/v22web/pets/$p.glb; cp -f zonas/${p}_zonas.png /home/claude/w/v22web/pets/${p}_zonas.png; done
python3 - <<'PY'
import json, glob, os, re
Z = {}
for f in sorted(glob.glob('/home/claude/w/rig4/zonas/*_zonas.json')):
    pet = os.path.basename(f).replace('_zonas.json', '')
    Z[pet] = [[z[0], i, z[1], z[2]] for i, z in enumerate(json.load(open(f)))]
v='/home/claude/w/v22web/pet-viewer.html'
s = open(v).read()
s = re.sub(r'const ZONES = /\*ZONES\*/\{.*?\};', lambda m: 'const ZONES = /*ZONES*/' + json.dumps(Z, ensure_ascii=False, separators=(',', ':')) + ';', s, flags=re.S)
open(v,'w').write(s); print('zonas', {k: len(v) for k, v in Z.items()})
PY
