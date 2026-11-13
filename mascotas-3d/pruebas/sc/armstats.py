import bpy, sys, numpy as np
src = sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
me = ob.data
col = np.zeros(len(me.vertices) * 4); me.color_attributes['dbgcol'].data.foreach_get('color', col); col = col.reshape(-1, 4)[:, :3]
pal = np.array([[0.85, 0.85, 0.85], [1, 0.2, 0.2], [0.2, 0.4, 1], [1, 1, 0.2], [1, 0.6, 0.1], [0.1, 0.9, 0.2], [0.1, 0.9, 0.9], [1, 0, 1], [0.6, 0.2, 0.9], [0.5, 0.1, 0.1], [0.1, 0.2, 0.5]])
part = np.argmin(np.linalg.norm(col[:, None, :] - pal[None], axis=2), axis=1)
P = np.array([(ob.matrix_world @ v.co)[:] for v in me.vertices])
B = {b.name: (np.array((arm.matrix_world @ b.head_local)[:]), np.array((arm.matrix_world @ b.tail_local)[:])) for b in arm.data.bones}
print('HUESOS', {k: (v[0].round(3).tolist(), v[1].round(3).tolist()) for k, v in B.items() if k.startswith(('Arm', 'Hand', 'Chest', 'Head'))})
for si, s in enumerate('LR'):
    Q = P[part == 1 + si]
    j = B['Arm' + s][0]; e = B['Hand' + s][0]; h = B['Hand' + s][1]
    far = Q[np.argmax(np.linalg.norm(Q - j, axis=1))]
    u = far - j; L = np.linalg.norm(u); u /= L
    d = (Q - j) @ u; rho = np.linalg.norm((Q - j) - np.outer(d, u), axis=1)
    print(f'BRAZO {s}: hombro {j.round(3)} codo {e.round(3)} mano {h.round(3)} punta {far.round(3)} L={L:.3f} eje={u.round(2)}')
    print(f'   d (a lo largo del eje, m): min {d.min():.3f} p5 {np.percentile(d,5):.3f} med {np.median(d):.3f} max {d.max():.3f} | radio: med {np.median(rho):.3f} p95 {np.percentile(rho,95):.3f} max {rho.max():.3f}')
    hist, edges = np.histogram(d, bins=8, range=(d.min(), d.max()))
    print('   vértices por tramo de d:', hist.tolist(), '| ancho medio del radio por tramo:', [round(float(np.median(rho[(d >= edges[k]) & (d <= edges[k+1])])), 3) if hist[k] else 0 for k in range(8)])
