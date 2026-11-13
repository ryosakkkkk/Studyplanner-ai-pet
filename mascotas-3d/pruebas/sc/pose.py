# Poses en Blender (misma cámara y luz para comparar): pose.py -- <anim.blend> <salida_prefijo> <clip:cuadro,clip:cuadro,...> <angulos,...> [cx cz escala]
import bpy, sys, math, os
args = sys.argv[sys.argv.index('--') + 1:]
src, out, shots, angs = args[:4]
cx, cz, scale = (float(a) for a in args[4:7]) if len(args) >= 7 else (0.0, 0.42, 0.75)
bpy.ops.wm.open_mainfile(filepath=src)
sc = bpy.context.scene
armob = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
ob = [o for o in bpy.data.objects if o.type == 'MESH' and o.data.shape_keys][0]
me = ob.data
for o in bpy.data.objects:
    if o.type == 'MESH' and o is not ob: o.hide_render = True
sc.render.engine = 'BLENDER_WORKBENCH'
sh = sc.display.shading
mode = os.environ.get('MODE', 'tex')
if mode == 'parts' and 'dbgcol' in me.color_attributes:
    me.color_attributes.active_color = me.color_attributes['dbgcol']; sh.color_type = 'VERTEX'
else:
    sh.color_type = 'TEXTURE'
sh.light = os.environ.get('LIGHT', 'STUDIO'); sh.show_backface_culling = True
sc.world = sc.world or bpy.data.worlds.new('w'); sc.world.color = (0.0, 0.6, 0.0)
res = int(os.environ.get('RES', '420'))
sc.render.resolution_x = sc.render.resolution_y = res
cd = bpy.data.cameras.new('c'); cd.type = 'ORTHO'; cd.ortho_scale = scale
cam = bpy.data.objects.new('c', cd); sc.collection.objects.link(cam); sc.camera = cam
piv = bpy.data.objects.new('piv', None); sc.collection.objects.link(piv); cam.parent = piv; piv.location = (cx, 0, cz)
cam.location = (0, -2.6, 0); cam.rotation_euler = (math.radians(90), 0, 0)
elev = float(os.environ.get('ELEV', '0'))
for shot in shots.split(','):
    clip, fr = shot.split(':')
    for t in armob.animation_data.nla_tracks: t.mute = (t.name != clip)
    if me.shape_keys.animation_data:
        for t in me.shape_keys.animation_data.nla_tracks: t.mute = True
    sc.frame_set(int(fr))
    for a in angs.split(','):
        piv.rotation_euler = (math.radians(elev), 0, math.radians(float(a)))
        sc.render.filepath = f'{out}_{clip}{fr}_a{a}.png'; bpy.ops.render.render(write_still=True)
print('POSE ok')
