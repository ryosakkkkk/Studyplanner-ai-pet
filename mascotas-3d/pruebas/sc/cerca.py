# Caras cerca de un punto, con su pieza (PART), normal y pesos: cerca.py -- <dbg.blend> x y z radio
import bpy, bmesh, sys, numpy as np
argv = sys.argv[sys.argv.index('--') + 1:]
bpy.ops.wm.open_mainfile(filepath=argv[0])
c = np.array([float(a) for a in argv[1:4]]); R = float(argv[4])
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data
col = np.zeros(len(me.vertices) * 4); me.color_attributes['dbgcol'].data.foreach_get('color', col); col = col.reshape(-1, 4)[:, :3]
pal = np.array([[0.85, 0.85, 0.85], [1, 0.2, 0.2], [0.2, 0.4, 1], [1, 1, 0.2], [1, 0.6, 0.1], [0.1, 0.9, 0.2], [0.1, 0.9, 0.9], [1, 0, 1], [0.6, 0.2, 0.9], [0.5, 0.1, 0.1], [0.1, 0.2, 0.5]])
part = np.argmin(np.linalg.norm(col[:, None, :] - pal[None], axis=2), axis=1)
bm = bmesh.new(); bm.from_mesh(me); bm.faces.ensure_lookup_table(); bm.normal_update()
from collections import defaultdict
rows = defaultdict(list)
for f in bm.faces:
    cc = np.array(f.calc_center_median()[:])
    if np.linalg.norm(cc - c) < R:
        pt = int(np.bincount([part[v.index] for v in f.verts]).argmax())
        rows[pt].append((cc, np.array(f.normal[:]), f.calc_area(), sum(1 for e in f.edges if len(e.link_faces) == 1)))
for pt, L in sorted(rows.items()):
    C = np.array([l[0] for l in L]); N = np.array([l[1] for l in L]); A = np.array([l[2] for l in L])
    print(f'PART {pt}: caras {len(L)} área cm2 {A.sum() * 1e4:.1f} | centro {np.round((C * A[:, None]).sum(0) / A.sum(), 3)} | normal media {np.round((N * A[:, None]).sum(0) / A.sum(), 2)} | z {C[:, 2].min():.3f}-{C[:, 2].max():.3f} | bordes {sum(l[3] for l in L)}')
    # reparto de normales: arriba/abajo/afuera
    up = (N[:, 2] > 0.5); dn = (N[:, 2] < -0.5)
    print(f'    mirando arriba {int(up.sum())} abajo {int(dn.sum())} | hacia -x {(N[:, 0] < -0.5).sum()} hacia +x {(N[:, 0] > 0.5).sum()}')
