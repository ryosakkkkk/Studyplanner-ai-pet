# Renders a rig blend: rest pose (4 views) and arms raised sideways (4 views), backface culling on.
import bpy, sys, math
from mathutils import Euler
src, out = sys.argv[-2], sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
sc=bpy.context.scene
armob=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
for o in bpy.data.objects:
    if o.type=='MESH' and len(o.data.polygons)<100: o.hide_render=True
sc.render.engine='BLENDER_WORKBENCH'; sc.display.shading.color_type='TEXTURE'
sc.display.shading.show_backface_culling=True
sc.display.shading.light='STUDIO'
sc.render.resolution_x, sc.render.resolution_y = 300, 360
cam_data=bpy.data.cameras.new('c'); cam=bpy.data.objects.new('c',cam_data); sc.collection.objects.link(cam); sc.camera=cam
piv=bpy.data.objects.new('piv',None); sc.collection.objects.link(piv); cam.parent=piv
cam.location=(0,-2.3,0.5); cam.rotation_euler=(math.radians(90),0,0)
pbs=armob.pose.bones
rest={b.name:b.bone.matrix_local.to_3x3() for b in pbs}
def rot(name, rx=0, ry=0, rz=0):
    if name not in pbs: return
    pbs[name].rotation_mode='QUATERNION'
    Rw=Euler((math.radians(rx),math.radians(ry),math.radians(rz)),'XYZ').to_matrix()
    pbs[name].rotation_quaternion=(rest[name].inverted()@Rw@rest[name]).to_quaternion()
for k,pose in enumerate(('rest','up')):
    if pose=='up':
        rot('ArmL', 0, -75, 0); rot('ArmR', 0, 75, 0)
    bpy.context.view_layer.update()
    for i,ang in enumerate((0,90,180,270)):
        piv.rotation_euler=(0,0,math.radians(ang))
        sc.render.filepath=f'{out}_{pose}_{i}.png'; bpy.ops.render.render(write_still=True)
