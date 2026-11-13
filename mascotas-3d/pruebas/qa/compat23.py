#!/usr/bin/env python3
"""Compatibilidad de un GLB nuevo con el anterior, mirando lo que usa la app (visor three.js):
nombres y jerarquía de nodos y huesos, orden de las articulaciones de la piel, clips (nombre, duración, canales),
materiales (nombre, doble cara, modo alfa, texturas), atributos de la malla, forma MouthOpen, escala y caja.
uso: compat23.py <anterior.glb> <nuevo.glb>   -> imprime diferencias y termina con 0 si es compatible."""
import sys, json, struct
import numpy as np

def load(p):
    b = open(p, 'rb').read()
    jl = struct.unpack('<I', b[12:16])[0]
    J = json.loads(b[20:20 + jl]); bin0 = 20 + jl + 8
    return J, b, bin0

def acc_arr(J, b, bin0, i):
    a = J['accessors'][i]; bv = J['bufferViews'][a['bufferView']]
    n = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}[a['type']]
    dt = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}[a['componentType']]
    off = bin0 + bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    st = bv.get('byteStride', 0); sz = np.dtype(dt).itemsize * n
    if st and st != sz:
        raw = np.frombuffer(b, np.uint8, count=(a['count'] - 1) * st + sz, offset=off)
        idx = (np.arange(a['count'])[:, None] * st + np.arange(sz)[None, :]).ravel()
        return raw[idx].copy().view(dt).reshape(a['count'], n)
    return np.frombuffer(b, dt, count=a['count'] * n, offset=off).reshape(a['count'], n)

def describe(p):
    J, b, bin0 = load(p)
    N = J['nodes']; par = {}
    for i, n in enumerate(N):
        for c in n.get('children', []): par[c] = i
    d = {}
    d['nodos'] = {n.get('name', f'#{i}'): N[par[i]].get('name') if i in par else None for i, n in enumerate(N)}
    d['trs'] = {n.get('name', f'#{i}'): (n.get('translation', [0, 0, 0]), n.get('rotation', [0, 0, 0, 1]), n.get('scale', [1, 1, 1])) for i, n in enumerate(N)}
    d['pieles'] = [[N[j].get('name') for j in s['joints']] for s in J.get('skins', [])]
    an = {}
    for a in J.get('animations', []):
        dur = 0.0; ch = set()
        for c in a['channels']:
            smp = a['samplers'][c['sampler']]
            dur = max(dur, float(J['accessors'][smp['input']]['max'][0]))
            ch.add((N[c['target']['node']].get('name'), c['target']['path']))
        an[a['name']] = (round(dur, 4), ch)
    d['clips'] = an
    d['materiales'] = {m.get('name'): dict(doble=m.get('doubleSided', False), alfa=m.get('alphaMode', 'OPAQUE'),
                                            base='baseColorTexture' in m.get('pbrMetallicRoughness', {}), normal='normalTexture' in m) for m in J.get('materials', [])}
    ms = {}
    for m in J.get('meshes', []):
        prims = []
        for pr in m['primitives']:
            pa = J['accessors'][pr['attributes']['POSITION']]
            prims.append(dict(attrs=sorted(pr['attributes'].keys()), n=pa['count'], tris=J['accessors'][pr['indices']]['count'] // 3 if 'indices' in pr else pa['count'] // 3,
                              mn=pa['min'], mx=pa['max'], morph=len(pr.get('targets', [])), mat=J['materials'][pr['material']].get('name') if 'material' in pr else None))
        ms[m.get('name')] = dict(prims=prims, formas=m.get('extras', {}).get('targetNames', []))
    d['mallas'] = ms
    d['texturas'] = [(i.get('mimeType'), i.get('name')) for i in J.get('images', [])]
    d['ext'] = sorted(J.get('extensionsUsed', []))
    # influencias: pesos por vértice de la malla mayor
    best = None
    for m in J.get('meshes', []):
        for pr in m['primitives']:
            if 'WEIGHTS_0' in pr['attributes']:
                n = J['accessors'][pr['attributes']['POSITION']]['count']
                if best is None or n > best[0]: best = (n, pr)
    if best:
        w = acc_arr(J, b, bin0, best[1]['attributes']['WEIGHTS_0']).astype(np.float64)
        if w.dtype != np.float64: w = w.astype(np.float64)
        d['influencias'] = dict(max_no_cero=int((w > 1e-6).sum(1).max()), tiene_WEIGHTS_1='WEIGHTS_1' in best[1]['attributes'])
    return d

A = describe(sys.argv[1]); B = describe(sys.argv[2])
bad = 0
def chk(name, ok, detail=''):
    global bad
    print(('  igual     ' if ok else '  DIFERENTE ') + name + (': ' + detail if detail else ''))
    if not ok: bad += 1
