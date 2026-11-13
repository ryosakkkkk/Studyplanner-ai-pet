# Saltos: para cada clip, el mayor desplazamiento entre cuadros consecutivos, en qué cuadro y con qué hueso
import bpy, sys, json, numpy as np
glb, clips = sys.argv[-2], sys.argv[-1].split(',')
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data; nv = len(me.vertices)
vg = {g.index: g.name for g in ob.vertex_groups}
dom = ['?'] * nv
for v in me.vertices:
    if v.groups: dom[v.index] = vg[max(v.groups, key=lambda g: g.weight).group]
sc = bpy.context.scene; arm.animation_data_create()
def evalP():
    dg = bpy.context.evaluated_depsgraph_get(); m2 = ob.evaluated_get(dg).to_mesh()
    p = np.empty(len(m2.vertices) * 3); m2.vertices.foreach_get('co', p); return p.reshape(-1, 3)
for t in arm.animation_data.nla_tracks: t.mute = True
for nm in clips:
    act = bpy.data.actions.get(nm); arm.animation_data.action = act
    try: arm.animation_data.action_slot = act.slots[0]
    except Exception: pass
    f0, f1 = int(act.frame_range[0]), int(act.frame_range[1])
    prev = None; best = (0, 0, 0)
    steps = []
    for fr in range(f0, f1 + 1):
        sc.frame_set(fr); P = evalP()
        if prev is not None:
            d = np.linalg.norm(P - prev, axis=1); i = int(np.argmax(d)); steps.append(float(d.max()))
            if d[i] > best[0]: best = (float(d[i]), fr, i)
        prev = P
    st = np.array(steps)
    # mayor salto relativo: paso / mediana de los pasos vecinos (aceleración brusca)
    acc = np.abs(np.diff(st)) if len(st) > 2 else np.zeros(1)
    ja = int(np.argmax(acc)) if len(acc) else 0
    print(nm, 'frames', f1 - f0 + 1, 'paso max mm', round(best[0] * 1000, 1), 'cuadro', best[1], 'hueso', dom[best[2]], 'z', round(float(prev[best[2], 2]), 2),
          '| cambio brusco mm', round(float(acc.max()) * 1000, 1) if len(acc) else 0, 'en cuadro', f0 + ja + 1)
