import sys, math
exec(open('/home/claude/w/rig4/base_head.py').read())
import bmesh
bm = bmesh.new(); bm.from_mesh(me); bm.faces.ensure_lookup_table()
sel=set()
for s in ('L','R'):
    a = Wm['Arm' + s] + Wm['Hand' + s]
    for f in bm.faces:
        if np.mean([a[v.index] for v in f.verts]) > 0.5: sel.add(f.index)
bm.free()
print('ARMFACES', len(sel))
red = bpy.data.materials.new('red'); red.diffuse_color=(1,0,0,1)
me.materials.append(red)
for p in me.polygons:
    if p.index in sel: p.material_index = 1
armob.hide_render=True
sc=bpy.context.scene
for o in bpy.data.objects:
    if o.type=='MESH' and o is not ob: o.hide_render=True
sc.render.engine='BLENDER_WORKBENCH'; sc.display.shading.color_type='TEXTURE'
sc.display.shading.show_backface_culling=True
sc.render.resolution_x, sc.render.resolution_y = 300, 360
cam_data=bpy.data.cameras.new('c'); cam=bpy.data.objects.new('c',cam_data); sc.collection.objects.link(cam); sc.camera=cam
piv=bpy.data.objects.new('piv',None); sc.collection.objects.link(piv); cam.parent=piv
cam.location=(0,-2.3,0.5); cam.rotation_euler=(math.radians(90),0,0)
for i,ang in enumerate((0,90,180,270)):
    piv.rotation_euler=(0,0,math.radians(ang))
    sc.render.filepath=f'/home/claude/w/rig4/shots/seg_{pet}_{i}.png'; bpy.ops.render.render(write_still=True)
