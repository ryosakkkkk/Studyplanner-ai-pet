import bpy, sys, json
out = {}
for pet in ('zorro', 'gato', 'panda', 'buho', 'pinguino', 'conejo'):
    bpy.ops.wm.open_mainfile(filepath=f'/mnt/user-data/outputs/v0222/blender/{pet}_anim.blend')
    arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
    d = {}
    for b in arm.data.bones:
        h = arm.matrix_world @ b.head_local; t = arm.matrix_world @ b.tail_local
        d[b.name] = [[round(x, 6) for x in h], [round(x, 6) for x in t], round(b.matrix_local.to_euler()[0], 6)]
    out[pet] = d
json.dump(out, open('/home/claude/w/rig4/huesos_v0222.json', 'w'), indent=0)
for pet, d in out.items(): print(pet, {k: v[0] for k, v in d.items() if k.startswith('Tail')}, len(d))
