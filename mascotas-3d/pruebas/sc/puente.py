# aristas que unen mano/brazo con el cuerpo (puentes) en un *_rig4.blend:  python3 puente.py -- <pet>
import bpy, sys, numpy as np
pet = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.open_mainfile(filepath=f'/home/claude/w/rig4/out/{pet}_rig4.blend')
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices)); me = ob.data
nv = len(me.vertices); vg = {g.index: g.name for g in ob.vertex_groups}
W = {}
for v in me.vertices:
    for g in v.groups: W.setdefault(vg[g.group], np.zeros(nv))[v.index] = g.weight
z = np.zeros(nv)
P = np.array([v.co[:] for v in me.vertices])
E = np.array([e.vertices[:] for e in me.edges]); L = np.linalg.norm(P[E[:, 0]] - P[E[:, 1]], axis=1)
for s in 'LR':
    a = W.get('Arm' + s, z) + W.get('Hand' + s, z); h = W.get('Hand' + s, z)
    da = np.abs(a[E[:, 0]] - a[E[:, 1]])
    br = np.nonzero(da > 0.6)[0]
    print(pet, s, 'aristas con salto de peso de brazo > 0.6:', len(br), '| largo mm: mediana', round(float(np.median(L[br])) * 1000, 1) if len(br) else 0, 'máx', round(float(L[br].max()) * 1000, 1) if len(br) else 0)
    hb = [i for i in br if max(h[E[i, 0]], h[E[i, 1]]) > 0.5]
    print('   de ellas tocan la mano:', len(hb))
    for i in sorted(hb, key=lambda i: -L[i])[:8]:
        u, v = E[i]; print('    ', np.round(P[u], 3), '->', np.round(P[v], 3), 'largo', round(float(L[i]) * 1000, 1), 'brazo', round(float(a[u]), 2), round(float(a[v]), 2))
    # caras que tienen vértices de mano (>0.9) y de cuerpo (<0.1)
    nf = 0; big = []
    for p in me.polygons:
        vs = list(p.vertices); aa = a[vs]
        if aa.max() > 0.9 and aa.min() < 0.1: nf += 1; big.append((p.area, p.index, p.material_index))
    print('   caras que unen brazo entero con cuerpo:', nf, '| área total cm2', round(sum(b[0] for b in big) * 1e4, 2))
attrs = [a.name for a in me.attributes if not a.name.startswith('.')]
print('atributos', attrs)
