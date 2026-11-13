import bpy, sys, numpy as np
src = sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
sc = bpy.context.scene
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
ob = [o for o in bpy.data.objects if o.type == 'MESH' and o.data.shape_keys][0]
def evalP():
    dg = bpy.context.evaluated_depsgraph_get(); m2 = ob.evaluated_get(dg).to_mesh()
    p = np.empty(len(m2.vertices) * 3); m2.vertices.foreach_get('co', p); return p.reshape(-1, 3)
for t in arm.animation_data.nla_tracks:
    if t.name not in ('idle', 'talk', 'talk_body', 'think', 'sleep', 'run', 'walk'): continue
    for u in arm.animation_data.nla_tracks: u.mute = (u.name != t.name)
    st = t.strips[0]; f0, f1 = int(st.frame_start), int(st.frame_end)
    sc.frame_set(f0); a = evalP(); sc.frame_set(f1); b = evalP()
    print('CICLO', t.name, 'fps', sc.render.fps, 'cuadros', f0, f1, 'diferencia mm', round(float(np.linalg.norm(a - b, axis=1).max()) * 1000, 2))
vg = {g.index: g.name for g in ob.vertex_groups}
for u in arm.animation_data.nla_tracks: u.mute = (u.name != 'run')
sc.frame_set(0); a = evalP(); sc.frame_set(18); b = evalP()
d = np.linalg.norm(a - b, axis=1); i = int(np.argmax(d))
print('PEOR', [ (vg[g.group], round(g.weight,2)) for g in ob.data.vertices[i].groups], a[i])
for fr in (0, 1, 17, 18):
    sc.frame_set(fr)
    print('POSE', fr, {pb.name: tuple(round(x, 4) for x in pb.rotation_quaternion) for pb in arm.pose.bones if pb.name in ('Hips', 'ArmL', 'ArmRimL', 'Tail', 'Root')})
