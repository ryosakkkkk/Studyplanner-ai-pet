import bpy, sys, numpy as np
for f in sys.argv[sys.argv.index('--') + 1:]:
    bpy.ops.wm.open_mainfile(filepath=f)
    ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices)); me = ob.data
    names = [g.name for g in ob.vertex_groups]; cnt = {n: 0 for n in names}; tot = {n: 0.0 for n in names}
    for v in me.vertices:
        for g in v.groups:
            tot[names[g.group]] += g.weight
            if g.weight > 0.5: cnt[names[g.group]] += 1
    print(f.split('/')[-1], {k: (cnt[k], round(tot[k])) for k in names if k.startswith(('Tail', 'Ear', 'Leg', 'Foot'))})
