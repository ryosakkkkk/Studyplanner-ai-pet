# Bordes abiertos de la malla principal de un GLB: python3 bordes.py -- <glb> [json de salida]
import bpy, bmesh, sys, json, numpy as np
argv = sys.argv[sys.argv.index('--') + 1:]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=argv[0])
obs = [o for o in bpy.data.objects if o.type == 'MESH']
ob = max(obs, key=lambda o: len(o.data.vertices))
me = ob.data
bm = bmesh.new(); bm.from_mesh(me)
n0 = len(bm.verts)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
bm.verts.ensure_lookup_table(); bm.edges.ensure_lookup_table(); bm.faces.ensure_lookup_table()
bnd = [e for e in bm.edges if len(e.link_faces) == 1]
nonman = [e for e in bm.edges if len(e.link_faces) > 2]
# lazos
adj = {}
for e in bnd:
    a, b = e.verts[0].index, e.verts[1].index
    adj.setdefault(a, []).append(b); adj.setdefault(b, []).append(a)
seen = set(); loops = []
for v0 in adj:
    if v0 in seen: continue
    st = [v0]; comp = []
    seen.add(v0)
    while st:
        v = st.pop(); comp.append(v)
        for w in adj[v]:
            if w not in seen: seen.add(w); st.append(w)
    loops.append(comp)
M = ob.matrix_world
out = []
for comp in loops:
    P = np.array([(M @ bm.verts[i].co)[:] for i in comp])
    ln = 0.0
    cs = set(comp)
    for e in bnd:
        if e.verts[0].index in cs: ln += e.calc_length()
    out.append(dict(n=len(comp), largo=round(ln, 4), c=[round(float(v), 3) for v in P.mean(0)], mn=[round(float(v), 3) for v in P.min(0)], mx=[round(float(v), 3) for v in P.max(0)]))
out.sort(key=lambda d: -d['largo'])
# componentes conexas
seenf = set(); ncomp = 0; sizes = []
for f in bm.faces:
    if f.index in seenf: continue
    ncomp += 1; st = [f]; seenf.add(f.index); k = 0
    while st:
        g = st.pop(); k += 1
        for e in g.edges:
            for h in e.link_faces:
                if h.index not in seenf: seenf.add(h.index); st.append(h)
    sizes.append(k)
sizes.sort(reverse=True)
print('MALLA', ob.name, 'verts', n0, '->', len(bm.verts), 'caras', len(bm.faces), 'piezas', ncomp, sizes[:12])
print('BORDES abiertos', len(bnd), 'aristas en', len(loops), 'lazos; largo total', round(sum(d['largo'] for d in out), 3), 'm; no variedad', len(nonman))
for d in out[:25]: print('  lazo', d)
if len(argv) > 1: json.dump(out, open(argv[1], 'w'))
