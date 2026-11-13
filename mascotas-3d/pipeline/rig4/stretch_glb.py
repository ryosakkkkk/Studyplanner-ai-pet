import bpy, sys, numpy as np
glb, clip, frame = sys.argv[-3], sys.argv[-2], int(sys.argv[-1])
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
arm=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
ob=max([o for o in bpy.data.objects if o.type=='MESH'], key=lambda o: len(o.data.vertices)); print('MESHES',[(o.name,len(o.data.vertices)) for o in bpy.data.objects if o.type=='MESH'])
act=bpy.data.actions.get(clip)
arm.animation_data_create(); arm.animation_data.action=act
try: arm.animation_data.action_slot = act.slots[0]
except Exception as e: print('slot',e)
for t in arm.animation_data.nla_tracks: t.mute=True
sc=bpy.context.scene; sc.frame_set(0)
me=ob.data
dg=bpy.context.evaluated_depsgraph_get(); P0=np.array([v.co[:] for v in ob.evaluated_get(dg).to_mesh().vertices])
sc.frame_set(frame); bpy.context.view_layer.update()
dg=bpy.context.evaluated_depsgraph_get(); P1=np.array([v.co[:] for v in ob.evaluated_get(dg).to_mesh().vertices])
E=np.array([e.vertices[:] for e in me.edges])
l0=np.linalg.norm(P0[E[:,0]]-P0[E[:,1]],axis=1); l1=np.linalg.norm(P1[E[:,0]]-P1[E[:,1]],axis=1)
r=l1/np.maximum(l0,1e-6); bad=np.nonzero((r>2.5)&(l1>0.01))[0]
print('verts',len(P0),'edges',len(E),'stretched',len(bad))
vg={g.index:g.name for g in ob.vertex_groups}
for i in bad[:10]:
    a,b=E[i]
    wa={vg[g.group]:round(g.weight,2) for g in me.vertices[a].groups}; wb={vg[g.group]:round(g.weight,2) for g in me.vertices[b].groups}
    print(np.round(me.vertices[a].co[:],3),wa,'|',np.round(me.vertices[b].co[:],3),wb, round(l0[i],4), round(l1[i],4))
