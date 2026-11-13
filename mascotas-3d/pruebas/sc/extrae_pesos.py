# Extrae de los .blend de la 0.22.2 las posiciones y los pesos por hueso (referencia para conservar el movimiento del cuerpo).
import bpy, sys, numpy as np
for pet in ('zorro', 'gato', 'panda', 'buho', 'pinguino', 'conejo'):
    bpy.ops.wm.open_mainfile(filepath=f'/mnt/user-data/outputs/v0222/blender/{pet}_anim.blend')
    ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices)); me = ob.data
    names = [g.name for g in ob.vertex_groups]
    P = np.array([(ob.matrix_world @ v.co)[:] for v in me.vertices])
    W = np.zeros((len(P), len(names)), np.float32)
    for v in me.vertices:
        for g in v.groups: W[v.index, g.group] = g.weight
    np.savez_compressed(f'/home/claude/w/rig4/ref0222/{pet}.npz', P=P.astype(np.float32), W=W, names=np.array(names))
    print(pet, len(P), names, W.sum(0).round(0).tolist())
