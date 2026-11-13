import bpy, sys, numpy as np
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.scene.render.fps = 30
bpy.ops.import_scene.gltf(filepath=sys.argv[-1])
sc = bpy.context.scene
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
meshes = [o for o in bpy.data.objects if o.type == 'MESH' and any(m.type == 'ARMATURE' for m in o.modifiers)]
print('mallas', [(o.name, len(o.data.vertices)) for o in meshes])
ob = max(meshes, key=lambda o: len(o.data.vertices)); me = ob.data
print('acciones', [(a.name, tuple(a.frame_range), len(getattr(a, 'slots', []))) for a in bpy.data.actions][:8])
print('nla', [(t.name, t.mute) for t in arm.animation_data.nla_tracks][:5], 'accion activa', arm.animation_data.action)
P0 = np.empty(len(me.vertices) * 3); me.vertices.foreach_get('co', P0); P0 = P0.reshape(-1, 3)
def evalP():
    dg = bpy.context.evaluated_depsgraph_get(); m2 = ob.evaluated_get(dg).to_mesh()
    p = np.empty(len(m2.vertices) * 3); m2.vertices.foreach_get('co', p); return p.reshape(-1, 3)
for t in arm.animation_data.nla_tracks: t.mute = True
arm.animation_data.action = None
sc.frame_set(0); print('sin acción: desplazamiento máx mm', np.linalg.norm(evalP() - P0, axis=1).max() * 1000)
act = bpy.data.actions['idle']; arm.animation_data.action = act
try:
    arm.animation_data.action_slot = act.slots[0]; print('slot ok', act.slots[0].name_display if hasattr(act.slots[0], 'name_display') else act.slots[0])
except Exception as e: print('slot error', e)
for fr in (0, 1, 30, 60, 120):
    sc.frame_set(fr); P = evalP(); print('idle cuadro', fr, 'desplazamiento máx mm', round(np.linalg.norm(P - P0, axis=1).max() * 1000, 1), 'media', round(np.linalg.norm(P - P0, axis=1).mean() * 1000, 2))
E = np.array([e.vertices[:] for e in me.edges]); L0 = np.linalg.norm(P0[E[:, 0]] - P0[E[:, 1]], axis=1)
sc.frame_set(30); P = evalP(); L = np.linalg.norm(P[E[:, 0]] - P[E[:, 1]], axis=1); i = int(np.argmax(L - L0))
print('arista que más crece en idle 30:', (L - L0)[i] * 1000, 'mm; reposo', L0[i] * 1000, 'vértices', E[i], P0[E[i]])
vg = {g.index: g.name for g in ob.vertex_groups}
for v in E[i]: print('  pesos', [(vg[g.group], round(g.weight, 2)) for g in me.vertices[int(v)].groups])
