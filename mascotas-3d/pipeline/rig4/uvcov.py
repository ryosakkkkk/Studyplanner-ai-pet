import bpy, sys, numpy as np
from PIL import Image, ImageDraw
pet=sys.argv[-1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=f'/home/claude/w/rig2/src_hq/{pet}.glb')
ob=[o for o in bpy.data.objects if o.type=='MESH' and o.data.shape_keys][0]; me=ob.data
for im in bpy.data.images: print('IMG',im.name,im.size[:],im.file_format,im.filepath, im.packed_file is not None)
R=2048
uv=np.zeros((len(me.loops),2)); me.uv_layers.active.data.foreach_get('uv',uv.ravel())
m=Image.new('L',(R,R),0); d=ImageDraw.Draw(m)
for p in me.polygons:
    pts=[(uv[l,0]*R,(1-uv[l,1])*R) for l in p.loop_indices]
    d.polygon(pts,fill=255)
a=np.array(m)>0
print('coverage',a.mean())
m.save(f'/home/claude/w/rig4/shots/uvcov_{pet}.png')
# largest empty square via distance transform
from scipy import ndimage
dt=ndimage.distance_transform_cdt(~a, metric='chessboard')
print('max empty half-size',dt.max(), 'count >=40:', int((dt>=40).sum()))
