# Renders part colors (dbgcol) in a wave-like pose, front and 3/4 views.
import bpy, sys, math
from mathutils import Euler
src, out = sys.argv[-2], sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
sc=bpy.context.scene
armob=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
ob=[o for o in bpy.data.objects if o.type=='MESH' and o.data.shape_keys][0]
me=ob.data
me.color_attributes.active_color = me.color_attributes['dbgcol']
for o in bpy.data.objects:
    if o.type=='MESH' and o is not ob: o.hide_render=True
sc.render.engine='BLENDER_WORKBENCH'; sc.display.shading.color_type='VERTEX'
sc.display.shading.show_backface_culling=True; sc.display.shading.light='STUDIO'
sc.render.resolution_x, sc.render.resolution_y = 600, 600
cam_data=bpy.data.cameras.new('c'); cam_data.lens=110; cam=bpy.data.objects.new('c',cam_data); sc.collection.objects.link(cam); sc.camera=cam
piv=bpy.data.objects.new('piv',None); sc.collection.objects.link(piv); cam.parent=piv; piv.location=(-0.15,0,0.38)
cam.location=(0,-2.6,0.0); cam.rotation_euler=(math.radians(90),0,0)
pbs=armob.pose.bones
rest={b.name:b.bone.matrix_local.to_3x3() for b in pbs}
def rot(name, rx=0, ry=0, rz=0):
    pbs[name].rotation_mode='QUATERNION'
    Rw=Euler((math.radians(rx),math.radians(ry),math.radians(rz)),'XYZ').to_matrix()
    pbs[name].rotation_quaternion=(rest[name].inverted()@Rw@rest[name]).to_quaternion()
rot('ArmR', -12, 76, 0); rot('HandR', 0, 50, 0)
bpy.context.view_layer.update()
for k,ang in enumerate((0, 30, -40)):
    piv.rotation_euler=(0,0,math.radians(ang))
    sc.render.filepath=f'{out}_{k}.png'; bpy.ops.render.render(write_still=True)
