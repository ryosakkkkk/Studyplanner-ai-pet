# ¿Hay grupos de caras al revés (mirando hacia adentro)? Grupos de orientación coherente y voto por rayos.
# python3 invert.py -- <glb de origen>
import bpy, bmesh, sys, numpy as np
sys.path.insert(0, '/home/claude/w/rig4')
import zip23
from mathutils.bvhtree import BVHTree
argv = sys.argv[sys.argv.index('--') + 1:]
bpy.ops.wm.read_factory_settings(use_empty=True); bpy.ops.import_scene.gltf(filepath=argv[0])
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
bm = bmesh.new(); bm.from_mesh(ob.data)
bmesh.ops.transform(bm, matrix=ob.matrix_world, verts=bm.verts)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
zip23.zip_seams(bm, 0.0005, 0.0005, log=lambda *a: None); zip23.zip_seams(bm, 0.0012, 0.0012, log=lambda *a: None)
bm.normal_update(); zip23.remove_fins(bm, log=lambda *a: None); zip23.fill_small_holes(bm, log=lambda *a: None)
bm.faces.ensure_lookup_table(); bm.normal_update()
bvh = BVHTree.FromBMesh(bm)
grp = np.full(len(bm.faces), -1, int); ng = 0
for f0 in bm.faces:
    if grp[f0.index] >= 0: continue
    st = [f0]; grp[f0.index] = ng
    while st:
        f = st.pop()
        for l in f.loops:
            e = l.edge
            if len(e.link_faces) != 2: continue
            l2 = [x for x in e.link_loops if x is not l][0]
            if l2.vert is l.vert: continue
            g = l2.face
            if grp[g.index] < 0: grp[g.index] = ng; st.append(g)
    ng += 1
def vote(f):
    n = f.normal
    if n.length < 0.5: return 0
    c = f.calc_center_median()
    hf = bvh.ray_cast(c + n * 0.0008, n, 3.0); hb = bvh.ray_cast(c - n * 0.0008, -n, 3.0)
    front_in = hf[0] is not None and hf[1].dot(n) > 0          # por delante choca con el interior de otra cara
    back_out = hb[0] is None or hb[1].dot(-n) < 0              # por detrás sale al aire o choca con un frente
    front_out = hf[0] is None or hf[1].dot(n) < 0
    back_in = hb[0] is not None and hb[1].dot(-n) > 0
    if front_in and back_out: return -1
    if front_out and back_in: return 1
    return 0
sizes = np.bincount(grp)
print('GRUPOS', ng, sorted(sizes.tolist(), reverse=True)[:8])
for g in np.argsort(-sizes)[:12]:
    fs = [bm.faces[i] for i in np.nonzero(grp == g)[0]]
    samp = fs if len(fs) <= 400 else [fs[i] for i in np.linspace(0, len(fs) - 1, 400).astype(int)]
    v = np.array([vote(f) for f in samp])
    cen = np.mean([f.calc_center_median()[:] for f in samp], 0)
    print(f'  grupo {g}: caras {len(fs)} centro {np.round(cen, 3)} | bien {int((v == 1).sum())} al revés {int((v == -1).sum())} dudosas {int((v == 0).sum())}')
