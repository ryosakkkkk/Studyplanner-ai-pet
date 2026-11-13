import bpy, sys, math
mode, src, clip, frame, cx, cz, ang, out = sys.argv[-8:]
frame=int(frame); cx=float(cx); cz=float(cz); ang=float(ang)
bpy.ops.wm.open_mainfile(filepath=src)
sc=bpy.context.scene
armob=[o for o in bpy.data.objects if o.type=='ARMATURE'][0]
ob=[o for o in bpy.data.objects if o.type=='MESH' and o.data.shape_keys][0]
me=ob.data
for t in armob.animation_data.nla_tracks: t.mute = (t.name != clip)
for t in me.shape_keys.animation_data.nla_tracks: t.mute = True
sc.frame_set(frame)
if mode == 'parts':
    me.color_attributes.active_color = me.color_attributes['dbgcol']; sc.display.shading.color_type='VERTEX'
else:
    sc.display.shading.color_type='TEXTURE'
for o in bpy.data.objects:
    if o.type=='MESH' and o is not ob: o.hide_render=True
sc.render.engine='BLENDER_WORKBENCH'
sc.display.shading.show_backface_culling=True; sc.display.shading.light=__import__('os').environ.get('LIGHT','STUDIO')
sc.world = sc.world or bpy.data.worlds.new('w')
sc.render.resolution_x, sc.render.resolution_y = 700, 700
cd=bpy.data.cameras.new('c'); cd.lens=float(__import__("os").environ.get("LENS","260")); cam=bpy.data.objects.new('c',cd); sc.collection.objects.link(cam); sc.camera=cam
piv=bpy.data.objects.new('piv',None); sc.collection.objects.link(piv); cam.parent=piv; piv.location=(cx,0,cz)
cam.location=(0,-2.6,0); cam.rotation_euler=(math.radians(90),0,0); piv.rotation_euler=(math.radians(float(__import__("os").environ.get("ELEV","0"))),0,math.radians(ang))
sc.render.filepath=out; bpy.ops.render.render(write_still=True)