print('COMPATIBILIDAD', sys.argv[1].split('/')[-1], '->', sys.argv[2])
chk('nombres de nodos', set(A['nodos']) == set(B['nodos']), f"faltan {sorted(set(A['nodos']) - set(B['nodos']))} sobran {sorted(set(B['nodos']) - set(A['nodos']))}" if set(A['nodos']) != set(B['nodos']) else f"{len(A['nodos'])} nodos")
com = set(A['nodos']) & set(B['nodos'])
dp = [n for n in com if A['nodos'][n] != B['nodos'][n]]
chk('jerarquía (padre de cada nodo)', not dp, str(dp[:8]))
chk('orden de articulaciones de la piel', A['pieles'] == B['pieles'], f"{[len(s) for s in A['pieles']]} vs {[len(s) for s in B['pieles']]}")
ca, cb = A['clips'], B['clips']
chk('nombres de clips', list(ca) == list(cb), f"{len(ca)} clips" if list(ca) == list(cb) else f"{sorted(set(ca) ^ set(cb))}")
dd = [(k, ca[k][0], cb[k][0]) for k in ca if k in cb and abs(ca[k][0] - cb[k][0]) > 1e-3]
chk('duración de cada clip', not dd, str(dd[:6]))
dc = [(k, sorted(ca[k][1] ^ cb[k][1])[:4]) for k in ca if k in cb and ca[k][1] != cb[k][1]]
chk('canales animados por clip (hueso y propiedad)', not dc, str(dc[:3]))
chk('materiales (nombre, doble cara, alfa, texturas)', A['materiales'] == B['materiales'], str(B['materiales']) if A['materiales'] != B['materiales'] else str(A['materiales']))
chk('nombres de mallas', list(A['mallas']) == list(B['mallas']), str(list(B['mallas'])))
for mn in A['mallas']:
    if mn not in B['mallas']: continue
    pa, pb = A['mallas'][mn]['prims'], B['mallas'][mn]['prims']
    chk(f'malla {mn}: primitivas y materiales', [p['mat'] for p in pa] == [p['mat'] for p in pb], f"{[p['mat'] for p in pa]} vs {[p['mat'] for p in pb]}")
    chk(f'malla {mn}: atributos', [p['attrs'] for p in pa] == [p['attrs'] for p in pb], str([p['attrs'] for p in pb][:1]))
    chk(f'malla {mn}: formas (MouthOpen)', A['mallas'][mn]['formas'] == B['mallas'][mn]['formas'] and [p['morph'] for p in pa] == [p['morph'] for p in pb], str(B['mallas'][mn]['formas']))
    for k, (x, y) in enumerate(zip(pa, pb)):
        dmn = float(np.abs(np.array(x['mn']) - np.array(y['mn'])).max()); dmx = float(np.abs(np.array(x['mx']) - np.array(y['mx'])).max())
        chk(f'malla {mn} primitiva {k}: caja (escala y orientación)', max(dmn, dmx) < 0.02, f"diferencia máx {max(dmn, dmx) * 1000:.1f} mm | triángulos {x['tris']} -> {y['tris']} | vértices {x['n']} -> {y['n']}")
chk('influencias por vértice', B.get('influencias', {}).get('max_no_cero', 0) <= 4 and not B.get('influencias', {}).get('tiene_WEIGHTS_1'), str(B.get('influencias')))
chk('extensiones glTF', A['ext'] == B['ext'], str(B['ext']))
# transformaciones de reposo: se esperan iguales salvo los huesos del brazo (pivote movido al arranque del brazo)
dt = []
for n in sorted(com):
    ta, tb = A['trs'][n], B['trs'][n]
    dpos = float(np.abs(np.array(ta[0]) - np.array(tb[0])).max()); drot = float(np.abs(np.abs(np.array(ta[1])) - np.abs(np.array(tb[1]))).max()); dsc = float(np.abs(np.array(ta[2]) - np.array(tb[2])).max())
    if max(dpos, drot, dsc) > 1e-4: dt.append((n, round(dpos * 1000, 1), round(drot, 3), round(dsc, 4)))
esper = [d for d in dt if d[0].startswith(('Arm', 'Hand'))]; otros = [d for d in dt if not d[0].startswith(('Arm', 'Hand'))]
print('  reposo de huesos de brazo distinto (esperado: el pivote va al arranque del brazo):', [(d[0], f'{d[1]} mm') for d in esper])
chk('reposo del resto de nodos', not otros, str(otros[:8]))
print('RESULTADO', 'compatible' if bad == 0 else f'{bad} diferencias que revisar')
sys.exit(0 if bad == 0 else 1)
