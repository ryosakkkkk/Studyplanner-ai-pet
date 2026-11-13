# QA v0.23 de un GLB exportado (se REIMPORTA en Blender): estructura para la app y deformación cuadro por cuadro.
#   python3 qa23.py -- <glb> <salida.json>
# Estructura: huesos (nombre, padre, posición de reposo), clips (nombre, duración), malla, materiales, atributos,
#   influencias por vértice, caja. Sirve para comparar con la versión anterior (compatibilidad de la integración).
# Deformación, en CADA cuadro de CADA clip (30 cuadros por segundo):
#   alarga_mm        cuánto crece la arista que más crece (máximo y percentil 99,95)
#   pico_mm          cuánto se aleja un vértice del promedio de sus vecinos respecto del reposo (un pico suelto)
#   volumen          volumen de toda la malla y de cada brazo respecto del reposo (mínimo y máximo de la razón)
#   paso_mm          mayor movimiento de un vértice entre cuadros seguidos (saltos)
#   cierre_mm        diferencia entre el último y el primer cuadro (solo importa en los clips que se repiten)
import bpy, sys, json, numpy as np
from scipy.sparse import csr_matrix
argv = sys.argv[sys.argv.index('--') + 1:]
glb, out = argv[0], argv[1]
LOOPS = {'idle', 'talk_body', 'think', 'sleep', 'run', 'walk'}
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.scene.render.fps = 30
bpy.ops.import_scene.gltf(filepath=glb)
sc = bpy.context.scene
arm = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
meshes = [o for o in bpy.data.objects if o.type == 'MESH' and any(m.type == 'ARMATURE' for m in o.modifiers)]
ob = max(meshes, key=lambda o: len(o.data.vertices))
me = ob.data
nv = len(me.vertices)
vg = {g.index: g.name for g in ob.vertex_groups}
W = {}
ninf = np.zeros(nv, int)
for v in me.vertices:
    for g in v.groups:
        if g.weight > 1e-6:
            W.setdefault(vg[g.group], np.zeros(nv))[v.index] = g.weight; ninf[v.index] += 1
zero = np.zeros(nv)
arm.animation_data_create()
for t in arm.animation_data.nla_tracks: t.mute = True
arm.animation_data.action = None
for pb in arm.pose.bones:
    pb.rotation_mode = 'QUATERNION'; pb.location = (0, 0, 0); pb.rotation_quaternion = (1, 0, 0, 0); pb.scale = (1, 1, 1)
if me.shape_keys:
    if me.shape_keys.animation_data:
        me.shape_keys.animation_data.action = None
        for t in me.shape_keys.animation_data.nla_tracks: t.mute = True
    for kb in me.shape_keys.key_blocks: kb.value = 0.0
bpy.context.view_layer.update()
def _evalP0():
    dg = bpy.context.evaluated_depsgraph_get(); m2 = ob.evaluated_get(dg).to_mesh()
    p = np.empty(len(m2.vertices) * 3); m2.vertices.foreach_get('co', p); ob.evaluated_get(dg).to_mesh_clear(); return p.reshape(-1, 3)
Pbase = np.empty(nv * 3); me.vertices.foreach_get('co', Pbase); Pbase = Pbase.reshape(-1, 3)
P0 = _evalP0()                       # reposo evaluado (huesos en reposo, boca cerrada)
rest_err = float(np.linalg.norm(P0 - Pbase, axis=1).max()) * 1000
E = np.array([e.vertices[:] for e in me.edges])
L0 = np.linalg.norm(P0[E[:, 0]] - P0[E[:, 1]], axis=1)
F = np.array([p.vertices[:] for p in me.polygons if len(p.vertices) == 3])
A = csr_matrix((np.ones(2 * len(E)), (np.r_[E[:, 0], E[:, 1]], np.r_[E[:, 1], E[:, 0]])), shape=(nv, nv))
deg = np.maximum(np.asarray(A.sum(1)).ravel(), 1)
def lap(P): return np.linalg.norm(P - (A @ P) / deg[:, None], axis=1)
lap0 = lap(P0)
def vol(P, faces, about=None):
    if not len(faces): return 0.0
    c = about if about is not None else np.zeros(3)
    a, b, d = P[faces[:, 0]] - c, P[faces[:, 1]] - c, P[faces[:, 2]] - c
    return float(np.einsum('ij,ij->i', a, np.cross(b, d)).sum() / 6.0)
sets = {}
for s in ('L', 'R'):
    w = W.get('Arm' + s, zero) + W.get('Hand' + s, zero)
    fs = F[(w[F] >= 0.5).all(1)]
    sets['brazo' + s] = (fs, np.unique(fs) if len(fs) else np.zeros(0, int))
V0 = vol(P0, F); VA0 = {k: vol(P0, fs, P0[vs].mean(0)) if len(fs) else 0.0 for k, (fs, vs) in sets.items()}
def evalP():
    dg = bpy.context.evaluated_depsgraph_get(); m2 = ob.evaluated_get(dg).to_mesh()
    p = np.empty(len(m2.vertices) * 3); m2.vertices.foreach_get('co', p); ob.evaluated_get(dg).to_mesh_clear(); return p.reshape(-1, 3)
