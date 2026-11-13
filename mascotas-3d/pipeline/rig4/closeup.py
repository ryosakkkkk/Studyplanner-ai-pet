# Close-up render of a region, rest pose or a named action frame, texture shading.
import bpy, sys, math
src, cx, cz, ang, out = sys.argv[-5], float(sys.argv[-4]), float(sys.argv[-3]), float(sys.argv[-2]), sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
sc=bpy.context.scene
for o in bpy.data.objects:
    if o.type=='MESH' and len(o.data.polygons)<100: o.hide_render=True
arm=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
if arm.animation_data:
    for t in arm.animation_data.nla_tracks: t.mute=True
    arm.animation_data.action=None
sc.render.engine='BLENDER_WORKBENCH'; sc.display.shading.color_type='TEXTURE'
sc.display.shading.show_backface_culling=True; sc.display.shading.light='STUDIO'
sc.render.resolution_x, sc.render.resolution_y = 700, 700
cd=bpy.data.cameras.new('c'); cd.lens=200; cam=bpy.data.objects.new('c',cd); sc.collection.objects.link(cam); sc.camera=cam
piv=bpy.data.objects.new('piv',None); sc.collection.objects.link(piv); cam.parent=piv; piv.location=(cx,0,cz)
cam.location=(0,-2.6,0); cam.rotation_euler=(math.radians(90),0,0); piv.rotation_euler=(0,0,math.radians(ang))
sc.render.filepath=out; bpy.ops.render.render(write_still=True)
