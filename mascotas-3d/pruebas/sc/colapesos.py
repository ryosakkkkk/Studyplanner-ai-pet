import bpy, sys, numpy as np
bpy.ops.wm.open_mainfile(filepath=sys.argv[-1])
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices)); me = ob.data
vg = {g.index: g.name for g in ob.vertex_groups}
P = np.array([v.co[:] for v in me.vertices]); T = np.zeros(len(P))
for v in me.vertices:
    for g in v.groups:
        if vg[g.group].startswith('Tail'): T[v.index] += g.weight
m = T > 0.05
print('vértices con peso de cola', int(m.sum()), '| caja x', P[m, 0].min().round(3), P[m, 0].max().round(3), 'y', P[m, 1].min().round(3), P[m, 1].max().round(3), 'z', P[m, 2].min().round(3), P[m, 2].max().round(3))
sus = m & (P[:, 1] < 0.12)
print('  con y < 0.12:', int(sus.sum()), P[sus][:8].round(3).tolist())
