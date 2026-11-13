#!/usr/bin/env python3
"""Del barrido del visor a la lista de celdas expuestas que usa el forro: expuesto.py <tag> [mínimo de píxeles por celda=2]
Lee scan/<tag>/<pet>_pos.json de las seis mascotas y escribe rig4/expuesto/<pet>.json (suma con lo que ya había)."""
import sys, json, os
tag = sys.argv[1]; mn = int(sys.argv[2]) if len(sys.argv) > 2 else 2
for pet in ('zorro', 'gato', 'panda', 'buho', 'pinguino', 'conejo'):
    f = f'/home/claude/w/v23test/scan/{tag}/{pet}_pos.json'
    if not os.path.exists(f): continue
    d = json.load(open(f))
    keys = {int(v[0]) for v in d['vox'] if v[1] >= mn}
    out = f'/home/claude/w/rig4/expuesto/{pet}.json'
    old = set(json.load(open(out))['celdas']) if os.path.exists(out) and '--nuevo' not in sys.argv else set()
    allk = sorted(keys | old)
    json.dump({'celdas': allk, 'origen': tag}, open(out, 'w'))
    print(pet, 'celdas del barrido', len(keys), '| acumuladas', len(allk))