bones = [{'nombre': b.name, 'padre': b.parent.name if b.parent else None, 'cabeza': [round(float(x), 4) for x in (arm.matrix_world @ b.head_local)], 'deforma': bool(b.use_deform)} for b in arm.data.bones]
names = sorted({a.name for a in bpy.data.actions if not a.name.endswith('_boca') and '_boca' not in a.name and not a.name.startswith('KeyAction')})
res = {}
for nm in names:
    act = bpy.data.actions.get(nm)
    if act is None: continue
    arm.animation_data.action = act
    try: arm.animation_data.action_slot = act.slots[0]
    except Exception: pass
    for t in arm.animation_data.nla_tracks: t.mute = True
    f0, f1 = int(round(act.frame_range[0])), int(round(act.frame_range[1]))
    r = dict(cuadros=f1 - f0 + 1, dur=round((f1 - f0) / 30, 3), alarga_max_mm=0.0, alarga_p9995_mm=0.0, estira_max=1.0, pico_max_mm=0.0, pico_p9999_mm=0.0,
             vol_min=1.0, vol_max=1.0, paso_max_mm=0.0, peor_cuadro=f0)
    for k in sets: r['vol_' + k + '_min'] = 1.0; r['vol_' + k + '_max'] = 1.0
    prev = None; first = None
    for fr in range(f0, f1 + 1):
        sc.frame_set(fr)
        P = evalP()
        if first is None: first = P.copy()
        L = np.linalg.norm(P[E[:, 0]] - P[E[:, 1]], axis=1); dl = L - L0
        g = float(dl.max()) * 1000
        if g > r['alarga_max_mm']:
            r['alarga_max_mm'] = g; r['peor_cuadro'] = fr
            i_ = int(np.argmax(dl)); r['peor_arista'] = dict(mm=round(g, 1), reposo_mm=round(float(L0[i_]) * 1000, 1), pos=[round(float(x), 3) for x in P0[E[i_, 0]]],
                                                              pesos=[sorted(((k, round(float(W[k][v]), 2)) for k in W if W[k][v] > 0.01), key=lambda kv: -kv[1]) for v in E[i_]])
        r['alarga_p9995_mm'] = max(r['alarga_p9995_mm'], float(np.percentile(dl, 99.95)) * 1000)
        r['estira_max'] = max(r['estira_max'], float(np.where(L0 > 2e-4, L / np.maximum(L0, 1e-9), 1.0).max()))
        dlap = lap(P) - lap0
        r['pico_max_mm'] = max(r['pico_max_mm'], float(dlap.max()) * 1000); r['pico_p9999_mm'] = max(r['pico_p9999_mm'], float(np.percentile(dlap, 99.99)) * 1000)
        vr = vol(P, F) / V0 if abs(V0) > 1e-9 else 1.0
        r['vol_min'] = min(r['vol_min'], vr); r['vol_max'] = max(r['vol_max'], vr)
        for k, (fs, vs) in sets.items():
            if len(fs) and abs(VA0[k]) > 1e-9:
                va = vol(P, fs, P[vs].mean(0)) / VA0[k]
                r['vol_' + k + '_min'] = min(r['vol_' + k + '_min'], va); r['vol_' + k + '_max'] = max(r['vol_' + k + '_max'], va)
        if prev is not None: r['paso_max_mm'] = max(r['paso_max_mm'], float(np.linalg.norm(P - prev, axis=1).max()) * 1000)
        prev = P
    r['cierre_mm'] = float(np.linalg.norm(prev - first, axis=1).max()) * 1000
    r['ciclo'] = nm in LOOPS
    res[nm] = {k: (round(v, 3) if isinstance(v, float) else v) for k, v in r.items()}
attrs = sorted(a.name for a in me.attributes if not a.name.startswith('.'))
info = {'vertices': nv, 'caras': len(me.polygons), 'aristas': len(E), 'huesos': len(arm.data.bones), 'materiales': [m.name for m in me.materials],
        'uv': [u.name for u in me.uv_layers], 'formas': [k.name for k in me.shape_keys.key_blocks] if me.shape_keys else [], 'atributos': attrs,
        'influencias_max': int(ninf.max()), 'sin_peso': int((ninf == 0).sum()), 'caja_min': [round(float(x), 4) for x in P0.min(0)], 'caja_max': [round(float(x), 4) for x in P0.max(0)],
        'volumen_reposo_l': round(V0 * 1000, 3), 'reposo_vs_malla_mm': round(rest_err, 3), 'objetos_malla': sorted(o.name for o in bpy.data.objects if o.type == 'MESH')}
json.dump({'info': info, 'huesos': bones, 'clips': res}, open(out, 'w'), indent=1, ensure_ascii=False)
print('QA23', glb.split('/')[-1], json.dumps({k: info[k] for k in ('vertices', 'caras', 'huesos', 'influencias_max', 'sin_peso', 'reposo_vs_malla_mm', 'materiales', 'formas')}, ensure_ascii=False))
for nm, r in res.items():
    print(f"  {nm:10s} cuadros {r['cuadros']:3d} | alarga máx {r['alarga_max_mm']:6.1f} mm (p99,95 {r['alarga_p9995_mm']:5.1f}) | pico {r['pico_max_mm']:5.1f} mm | vol {r['vol_min']:.3f}-{r['vol_max']:.3f}"
          f" | brazos {r.get('vol_brazoL_min', 1):.2f}-{r.get('vol_brazoL_max', 1):.2f} / {r.get('vol_brazoR_min', 1):.2f}-{r.get('vol_brazoR_max', 1):.2f} | paso {r['paso_max_mm']:5.1f} | cierre {r['cierre_mm']:5.1f}{' (ciclo)' if r['ciclo'] else ''}")
    pa = r.get('peor_arista')
    if pa and pa['mm'] > 15: print(f"      peor arista: +{pa['mm']} mm (reposo {pa['reposo_mm']}) en {pa['pos']} pesos {pa['pesos']}")
