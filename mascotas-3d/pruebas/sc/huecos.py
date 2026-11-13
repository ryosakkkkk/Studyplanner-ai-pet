# Inspección en reposo: la malla con las aristas de BORDE ABIERTO marcadas en rojo y fondo verde (cara trasera oculta).
# Uso: huecos.py -- <blend con dbgcol> <salida> <piezas a quitar, p. ej. 1,2,9,10 o -> <res> [cx cz escala] [ángulos az:el,...]
import bpy, bmesh, sys, math, numpy as np
argv = sys.argv[sys.argv.index('--') + 1:]
src, out, dropl, res = argv[0], argv[1], argv[2], int(argv[3])
bpy.ops.wm.open_mainfile(filepath=src)
sc = bpy.context.scene
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data
if 'dbgcol' in me.color_attributes and dropl != '-':
    col = np.zeros(len(me.vertices) * 4); me.color_attributes['dbgcol'].data.foreach_get('color', col); col = col.reshape(-1, 4)[:, :3]
    pal = np.array([[0.85, 0.85, 0.85], [1, 0.2, 0.2], [0.2, 0.4, 1], [1, 1, 0.2], [1, 0.6, 0.1], [0.1, 0.9, 0.2], [0.1, 0.9, 0.9], [1, 0, 1], [0.6, 0.2, 0.9], [0.5, 0.1, 0.1], [0.1, 0.2, 0.5]])
    part = np.argmin(np.linalg.norm(col[:, None, :] - pal[None], axis=2), axis=1)
    drop = np.isin(part, [int(k) for k in dropl.split(',')])
    bm = bmesh.new(); bm.from_mesh(me); bm.verts.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if any(drop[v.index] for v in f.verts)], context='FACES')
    bm.to_mesh(me); bm.free()
for m in list(ob.modifiers): ob.modifiers.remove(m)
for o in bpy.data.objects:
    if o.type == 'MESH' and o is not ob: o.hide_render = True
    if o.type == 'ARMATURE': o.hide_render = True
# bordes abiertos (uniendo antes los vértices duplicados exactos de las piezas juntadas)
bm = bmesh.new(); bm.from_mesh(me)
bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
be = [e for e in bm.edges if len(e.link_faces) == 1]
tot = sum(e.calc_length() for e in be)
V = []; E = []
idx = {}
for e in be:
    a = []
    for v in e.verts:
        if v.index not in idx: idx[v.index] = len(V); V.append(v.co[:])
        a.append(idx[v.index])
    E.append(a)
bm.free()
print('BORDE ABIERTO', len(be), 'aristas', round(tot, 2), 'm')
if V:
    em = bpy.data.meshes.new('bordes'); em.from_pydata(V, E, []); em.update()
    eo = bpy.data.objects.new('bordes', em); sc.collection.objects.link(eo)
    bpy.ops.object.select_all(action='DESELECT'); eo.select_set(True); bpy.context.view_layer.objects.active = eo
    bpy.ops.object.convert(target='CURVE')
    eo = bpy.context.view_layer.objects.active
    eo.data.bevel_depth = float(__import__('os').environ.get('GROSOR', '0.0016')); eo.data.bevel_resolution = 1
    mt = bpy.data.materials.new('rojo'); mt.diffuse_color = (1, 0, 0, 1); eo.data.materials.append(mt)
sc.render.engine = 'BLENDER_WORKBENCH'
sh = sc.display.shading
sh.color_type = __import__('os').environ.get('COLOR', 'TEXTURE'); sh.light = 'FLAT' if sh.color_type == 'TEXTURE' else 'STUDIO'; sh.show_backface_culling = True
sc.world = sc.world or bpy.data.worlds.new('w'); sc.world.color = (0.0, 1.0, 0.0)
sc.render.film_transparent = False
sc.render.resolution_x = sc.render.resolution_y = res
cx, cz, scale = (float(argv[4]), float(argv[5]), float(argv[6])) if len(argv) > 6 else (0.0, 0.42, 1.0)
views = [(float(a.split(':')[0]), float(a.split(':')[1])) for a in argv[7].split(',')] if len(argv) > 7 else [(0, 0), (90, 0), (180, 0), (270, 0), (45, 35), (225, 35)]
cd = bpy.data.cameras.new('c'); cd.type = 'ORTHO'; cd.ortho_scale = scale; cam = bpy.data.objects.new('c', cd); sc.collection.objects.link(cam); sc.camera = cam
piv = bpy.data.objects.new('piv', None); sc.collection.objects.link(piv); cam.parent = piv
cam.location = (0, -2.5, 0); cam.rotation_euler = (math.radians(90), 0, 0)
piv.location = (cx, 0.0, cz)
for k, (az, el) in enumerate(views):
    piv.rotation_euler = (math.radians(-el), 0, math.radians(az))
    sc.render.filepath = f'{out}_{k}.png'; bpy.ops.render.render(write_still=True)
print('HUECOS ok')
