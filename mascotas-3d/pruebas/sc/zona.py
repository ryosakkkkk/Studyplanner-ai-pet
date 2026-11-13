# ¿Qué hay en una zona del modelo? python3 zona.py -- <blend> x0 x1 y0 y1 z0 z1
import bpy, bmesh, sys, numpy as np
argv = sys.argv[sys.argv.index('--') + 1:]
bpy.ops.wm.open_mainfile(filepath=argv[0])
x0, x1, y0, y1, z0, z1 = [float(a) for a in argv[1:7]]
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data
bm = bmesh.new(); bm.from_mesh(me); bm.faces.ensure_lookup_table(); bm.verts.ensure_lookup_table(); bm.normal_update()
# piezas conexas
lab = np.full(len(bm.verts), -1, int); n = 0
for v0 in bm.verts:
    if lab[v0.index] >= 0: continue
    st = [v0]; lab[v0.index] = n
    while st:
        v = st.pop()
        for e in v.link_edges:
            w = e.other_vert(v)
            if lab[w.index] < 0: lab[w.index] = n; st.append(w)
    n += 1
sizes = np.bincount(lab)
vg = {g.index: g.name for g in ob.vertex_groups}
dl = bm.verts.layers.deform.active
sel = [f for f in bm.faces if x0 <= f.calc_center_median().x <= x1 and y0 <= f.calc_center_median().y <= y1 and z0 <= f.calc_center_median().z <= z1]
print('ZONA caras', len(sel), 'de', len(bm.faces), '| piezas conexas', n)
from collections import Counter
cc = Counter(int(lab[f.verts[0].index]) for f in sel)
for c, k in cc.most_common(8):
    fs = [f for f in sel if lab[f.verts[0].index] == c]
    nrm = np.mean([f.normal[:] for f in fs], 0); cen = np.mean([f.calc_center_median()[:] for f in fs], 0)
    nb = sum(1 for f in fs for e in f.edges if len(e.link_faces) == 1)
    w = Counter()
    for f in fs:
        for v in f.verts:
            for gi, wt in v[dl].items(): w[vg[gi]] += wt
    tot = sum(w.values())
    print(f'  pieza {c} (vértices {sizes[c]}): caras {k} | centro {np.round(cen, 3)} | normal media {np.round(nrm, 2)} | aristas de borde {nb} | pesos', {a: round(b / tot, 2) for a, b in w.most_common(4)})
