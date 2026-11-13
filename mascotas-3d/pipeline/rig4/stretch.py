import bpy, sys, math, numpy as np
from mathutils import Euler
src = sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
armob=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
ob=[o for o in bpy.data.objects if o.type=='MESH' and o.data.shape_keys][0]
if armob.animation_data:
    armob.animation_data.action=None
    for t in armob.animation_data.nla_tracks: t.mute=True
pbs=armob.pose.bones
rest={b.name:b.bone.matrix_local.to_3x3() for b in pbs}
def rot(name, rx=0, ry=0, rz=0):
    pbs[name].rotation_mode='QUATERNION'
    Rw=Euler((math.radians(rx),math.radians(ry),math.radians(rz)),'XYZ').to_matrix()
    pbs[name].rotation_quaternion=(rest[name].inverted()@Rw@rest[name]).to_quaternion()
for pb in pbs: pb.rotation_mode='QUATERNION'; pb.rotation_quaternion=(1,0,0,0); pb.location=(0,0,0); pb.scale=(1,1,1)
rot('ArmR', -12, 76, 0); rot('HandR', 0, 50, 0)
bpy.context.view_layer.update()
me=ob.data
P0=np.array([v.co[:] for v in me.vertices])
dg=bpy.context.evaluated_depsgraph_get(); oe=ob.evaluated_get(dg); m2=oe.to_mesh()
P1=np.array([v.co[:] for v in m2.vertices])
E=np.array([e.vertices[:] for e in me.edges])
l0=np.linalg.norm(P0[E[:,0]]-P0[E[:,1]],axis=1); l1=np.linalg.norm(P1[E[:,0]]-P1[E[:,1]],axis=1)
r=l1/np.maximum(l0,1e-6)
bad=np.nonzero((r>2.5)&(l1>0.01))[0]
print('edges',len(E),'stretched',len(bad))
vg={g.index:g.name for g in ob.vertex_groups}
for i in bad[:12]:
    a,b=E[i]
    wa={vg[g.group]:round(g.weight,2) for g in me.vertices[a].groups}; wb={vg[g.group]:round(g.weight,2) for g in me.vertices[b].groups}
    print(np.round(P0[a],3),wa,'|',np.round(P0[b],3),wb, round(l0[i],4), round(l1[i],4))
