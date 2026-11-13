# Inspección de la malla: dibuja solo el brazo (y opcionalmente su pared) en reposo, desde varios ángulos, con textura
# o con colores por pieza. Uso: armonly.py -- <blend con dbgcol> <salida> <L|R> <tex|parts> [con_pared 0/1]
import bpy, bmesh, sys, math, os, numpy as np
src, out, side, mode, wall = sys.argv[-5:]
bpy.ops.wm.open_mainfile(filepath=src)
sc = bpy.context.scene
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data
col = np.zeros(len(me.vertices) * 4); me.color_attributes['dbgcol'].data.foreach_get('color', col); col = col.reshape(-1, 4)[:, :3]
pal = np.array([[0.85, 0.85, 0.85], [1, 0.2, 0.2], [0.2, 0.4, 1], [1, 1, 0.2], [1, 0.6, 0.1], [0.1, 0.9, 0.2], [0.1, 0.9, 0.9], [1, 0, 1], [0.6, 0.2, 0.9], [0.5, 0.1, 0.1], [0.1, 0.2, 0.5]])
part = np.argmin(np.linalg.norm(col[:, None, :] - pal[None], axis=2), axis=1)
si = 0 if side == 'L' else 1
keep = (part == 1 + si) | ((part == 9 + si) if wall == '1' else False)
P = np.array([v.co[:] for v in me.vertices])
cen = P[part == 1 + si].mean(0); ext = np.ptp(P[part == 1 + si], axis=0).max()
bm = bmesh.new(); bm.from_mesh(me); bm.verts.ensure_lookup_table()
bmesh.ops.delete(bm, geom=[f for f in bm.faces if not all(keep[v.index] for v in f.verts)], context='FACES')
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
sc.render.film_transparent = False
sc.world = sc.world or bpy.data.worlds.new('w'); sc.world.color = (0.0, 0.6, 0.0)
sc.render.resolution_x = sc.render.resolution_y = 420
cd = bpy.data.cameras.new('c'); cd.type = 'ORTHO'; cd.ortho_scale = ext * 1.5; cam = bpy.data.objects.new('c', cd); sc.collection.objects.link(cam); sc.camera = cam
piv = bpy.data.objects.new('piv', None); sc.collection.objects.link(piv); cam.parent = piv; piv.location = tuple(cen)
cam.location = (0, -2.0, 0); cam.rotation_euler = (math.radians(90), 0, 0)
sg = 1 if side == 'L' else -1
views = [('frente', 0, 0), ('fuera', 90 * sg, 0), ('atras', 180, 0), ('dentro', -90 * sg, 0), ('abajo', 0, 80), ('dentro_abajo', -60 * sg, 40)]
for name, az, el in views:
    piv.rotation_euler = (math.radians(el), 0, math.radians(az))
    sc.render.filepath = f'{out}_{name}.png'; bpy.ops.render.render(write_still=True)
print('ARMONLY', side, 'centro', cen.round(3), 'ext', round(float(ext), 3), 'verts', int((part == 1 + si).sum()), 'pared', int((part == 9 + si).sum()))
