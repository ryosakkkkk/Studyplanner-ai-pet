# Mide las costuras abiertas de un GLB: para cada vértice de borde, distancia al borde abierto más cercano que no lo toca.
# python3 costuras.py -- <glb> [dist de unión, por defecto 1e-6]
import bpy, bmesh, sys, numpy as np
from mathutils.bvhtree import BVHTree
from scipy.spatial import cKDTree
argv = sys.argv[sys.argv.index('--') + 1:]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=argv[0])
obs = [o for o in bpy.data.objects if o.type == 'MESH']
ob = max(obs, key=lambda o: len(o.data.vertices))
bm = bmesh.new(); bm.from_mesh(ob.data)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=float(argv[1]) if len(argv) > 1 else 1e-6)
bm.verts.ensure_lookup_table(); bm.edges.ensure_lookup_table()
bnd = [e for e in bm.edges if len(e.link_faces) == 1]
bv = sorted({v.index for e in bnd for v in e.verts})
P = np.array([bm.verts[i].co[:] for i in bv]); idx = {v: k for k, v in enumerate(bv)}
E = np.array([[idx[e.verts[0].index], idx[e.verts[1].index]] for e in bnd])
A = P[E[:, 0]]; B = P[E[:, 1]]
mid = (A + B) / 2; hl = np.linalg.norm(B - A, axis=1) / 2
tree = cKDTree(mid)
dmin = np.full(len(P), np.inf); kind = np.zeros(len(P), int)   # 1 = cae dentro de la arista (unión en T), 2 = cae en un extremo
inc = [[] for _ in P]
for k, (a, b) in enumerate(E): inc[a].append(k); inc[b].append(k)
R = float(hl.max()) + 0.01
for i, p in enumerate(P):
    cand = tree.query_ball_point(p, R)
    for k in cand:
        if k in inc[i]: continue
        a, b = A[k], B[k]; ab = b - a; t = float(np.dot(p - a, ab) / max(np.dot(ab, ab), 1e-18)); tc = min(max(t, 0), 1)
        d = float(np.linalg.norm(p - (a + tc * ab)))
        if d < dmin[i]: dmin[i] = d; kind[i] = 1 if 0.02 < t < 0.98 else 2
elen = np.linalg.norm(B - A, axis=1)
print('COSTURAS', argv[0].split('/')[-1], 'aristas de borde', len(bnd), 'vértices de borde', len(P), 'largo m', round(float(elen.sum()), 2))
for thr in (1e-6, 1e-5, 1e-4, 3e-4, 1e-3, 2e-3, 5e-3, 1e-2):
    print('  a menos de', thr, 'm:', int((dmin < thr).sum()), f'({100 * (dmin < thr).mean():.1f} %)', '| en T', int(((dmin < thr) & (kind == 1)).sum()), 'en extremo', int(((dmin < thr) & (kind == 2)).sum()))
print('  mediana mm', round(float(np.median(dmin[np.isfinite(dmin)])) * 1000, 4), 'p90 mm', round(float(np.quantile(dmin[np.isfinite(dmin)], 0.9)) * 1000, 3), 'sin vecino', int((~np.isfinite(dmin)).sum()))
