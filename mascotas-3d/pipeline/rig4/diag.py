import bpy, bmesh, sys
import numpy as np
pet=sys.argv[-1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=f'/home/claude/w/rig2/src_hq/{pet}.glb')
obs=[o for o in bpy.data.objects if o.type=='MESH']
for o in obs: print('MESH',o.name,len(o.data.vertices),len(o.data.polygons), bool(o.data.shape_keys))
ob=[o for o in obs if o.data.shape_keys and 'MouthOpen' in o.data.shape_keys.key_blocks][0]
me=ob.data
bm=bmesh.new(); bm.from_mesh(me)
print('verts',len(bm.verts),'bnd',sum(e.is_boundary for e in bm.edges))
for th in (1e-4,1e-3,3e-3):
    b2=bm.copy(); bmesh.ops.remove_doubles(b2,verts=b2.verts,dist=th)
    nb=sum(e.is_boundary for e in b2.edges); nm=sum((not e.is_manifold) and not e.is_boundary for e in b2.edges)
    # components
    print('th',th,'verts',len(b2.verts),'bnd',nb,'nonmanifold',nm)
    b2.free()
