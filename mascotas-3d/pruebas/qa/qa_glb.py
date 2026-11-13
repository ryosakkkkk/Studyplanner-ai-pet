# QA de un GLB exportado (se reimporta): para cada animación y cada 2 cuadros mide
#  - estiramiento de aristas (largo / largo en reposo) en la malla principal
#  - rendija brazo-cuerpo: distancia entre pares de vértices que en reposo coinciden (uno de brazo, otro de cuerpo)
#  - saltos: desplazamiento máximo entre cuadros consecutivos y diferencia entre el primer y último cuadro (ciclos)
import bpy, sys, json, numpy as np
from scipy.spatial import cKDTree
glb, out = sys.argv[-2], sys.argv[-1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
meshes = [o for o in bpy.data.objects if o.type == 'MESH' and o.parent == arm or (o.type == 'MESH' and any(m.type == 'ARMATURE' for m in o.modifiers))]
ob = max(meshes, key=lambda o: len(o.data.vertices))
me = ob.data
vg = {g.index: g.name for g in ob.vertex_groups}
W = {}
for v in me.vertices:
    for g in v.groups:
        W.setdefault(vg[g.group], np.zeros(len(me.vertices)))[v.index] = g.weight
zero = np.zeros(len(me.vertices))
armw = sum(W.get(n, zero) for n in ('ArmL', 'ArmR', 'HandL', 'HandR'))
P0 = np.array([v.co[:] for v in me.vertices])
E = np.array([e.vertices[:] for e in me.edges])
L0 = np.linalg.norm(P0[E[:, 0]] - P0[E[:, 1]], axis=1)
# pares brazo/cuerpo que coinciden en reposo
A = np.nonzero(armw > 0.9)[0]; B = np.nonzero(armw < 0.1)[0]
pairs = np.zeros((0, 2), int)
if len(A) and len(B):
    d, j = cKDTree(P0[B]).query(P0[A]); m = d < 1e-5
    pairs = np.c_[A[m], B[j[m]]]
sc = bpy.context.scene
acts = [a for a in bpy.data.actions]
names = sorted({a.name.split('_boca')[0] for a in acts if not a.name.endswith('_boca')} )
arm.animation_data_create()
res = {}
def evalP():
    dg = bpy.context.evaluated_depsgraph_get(); m2 = ob.evaluated_get(dg).to_mesh()
    p = np.empty(len(m2.vertices) * 3); m2.vertices.foreach_get('co', p); return p.reshape(-1, 3)
for nm in names:
    act = bpy.data.actions.get(nm)
    if act is None: continue
    arm.animation_data.action = act
    try: arm.animation_data.action_slot = act.slots[0]
    except Exception: pass
    for t in arm.animation_data.nla_tracks: t.mute = True
    f0, f1 = int(act.frame_range[0]), int(act.frame_range[1])
    grow_mm = 0.0; grow99 = 0.0
    prev = None; maxstr = 1.0; p99 = 1.0; gap = 0.0; jump = 0.0; steps = []
    first = None
    for fr in range(f0, f1 + 1, 2):
        sc.frame_set(fr)
        P = evalP()
        if first is None: first = P.copy()
        L = np.linalg.norm(P[E[:, 0]] - P[E[:, 1]], axis=1); r = L / np.maximum(L0, 1e-6)
        r = np.where(L0 > 1e-4, r, 1.0)
        maxstr = max(maxstr, float(r.max())); p99 = max(p99, float(np.percentile(r, 99.9)))
        dl = L - L0; grow_mm = max(grow_mm, float(dl.max()) * 1000); grow99 = max(grow99, float(np.percentile(dl, 99.95)) * 1000)
        if len(pairs): gap = max(gap, float(np.linalg.norm(P[pairs[:, 0]] - P[pairs[:, 1]], axis=1).max()))
        if prev is not None: steps.append(float(np.linalg.norm(P - prev, axis=1).max()))
        prev = P
    loop = float(np.linalg.norm(prev - first, axis=1).max())
    st = np.array(steps) if steps else np.zeros(1)
    res[nm] = {'alarga_max_mm': round(grow_mm, 1), 'alarga_p9995_mm': round(grow99, 1), 'frames': f1 - f0 + 1, 'estiramiento_max': round(maxstr, 2), 'estiramiento_p999': round(p99, 2), 'rendija_mm': round(gap * 1000, 1),
               'paso_max_mm': round(float(st.max()) * 1000, 1), 'paso_mediana_mm': round(float(np.median(st)) * 1000, 1), 'cierre_ciclo_mm': round(loop * 1000, 1)}
info = {'vertices': len(me.vertices), 'caras': len(me.polygons), 'huesos': len(arm.data.bones), 'pares_borde': int(len(pairs)),
        'materiales': [m.name for m in me.materials], 'animaciones': len(res)}
json.dump({'info': info, 'clips': res}, open(out, 'w'), indent=1, ensure_ascii=False)
print('QA', json.dumps(info, ensure_ascii=False))
