# ¿Dónde se estira? Para unas animaciones, las 15 aristas más estiradas (posición en reposo, huesos dominantes).
import bpy, sys, numpy as np
glb, clips, MODE = sys.argv[-3], sys.argv[-2].split(','), sys.argv[-1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data; vg = {g.index: g.name for g in ob.vertex_groups}
def dom(i):
    gs = sorted(((g.weight, vg[g.group]) for g in me.vertices[i].groups), reverse=True)[:2]
    return '/'.join(f'{n}{w:.1f}' for w, n in gs)
P0 = np.array([v.co[:] for v in me.vertices]); E = np.array([e.vertices[:] for e in me.edges])
L0 = np.linalg.norm(P0[E[:, 0]] - P0[E[:, 1]], axis=1)
sc = bpy.context.scene; arm.animation_data_create()
for nm in clips:
    act = bpy.data.actions[nm]; arm.animation_data.action = act
    try: arm.animation_data.action_slot = act.slots[0]
    except Exception: pass
    best = None
    for fr in range(int(act.frame_range[0]), int(act.frame_range[1]) + 1, 3):
        sc.frame_set(fr); dg = bpy.context.evaluated_depsgraph_get(); m2 = ob.evaluated_get(dg).to_mesh()
        P = np.array([v.co[:] for v in m2.vertices]); Lc = np.linalg.norm(P[E[:, 0]] - P[E[:, 1]], axis=1)
        r = (Lc - L0) * 1000 if MODE == 'mm' else Lc / np.maximum(L0, 1e-6)
        r[L0 < 1e-4] = 0
        if best is None or r.max() > best[0]: best = (r.max(), fr, r.copy())
    r = best[2]; idx = np.argsort(-r)[:12]
    print('==', nm, 'cuadro', best[1], 'max', round(best[0], 1))
    for i in idx:
        a, b = E[i]
        print(f'  r={r[i]:5.1f} L0={L0[i]*1000:5.2f}mm  pos={np.round(P0[a],3)}  {dom(a)} | {dom(b)}')
