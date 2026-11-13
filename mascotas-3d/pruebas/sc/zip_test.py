# prueba del cierre de costuras sobre el modelo de origen: python3 zip_test.py -- <pet> <tol_v> <tol_t> [salida.blend]
import bpy, bmesh, sys, numpy as np
sys.path.insert(0, '/home/claude/w/rig4')
import importlib, zip23; importlib.reload(zip23)
argv = sys.argv[sys.argv.index('--') + 1:]
pet = argv[0]; tv = float(argv[1]); tt = float(argv[2])
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=f'/home/claude/w/rig2/src_hq/{pet}.glb')
ob = [o for o in bpy.data.objects if o.type == 'MESH' and o.data.shape_keys and 'MouthOpen' in o.data.shape_keys.key_blocks][0]
me = ob.data
bm = bmesh.new(); bm.from_mesh(me)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.0001)
nf0 = len(bm.faces); nv0 = len(bm.verts)
a0 = sum(f.calc_area() for f in bm.faces)
for (a, b) in [(tv, tt)] + ([(float(argv[3]), float(argv[4]))] if len(argv) > 4 and argv[3][0].isdigit() else []):
    zip23.zip_seams(bm, a, b, tag=pet)
zip23.fill_small_holes(bm, tag=pet)
a1 = sum(f.calc_area() for f in bm.faces)
lab, nc = zip23._components(bm)
print('PIEZAS', nc, sorted(np.bincount(lab).tolist(), reverse=True)[:8])
print('CARAS', nf0, '->', len(bm.faces), 'VERTICES', nv0, '->', len(bm.verts), 'área', round(a0, 4), '->', round(a1, 4))
# lo que queda abierto: lazos más largos
be = [e for e in bm.edges if len(e.link_faces) == 1]
adj = {}
for e in be:
    a, b = e.verts[0].index, e.verts[1].index
    adj.setdefault(a, []).append((b, e.calc_length())); adj.setdefault(b, []).append((a, e.calc_length()))
seen = set(); loops = []
bm.verts.ensure_lookup_table()
for v0 in adj:
    if v0 in seen: continue
    st = [v0]; seen.add(v0); comp = []; ln = 0
    while st:
        v = st.pop(); comp.append(v)
        for w, l in adj[v]:
            ln += l / 2
            if w not in seen: seen.add(w); st.append(w)
    Pc = np.array([bm.verts[i].co[:] for i in comp])
    loops.append((ln, len(comp), np.round(Pc.mean(0), 3).tolist(), np.round(Pc.min(0), 3).tolist(), np.round(Pc.max(0), 3).tolist()))
loops.sort(reverse=True)
print('LAZOS que quedan', len(loops), 'largo', round(sum(l[0] for l in loops), 2))
for l in loops[:14]: print('  ', round(l[0], 3), l[1], l[2], l[3], l[4])
nm = [e for e in bm.edges if len(e.link_faces) > 2]
print('aristas con más de dos caras', len(nm))
out = [a for a in argv if a.endswith('.blend')]
if out:
    bm.to_mesh(me); me.update(); bpy.ops.wm.save_as_mainfile(filepath=out[0])
