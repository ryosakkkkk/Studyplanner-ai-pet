# Exposición de caras traseras en reposo desde una dirección (rayos paralelos): qué caras se ven por detrás.
# python3 expo.py -- <blend> <az grados> <elev grados (+ desde arriba)> [paso mm=2] [salida.png]
import bpy, bmesh, sys, math, numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from collections import Counter
argv = sys.argv[sys.argv.index('--') + 1:]
bpy.ops.wm.open_mainfile(filepath=argv[0])
az, el = math.radians(float(argv[1])), math.radians(float(argv[2])); step = float(argv[3]) / 1000 if len(argv) > 3 else 0.002
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data
bm = bmesh.new(); bm.from_mesh(me); bm.faces.ensure_lookup_table(); bm.verts.ensure_lookup_table(); bm.normal_update()
bvh = BVHTree.FromBMesh(bm)
# cámara: al frente (-y) girada az alrededor de z; elevación el
cdir = np.array([math.sin(az) * math.cos(el), -math.cos(az) * math.cos(el), math.sin(el)])     # posición de la cámara (dirección)
d = -cdir
up = np.array([0, 0, 1.0]); right = np.cross(d, up); right /= np.linalg.norm(right); upv = np.cross(right, d)
P = np.array([v.co[:] for v in bm.verts]); c = (P.min(0) + P.max(0)) / 2
u = (P - c) @ right; w = (P - c) @ upv
us = np.arange(u.min(), u.max(), step); ws = np.arange(w.min(), w.max(), step)
back = Counter(); nb = 0; nh = 0
dv = Vector(d)
for uu in us:
    for ww in ws:
        o = c + right * uu + upv * ww - d * 2.0
        hit = bvh.ray_cast(Vector(o), dv, 6.0)
        if hit[0] is None: continue
        nh += 1
        if hit[1].dot(dv) > 0: back[hit[2]] += 1; nb += 1
print('EXPOSICION az', argv[1], 'elev', argv[2], '| rayos con figura', nh, '| caen en cara trasera', nb, f'({100 * nb / max(nh, 1):.2f} %)', '| caras distintas', len(back))
# piezas conexas
lab = np.full(len(bm.verts), -1, int); n = 0
for v0 in bm.verts:
    if lab[v0.index] >= 0: continue
    st = [v0]; lab[v0.index] = n
    while st:
        v = st.pop()
        for e in v.link_edges:
            x = e.other_vert(v)
            if lab[x.index] < 0: lab[x.index] = n; st.append(x)
    n += 1
sizes = np.bincount(lab)
vg = {g.index: g.name for g in ob.vertex_groups}; dl = bm.verts.layers.deform.active
# agrupa las caras expuestas por vecindad (comparten vértice) y lista los grupos mayores
faces = list(back.keys()); fs = set(faces); seen = set(); groups = []
v2f = {}
for fi in faces:
    for v in bm.faces[fi].verts: v2f.setdefault(v.index, []).append(fi)
for f0 in faces:
    if f0 in seen: continue
    st = [f0]; seen.add(f0); g = []
    while st:
        f = st.pop(); g.append(f)
        for v in bm.faces[f].verts:
            for f2 in v2f[v.index]:
                if f2 not in seen: seen.add(f2); st.append(f2)
    groups.append(g)
groups.sort(key=lambda g: -sum(back[f] for f in g))
for g in groups[:14]:
    cen = np.mean([bm.faces[f].calc_center_median()[:] for f in g], 0); nrm = np.mean([bm.faces[f].normal[:] for f in g], 0)
    comp = Counter(int(lab[bm.faces[f].verts[0].index]) for f in g).most_common(1)[0][0]
    wts = Counter()
    for f in g:
        for v in bm.faces[f].verts:
            for gi, wt in v[dl].items(): wts[vg[gi]] += wt
    tot = sum(wts.values()) or 1
    nbe = sum(1 for f in g for e in bm.faces[f].edges if len(e.link_faces) == 1)
    print(f'  rayos {sum(back[f] for f in g):5d} caras {len(g):4d} | centro {np.round(cen, 3)} normal {np.round(nrm, 2)} | pieza {comp} ({sizes[comp]} vért.) | aristas de borde {nbe} |', {a: round(b / tot, 2) for a, b in wts.most_common(3)})
