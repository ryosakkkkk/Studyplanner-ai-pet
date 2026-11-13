import bpy, bmesh, sys, numpy as np
sys.path.insert(0, '/home/claude/w/rig4')
import zip23
pet = sys.argv[-1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=f'/home/claude/w/rig2/src_hq/{pet}.glb')
ob = [o for o in bpy.data.objects if o.type == 'MESH' and o.data.shape_keys and 'MouthOpen' in o.data.shape_keys.key_blocks][0]
bm = bmesh.new(); bm.from_mesh(ob.data)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.0001)
zip23.zip_seams(bm, 0.0005, 0.0005, tag=pet); zip23.zip_seams(bm, 0.0012, 0.0012, tag=pet)
be = [e for e in bm.edges if len(e.link_faces) == 1]
nbr = {}
for e in be:
    for v in e.verts: nbr.setdefault(v, []).append(e)
# grupos de aristas de borde conectadas
seen = set(); groups = []
for e0 in be:
    if e0 in seen: continue
    st = [e0]; seen.add(e0); g = []
    while st:
        e = st.pop(); g.append(e)
        for v in e.verts:
            for e2 in nbr[v]:
                if e2 not in seen: seen.add(e2); st.append(e2)
    groups.append(g)
from collections import Counter
kinds = Counter()
for g in groups:
    vs = {v for e in g for v in e.verts}
    val = Counter(len(nbr[v]) for v in vs)
    per = sum(e.calc_length() for e in g)
    simple = all(len(nbr[v]) == 2 for v in vs)
    nm = sum(1 for v in vs for e in v.link_edges if len(e.link_faces) > 2)
    if per < 0.06:
        # ¿las aristas pertenecen a la misma cara?
        faces = Counter(e.link_faces[0].index for e in g)
        kinds[(len(g), len(vs), simple, max(faces.values()), nm > 0)] += 1
for k, c in sorted(kinds.items(), key=lambda kv: -kv[1])[:20]: print('aristas, vértices, simple, aristas de una misma cara, toca no-variedad:', k, '->', c)
