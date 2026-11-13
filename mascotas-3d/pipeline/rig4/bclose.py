# Close-up renders: side views at rest and with the arm raised, 2x resolution.
import bpy, sys, math
from mathutils import Euler
src, out = sys.argv[-2], sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
sc=bpy.context.scene
armob=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
for o in bpy.data.objects:
    if o.type=='MESH' and len(o.data.polygons)<100: o.hide_render=True
sc.render.engine='BLENDER_EEVEE' if 'BLENDER_EEVEE' in [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items] else 'BLENDER_WORKBENCH'
sc.render.engine='BLENDER_WORKBENCH'; sc.display.shading.color_type='TEXTURE'
sc.display.shading.show_backface_culling=True; sc.display.shading.light='STUDIO'
sc.render.resolution_x, sc.render.resolution_y = 520, 520
cam_data=bpy.data.cameras.new('c'); cam_data.lens=85; cam=bpy.data.objects.new('c',cam_data); sc.collection.objects.link(cam); sc.camera=cam
piv=bpy.data.objects.new('piv',None); sc.collection.objects.link(piv); cam.parent=piv; piv.location=(0,0,0.38)
cam.location=(0,-2.6,0.0); cam.rotation_euler=(math.radians(90),0,0)
pbs=armob.pose.bones
rest={b.name:b.bone.matrix_local.to_3x3() for b in pbs}
def rot(name, rx=0, ry=0, rz=0):
    if name not in pbs: return
    pbs[name].rotation_mode='QUATERNION'
    Rw=Euler((math.radians(rx),math.radians(ry),math.radians(rz)),'XYZ').to_matrix()
    pbs[name].rotation_quaternion=(rest[name].inverted()@Rw@rest[name]).to_quaternion()
shots=[('rest',60,0),('rest',120,0),('rest',-60,0),('up',75,0),('up',110,0),('fwd',75,0),('up',-75,0),('up',30,0)]
for k,(pose,ang,_) in enumerate(shots):
    for n in ('ArmL','ArmR','HandL','HandR'): rot(n)
    if pose=='up': rot('ArmL',0,-80,0); rot('ArmR',0,80,0)
    if pose=='fwd': rot('ArmL',-80,0,0); rot('ArmR',-80,0,0)
    bpy.context.view_layer.update()
    piv.rotation_euler=(0,0,math.radians(ang))
    sc.render.filepath=f'{out}_{k}.png'; bpy.ops.render.render(write_still=True)
