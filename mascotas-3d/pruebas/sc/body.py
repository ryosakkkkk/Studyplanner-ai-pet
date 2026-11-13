import bpy, bmesh, sys, math, numpy as np
src, out = sys.argv[-2:]
bpy.ops.wm.open_mainfile(filepath=src)
sc = bpy.context.scene
ob = max([o for o in bpy.data.objects if o.type == 'MESH'], key=lambda o: len(o.data.vertices))
me = ob.data
col = np.zeros(len(me.vertices) * 4); me.color_attributes['dbgcol'].data.foreach_get('color', col); col = col.reshape(-1, 4)
memb = ((np.abs(col[:, 0] - 0.5) < 0.01) & (np.abs(col[:, 1] - 0.1) < 0.01)) | ((np.abs(col[:, 0] - 0.1) < 0.01) & (np.abs(col[:, 1] - 0.2) < 0.01) & (np.abs(col[:, 2] - 0.5) < 0.01))
arm = ((np.abs(col[:, 0] - 1) < 0.01) & (np.abs(col[:, 1] - 0.2) < 0.01)) | ((np.abs(col[:, 0] - 0.2) < 0.01) & (np.abs(col[:, 1] - 0.4) < 0.01) & (np.abs(col[:, 2] - 1) < 0.01))
bm = bmesh.new(); bm.from_mesh(me); bm.verts.ensure_lookup_table()
bmesh.ops.delete(bm, geom=[f for f in bm.faces if any(memb[v.index] or arm[v.index] for v in f.verts)], context="FACES")
bm.to_mesh(me); bm.free()
for m in list(ob.modifiers): ob.modifiers.remove(m)
for o in bpy.data.objects:
    if o.type == 'MESH' and o is not ob: o.hide_render = True
me.color_attributes.active_color = me.color_attributes['dbgcol']
sc.render.engine = 'BLENDER_WORKBENCH'; sc.display.shading.color_type = 'VERTEX'; sc.display.shading.light = 'STUDIO'
sc.display.shading.show_backface_culling = True
sc.render.resolution_x = sc.render.resolution_y = 500
cd = bpy.data.cameras.new('c'); cd.lens = 260; cam = bpy.data.objects.new('c', cd); sc.collection.objects.link(cam); sc.camera = cam
piv = bpy.data.objects.new('piv', None); sc.collection.objects.link(piv); cam.parent = piv; import os; piv.location = (float(os.environ.get("PX","0.2")), 0, float(os.environ.get("PZ","0.35")))
cam.location = (0, -2.6, 0); cam.rotation_euler = (math.radians(90), 0, 0)
for i, ang in enumerate([float(a) for a in os.environ.get("ANGS","60,90,120,30").split(",")]):
    piv.rotation_euler = (0, 0, math.radians(ang))
    sc.render.filepath = f'{out}_{i}.png'; bpy.ops.render.render(write_still=True)
