import bpy, sys, numpy as np
argv = sys.argv[sys.argv.index('--') + 1:]
bpy.ops.wm.open_mainfile(filepath=argv[0]); c = np.array([float(a) for a in argv[1:4]]); R = float(argv[4])
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices)); me = ob.data
vg = {g.index: g.name for g in ob.vertex_groups}
part = None
if 'dbgcol' in me.color_attributes:
    col = np.zeros(len(me.vertices) * 4); me.color_attributes['dbgcol'].data.foreach_get('color', col); col = col.reshape(-1, 4)[:, :3]
    pal = np.array([[0.85, 0.85, 0.85], [1, 0.2, 0.2], [0.2, 0.4, 1], [1, 1, 0.2], [1, 0.6, 0.1], [0.1, 0.9, 0.2], [0.1, 0.9, 0.9], [1, 0, 1], [0.6, 0.2, 0.9], [0.5, 0.1, 0.1], [0.1, 0.2, 0.5]])
    part = np.argmin(np.linalg.norm(col[:, None, :] - pal[None], axis=2), axis=1)
rows = []
for v in me.vertices:
    p = np.array(v.co[:])
    if np.linalg.norm(p - c) < R:
        rows.append((np.linalg.norm(p - c), v.index, p.round(3).tolist(), int(part[v.index]) if part is not None else -1, sorted(((vg[g.group], round(g.weight, 2)) for g in v.groups if g.weight > 0.02), key=lambda kv: -kv[1])))
for r in sorted(rows)[:40]: print(f'{r[0]*1000:5.1f} mm v{r[1]} {r[2]} PART {r[3]} {r[4]}')
