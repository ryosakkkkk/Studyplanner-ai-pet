# Coherencia de orientación de caras: aristas compartidas por dos caras que las recorren en el MISMO sentido (una está al revés).
# python3 orient.py -- <glb|blend> [zip]
import bpy, bmesh, sys, numpy as np
sys.path.insert(0, '/home/claude/w/rig4')
argv = sys.argv[sys.argv.index('--') + 1:]
if argv[0].endswith('.blend'): bpy.ops.wm.open_mainfile(filepath=argv[0])
else:
    bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.gltf(filepath=argv[0])
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
bm = bmesh.new(); bm.from_mesh(ob.data)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4 if not argv[0].endswith('.blend') else 1e-6)
if len(argv) > 1 and argv[1] == 'zip':
    import zip23
    zip23.zip_seams(bm, 0.0005, 0.0005); zip23.zip_seams(bm, 0.0012, 0.0012); zip23.fill_small_holes(bm)
bm.faces.ensure_lookup_table(); bm.edges.ensure_lookup_table()
bad = 0; badlen = 0; badfaces = set()
for e in bm.edges:
    if len(e.link_faces) != 2: continue
    l1, l2 = e.link_loops
    if l1.vert is l2.vert:
        bad += 1; badlen += e.calc_length(); badfaces.add(l1.face.index); badfaces.add(l2.face.index)
print('ORIENTACION', argv[0].split('/')[-1], 'caras', len(bm.faces), '| aristas incoherentes', bad, 'largo m', round(badlen, 3), '| caras tocadas', len(badfaces))
# grupos de caras coherentes: componentes del grafo de caras unidas por aristas coherentes
import collections
seen = np.zeros(len(bm.faces), bool); sizes = []
for f0 in bm.faces:
    if seen[f0.index]: continue
    st = [f0]; seen[f0.index] = True; k = 0
    while st:
        f = st.pop(); k += 1
        for l in f.loops:
            e = l.edge
            if len(e.link_faces) != 2: continue
            l2 = [x for x in e.link_loops if x is not l][0]
            if l2.vert is l.vert: continue
            g = l2.face
            if not seen[g.index]: seen[g.index] = True; st.append(g)
    sizes.append(k)
sizes.sort(reverse=True)
print('  grupos de orientación coherente', len(sizes), sizes[:10], '... chicos (<=3 caras):', sum(1 for s in sizes if s <= 3))
