# El cuerpo SIN la mochila (canal 'pack' de zmask) y sin forro, con bordes abiertos en rojo. Uso: sinmochila.py -- <blend> <salida> <res> cx cz escala "az:el,..." [canal]
import bpy, bmesh, sys, math, numpy as np
argv = sys.argv[sys.argv.index('--') + 1:]
src, out, res = argv[0], argv[1], int(argv[2]); cx, cz, scale = float(argv[3]), float(argv[4]), float(argv[5])
views = [(float(a.split(':')[0]), float(a.split(':')[1])) for a in argv[6].split(',')]
ch = int(argv[7]) if len(argv) > 7 else 1
bpy.ops.wm.open_mainfile(filepath=src)
sc = bpy.context.scene
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data
zm = np.zeros(len(me.vertices) * 4); me.color_attributes['zmask'].data.foreach_get('color', zm); zm = zm.reshape(-1, 4)
drop = zm[:, ch] > 0.5 if ch >= 0 else np.zeros(len(me.vertices), bool)
bm = bmesh.new(); bm.from_mesh(me); bm.verts.ensure_lookup_table()
bmesh.ops.delete(bm, geom=[f for f in bm.faces if any(drop[v.index] for v in f.verts)], context='FACES')
bm.to_mesh(me)
for m in list(ob.modifiers): ob.modifiers.remove(m)
for o in bpy.data.objects:
    if o is not ob: o.hide_render = True
def k(v): return (round(v.co.x * 1e6), round(v.co.y * 1e6), round(v.co.z * 1e6))
be = [e for e in bm.edges if len(e.link_faces) == 1]
cnt = {}
for e in be:
    kk = tuple(sorted((k(e.verts[0]), k(e.verts[1])))); cnt[kk] = cnt.get(kk, 0) + 1
op = [e for e in be if cnt[tuple(sorted((k(e.verts[0]), k(e.verts[1]))))] == 1]
V = []; E = []; idx = {}
for e in op:
    a = []
    for v in e.verts:
        if v.index not in idx: idx[v.index] = len(V); V.append(v.co[:])
        a.append(idx[v.index])
    E.append(a)
print('BORDE ABIERTO', len(op), 'aristas', round(sum(e.calc_length() for e in op), 2), 'm')
bm.free()
if V:
    em = bpy.data.meshes.new('bordes'); em.from_pydata(V, E, []); em.update()
    eo = bpy.data.objects.new('bordes', em); sc.collection.objects.link(eo)
    bpy.ops.object.select_all(action='DESELECT'); eo.select_set(True); bpy.context.view_layer.objects.active = eo
    bpy.ops.object.convert(target='CURVE'); eo = bpy.context.view_layer.objects.active
    eo.data.bevel_depth = 0.0012; eo.data.bevel_resolution = 1
    mt = bpy.data.materials.new('rojo'); mt.diffuse_color = (1, 0, 0, 1); eo.data.materials.append(mt)
sc.render.engine = 'BLENDER_WORKBENCH'
sh = sc.display.shading; sh.color_type = 'TEXTURE'; sh.light = 'FLAT'; sh.show_backface_culling = True
sc.world = sc.world or bpy.data.worlds.new('w'); sc.world.color = (0.0, 1.0, 0.0)
sc.render.resolution_x = sc.render.resolution_y = res
cd = bpy.data.cameras.new('c'); cd.type = 'ORTHO'; cd.ortho_scale = scale; cam = bpy.data.objects.new('c', cd); sc.collection.objects.link(cam); sc.camera = cam
piv = bpy.data.objects.new('piv', None); sc.collection.objects.link(piv); cam.parent = piv
cam.location = (0, -2.5, 0); cam.rotation_euler = (math.radians(90), 0, 0); piv.location = (cx, 0.0, cz)
for i, (az, el) in enumerate(views):
    piv.rotation_euler = (math.radians(-el), 0, math.radians(az))
    sc.render.filepath = f'{out}_{i}.png'; bpy.ops.render.render(write_still=True)
print('OK')
