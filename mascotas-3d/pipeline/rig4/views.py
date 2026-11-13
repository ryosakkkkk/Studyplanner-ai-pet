# Renders 4 views (front, left, back, right) of a GLB with backface culling, like three.js FrontSide.
import bpy, sys, math
glb, out = sys.argv[-2], sys.argv[-1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
sc=bpy.context.scene
for o in bpy.data.objects:
    if o.type=='MESH' and o.name.lower().startswith('ico'): o.hide_render=True
sc.render.engine='BLENDER_WORKBENCH'; sc.display.shading.color_type='TEXTURE'
sc.display.shading.show_backface_culling=True
sc.display.shading.light='STUDIO'
sc.render.film_transparent=False
sc.world = bpy.data.worlds.new('w'); 
sc.display.shading.background_type='VIEWPORT' if hasattr(sc.display.shading,'background_type') else None
sc.render.resolution_x, sc.render.resolution_y = 400, 480
cam_data=bpy.data.cameras.new('c'); cam=bpy.data.objects.new('c',cam_data); sc.collection.objects.link(cam); sc.camera=cam
piv=bpy.data.objects.new('piv',None); sc.collection.objects.link(piv); cam.parent=piv
cam.location=(0,-2.3,0.5); cam.rotation_euler=(math.radians(90),0,0)
for i,ang in enumerate((0,90,180,270)):
    piv.rotation_euler=(0,0,math.radians(ang))
    sc.render.filepath=f'{out}_{i}.png'; bpy.ops.render.render(write_still=True)
