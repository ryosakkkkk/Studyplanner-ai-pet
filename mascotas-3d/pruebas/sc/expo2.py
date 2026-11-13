# Exposición de caras traseras en una POSE (clip y cuadro) desde una dirección: qué caras se ven por detrás.
# python3 expo2.py -- <anim.blend> <clip> <cuadro> <az> <elev> [paso mm=2]
import bpy, bmesh, sys, math, numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from collections import Counter
argv = sys.argv[sys.argv.index('--') + 1:]
bpy.ops.wm.open_mainfile(filepath=argv[0])
clip, fr = argv[1], int(argv[2]); az, el = math.radians(float(argv[3])), math.radians(float(argv[4])); step = float(argv[5]) / 1000 if len(argv) > 5 else 0.002
sc = bpy.context.scene
armob = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
ob = [o for o in bpy.data.objects if o.type == 'MESH' and o.data.shape_keys][0]
me = ob.data
for t in armob.animation_data.nla_tracks: t.mute = (t.name != clip)
if me.shape_keys.animation_data:
    for t in me.shape_keys.animation_data.nla_tracks: t.mute = True
sc.frame_set(fr)
dg = bpy.context.evaluated_depsgraph_get(); m2 = ob.evaluated_get(dg).to_mesh()
P = np.empty(len(m2.vertices) * 3); m2.vertices.foreach_get('co', P); P = P.reshape(-1, 3)
P0 = np.empty(len(me.vertices) * 3); me.vertices.foreach_get('co', P0); P0 = P0.reshape(-1, 3)
F = [tuple(p.vertices) for p in me.polygons]
bvh = BVHTree.FromPolygons([tuple(p) for p in P], F)
cdir = np.array([math.sin(az) * math.cos(el), -math.cos(az) * math.cos(el), math.sin(el)]); d = -cdir
up = np.array([0, 0, 1.0]); right = np.cross(d, up); right /= np.linalg.norm(right); upv = np.cross(right, d)
c = (P.min(0) + P.max(0)) / 2
u = (P - c) @ right; w = (P - c) @ upv
back = Counter(); nh = 0; dv = Vector(d)
for uu in np.arange(u.min(), u.max(), step):
    for ww in np.arange(w.min(), w.max(), step):
        hit = bvh.ray_cast(Vector(c + right * uu + upv * ww - d * 2.0), dv, 6.0)
        if hit[0] is None: continue
        nh += 1
        if hit[1].dot(dv) > 0: back[hit[2]] += 1
print('EXPOSICION', clip, 'cuadro', fr, 'az', argv[3], 'elev', argv[4], '| rayos con figura', nh, '| en cara trasera', sum(back.values()), '| caras', len(back))
bm = bmesh.new(); bm.from_mesh(me); bm.faces.ensure_lookup_table(); bm.verts.ensure_lookup_table()
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
faces = list(back.keys()); seen = set(); groups = []; v2f = {}
for fi in faces:
    for v in F[fi]: v2f.setdefault(v, []).append(fi)
for f0 in faces:
    if f0 in seen: continue
    st = [f0]; seen.add(f0); g = []
    while st:
        f = st.pop(); g.append(f)
        for v in F[f]:
            for f2 in v2f[v]:
                if f2 not in seen: seen.add(f2); st.append(f2)
    groups.append(g)
groups.sort(key=lambda g: -sum(back[f] for f in g))
for g in groups[:12]:
    vs = np.unique([v for f in g for v in F[f]])
    cen0 = P0[vs].mean(0); cen = P[vs].mean(0)
    comp = Counter(int(lab[F[f][0]]) for f in g).most_common(1)[0][0]
    wts = Counter()
    for v in vs:
        for gi, wt in bm.verts[int(v)][dl].items(): wts[vg[gi]] += wt
    tot = sum(wts.values()) or 1
    nbe = sum(1 for f in g for e in bm.faces[f].edges if len(e.link_faces) == 1)
    print(f'  rayos {sum(back[f] for f in g):5d} caras {len(g):4d} | reposo {np.round(cen0, 3)} pose {np.round(cen, 3)} | pieza {comp} ({sizes[comp]} vért.) | bordes {nbe} |', {a: round(b / tot, 2) for a, b in wts.most_common(4)})
