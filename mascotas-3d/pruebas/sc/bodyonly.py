# Inspección: el cuerpo SIN los brazos (reposo), con o sin el parche y la pared del cuerpo.
# Uso: bodyonly.py -- <blend con dbgcol> <salida> <tex|parts> <con_parche 0/1>
import bpy, bmesh, sys, math, numpy as np
src, out, mode, withplug = sys.argv[-4:]
bpy.ops.wm.open_mainfile(filepath=src)
sc = bpy.context.scene
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
me = ob.data
col = np.zeros(len(me.vertices) * 4); me.color_attributes['dbgcol'].data.foreach_get('color', col); col = col.reshape(-1, 4)[:, :3]
pal = np.array([[0.85, 0.85, 0.85], [1, 0.2, 0.2], [0.2, 0.4, 1], [1, 1, 0.2], [1, 0.6, 0.1], [0.1, 0.9, 0.2], [0.1, 0.9, 0.9], [1, 0, 1], [0.6, 0.2, 0.9], [0.5, 0.1, 0.1], [0.1, 0.2, 0.5]])
part = np.argmin(np.linalg.norm(col[:, None, :] - pal[None], axis=2), axis=1)
drop = np.isin(part, [1, 2, 9, 10])
if withplug == '0': drop |= np.isin(part, [3, 4, 7, 8])
bm = bmesh.new(); bm.from_mesh(me); bm.verts.ensure_lookup_table()
bmesh.ops.delete(bm, geom=[f for f in bm.faces if any(drop[v.index] for v in f.verts)], context='FACES')
bm.to_mesh(me); bm.free()
for m in list(ob.modifiers): ob.modifiers.remove(m)
for o in bpy.data.objects:
    if o.type == 'MESH' and o is not ob: o.hide_render = True
sc.render.engine = 'BLENDER_WORKBENCH'
sh = sc.display.shading
if mode == 'parts':
    me.color_attributes.active_color = me.color_attributes['dbgcol']; sh.color_type = 'VERTEX'
else:
    sh.color_type = 'TEXTURE'
sh.light = 'STUDIO'; sh.show_backface_culling = True
sc.world = sc.world or bpy.data.worlds.new('w'); sc.world.color = (0.0, 0.6, 0.0)
sc.render.resolution_x = sc.render.resolution_y = 420
B = {b.name: arm.matrix_world @ b.head_local for b in arm.data.bones}
cd = bpy.data.cameras.new('c'); cd.type = 'ORTHO'; cd.ortho_scale = 0.42; cam = bpy.data.objects.new('c', cd); sc.collection.objects.link(cam); sc.camera = cam
piv = bpy.data.objects.new('piv', None); sc.collection.objects.link(piv); cam.parent = piv
cam.location = (0, -2.0, 0); cam.rotation_euler = (math.radians(90), 0, 0)
for s, sg in (('L', 1), ('R', -1)):
    j = B['Arm' + s]
    piv.location = (j.x * 1.0, 0.0, j.z - 0.10)
    for name, az, el in (('lado', 90 * sg, 0), ('frente45', 40 * sg, 0), ('atras45', 140 * sg, 0), ('abajo', 70 * sg, 35)):
        piv.rotation_euler = (math.radians(el), 0, math.radians(az))
        sc.render.filepath = f'{out}_{s}_{name}.png'; bpy.ops.render.render(write_still=True)
print('BODYONLY ok')
