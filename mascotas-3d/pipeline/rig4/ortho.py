# Orthographic front/back renders of the rig blend (rest pose) to locate the logos: pixel (i,j) -> x,z.
import bpy, sys, math
src, out = sys.argv[-2], sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
sc=bpy.context.scene
for o in bpy.data.objects:
    if o.type=='MESH' and len(o.data.polygons)<100: o.hide_render=True
sc.render.engine='BLENDER_WORKBENCH'; sc.display.shading.color_type='TEXTURE'
sc.display.shading.show_backface_culling=True; sc.display.shading.light='FLAT'
sc.render.resolution_x, sc.render.resolution_y = 1000, 1000
cd=bpy.data.cameras.new('c'); cd.type='ORTHO'; cd.ortho_scale=1.0
cam=bpy.data.objects.new('c',cd); sc.collection.objects.link(cam); sc.camera=cam
for k,(y,rz) in enumerate(((-3,0),(3,180))):
    cam.location=(0,y,0.5); cam.rotation_euler=(math.radians(90),0,math.radians(rz))
    sc.render.filepath=f'{out}_{k}.png'; bpy.ops.render.render(write_still=True)
