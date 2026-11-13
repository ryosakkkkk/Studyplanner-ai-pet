import bpy, sys, math
src, clip, frame, out = sys.argv[-4], sys.argv[-3], int(sys.argv[-2]), sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
sc=bpy.context.scene
armob=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
ob=[o for o in bpy.data.objects if o.type=='MESH' and o.data.shape_keys][0]
me=ob.data
for t in armob.animation_data.nla_tracks: t.mute = (t.name != clip)
for t in me.shape_keys.animation_data.nla_tracks: t.mute = True
sc.frame_set(frame)
mode = sys.argv[-5]
if mode == 'parts':
    me.color_attributes.active_color = me.color_attributes['dbgcol']; sc.display.shading.color_type='VERTEX'
else:
    sc.display.shading.color_type='TEXTURE'
for o in bpy.data.objects:
    if o.type=='MESH' and o is not ob: o.hide_render=True
sc.render.engine='BLENDER_WORKBENCH'
sc.display.shading.show_backface_culling=True; sc.display.shading.light='STUDIO'
sc.render.film_transparent=False
sc.render.resolution_x, sc.render.resolution_y = 700, 700
cam_data=bpy.data.cameras.new('c'); cam_data.lens=120; cam=bpy.data.objects.new('c',cam_data); sc.collection.objects.link(cam); sc.camera=cam
piv=bpy.data.objects.new('piv',None); sc.collection.objects.link(piv); cam.parent=piv; piv.location=(0,0,0.36)
cam.location=(0,-2.6,0.12); cam.rotation_euler=(math.radians(87),0,0)
for k,ang in enumerate((0, 28, -32)):
    piv.rotation_euler=(0,0,math.radians(ang))
    sc.render.filepath=f'{out}_{k}.png'; bpy.ops.render.render(write_still=True)
