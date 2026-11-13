# Esqueleto v2 (brazos originales recortados, v0.22) para las mascotas de StudyPlanner (Blender 5.2, bpy).
# Toma el GLB lite (malla, textura y forma MouthOpen), quita el esqueleto viejo de 8 huesos y arma uno nuevo:
# Root, Hips, Chest, Head, orejas, brazo + mano, pierna + pie y cola en cadena. Los pesos salen del calor de los
# huesos (automáticos de Blender) y luego se limpian por regiones para que ninguna parte se estire de más.
# Uso: python3 rig2.py -- <mascota> <salida.blend>
import bpy, bmesh, sys, math, json
import os as _os
import numpy as np
from mathutils import Vector, Matrix, kdtree

pet, out_blend = sys.argv[-2], sys.argv[-1]
# v0.21: modelos de alta calidad (30 000 triángulos, texturas grandes) del paquete mascotas_3d_v017.
SRC = f'/home/claude/w/rig2/src_hq/{pet}.glb' if pet != 'conejo' else '/home/claude/w/rig2/src_hq/conejo.glb'

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=SRC)
ob = [o for o in bpy.data.objects if o.type == 'MESH' and o.data.shape_keys and 'MouthOpen' in o.data.shape_keys.key_blocks][0]
old = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
for o in list(bpy.data.objects):
    if o.type == 'MESH' and o is not ob:
        bpy.data.objects.remove(o)

# Datos del esqueleto viejo y regiones viejas (solo como referencia de alturas).
ob_world = ob.matrix_world.copy()
oldb = {b.name: ((old.matrix_world @ b.head_local), (old.matrix_world @ b.tail_local)) for b in old.data.bones}
me = ob.data
nv = len(me.vertices)
W0 = {g.name: np.zeros(nv) for g in ob.vertex_groups}
gidx = {g.index: g.name for g in ob.vertex_groups}
for v in me.vertices:
    for g in v.groups:
        W0[gidx[g.group]][v.index] = g.weight

# Malla en coordenadas del mundo (pose de reposo).
ob.parent = None
ob.matrix_world = ob_world
for m in list(ob.modifiers):
    ob.modifiers.remove(m)
ob.vertex_groups.clear()
bpy.data.objects.remove(old)
for a in list(bpy.data.actions):
    bpy.data.actions.remove(a)
if ob.data.shape_keys.animation_data:
    ob.data.shape_keys.animation_data_clear()
if ob.animation_data:
    ob.animation_data_clear()
bpy.context.view_layer.update()
bpy.ops.object.select_all(action='DESELECT')
ob.select_set(True); bpy.context.view_layer.objects.active = ob
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
# Las costuras de UV del GLB separan vértices en la misma posición: se vuelven a unir para que la malla sea
# continua (las UV quedan en las esquinas de las caras, no se pierden).
bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.mesh.remove_doubles(threshold=0.0001); bpy.ops.object.mode_set(mode='OBJECT')
CFG0 = json.load(open('/home/claude/w/rig4/cfg4.json'))[pet]
if CFG0.get('zip', True):
    # v0.23: la malla de origen llega partida en parches con los bordes sin unir (decenas de metros de borde abierto);
    # se cosen antes de armar el esqueleto para que ninguna deformación pueda abrir esas costuras.
    sys.path.insert(0, '/home/claude/w/rig4')
    import zip23
    bm_ = bmesh.new(); bm_.from_mesh(me)
    zip23.zip_seams(bm_, CFG0.get('zip_tol', 0.0005), CFG0.get('zip_tol', 0.0005), tag=pet)
    zip23.zip_seams(bm_, CFG0.get('zip_tol2', 0.0012), CFG0.get('zip_tol2', 0.0012), tag=pet)
    bm_.normal_update(); zip23.remove_fins(bm_, tag=pet)
    zip23.fill_small_holes(bm_, tag=pet)
    bm_.to_mesh(me); bm_.free(); me.update()
nv = len(me.vertices)
P = np.array([v.co[:] for v in me.vertices])
x, y, z = P[:, 0], P[:, 1], P[:, 2]

hip_z = oldb['Hips'][0].z
neck_z = oldb['Head'][0].z
sh = oldb['ArmL'][0]
arm_x0, sh_z = abs(sh.x), sh.z
tail_root = oldb['Tail'][0] if 'Tail' in oldb else Vector((0, 0.2, 0.25))

CFG = json.load(open('/home/claude/w/rig4/cfg4.json'))[pet]
def sstep_np(t):
    t = np.clip(t, 0, 1); return t * t * (3 - 2 * t)

# ---- regiones geométricas
head_mask = z > neck_z + 0.02
# cola: por detrás del torso y por debajo del cuello; se mide la conectividad desde la raíz.
bm = bmesh.new(); bm.from_mesh(me); bm.verts.ensure_lookup_table()
adj = [[] for _ in range(nv)]
for e in bm.edges:
    a, b = e.verts[0].index, e.verts[1].index
    adj[a].append(b); adj[b].append(a)
# piezas sueltas
comp = -np.ones(nv, int); parts = []
for s in range(nv):
    if comp[s] >= 0: continue
    st = [s]; comp[s] = len(parts); lst = []
    while st:
        u = st.pop(); lst.append(u)
        for w in adj[u]:
            if comp[w] < 0:
                comp[w] = len(parts); st.append(w)
    parts.append(np.array(lst))

torso_back = CFG.get('torso_back', 0.15)
order = sorted(range(len(parts)), key=lambda i: -len(parts[i]))
pack_mask = np.zeros(nv, bool)
for i in order[1:]:
    c = P[parts[i]].mean(0)
    if len(parts[i]) > 300 and c[1] > 0.08 and hip_z - 0.1 < c[2] < neck_z + 0.08:
        pack_mask[parts[i]] = True
# primera estimación de la cola (se afina con los pesos automáticos)
tail_guess = (y > torso_back) & (z < neck_z - 0.04) & ~pack_mask
# ---- esqueleto nuevo
arm = bpy.data.armatures.new(pet + '_rig')
armob = bpy.data.objects.new(pet + '_rig', arm)
bpy.context.scene.collection.objects.link(armob)
bpy.ops.object.select_all(action='DESELECT')
bpy.context.view_layer.objects.active = armob; armob.select_set(True)
bpy.ops.object.mode_set(mode='EDIT')
def add(name, h, t, parent=None, deform=True):
    b = arm.edit_bones.new(name); b.head = Vector(h); b.tail = Vector(t)
    if parent: b.parent = arm.edit_bones[parent]
    b.use_deform = deform
    return b
chest_z = hip_z + (neck_z - hip_z) * 0.45
add('Root', (0, 0, 0), (0, -0.12, 0), deform=False)
add('Hips', (0, 0, hip_z), (0, 0, chest_z), 'Root')
add('Chest', (0, 0, chest_z), (0, 0, neck_z), 'Hips')
add('Head', (0, 0, neck_z), (0, 0, neck_z + 0.3), 'Chest')
arm_y = CFG.get('arm_y', 0.0)
hand_z = CFG.get('hand_z', hip_z + 0.03)
for s, sg in (('L', 1), ('R', -1)):
    sx = sg * arm_x0
    ex = sg * (arm_x0 + CFG.get('arm_out', 0.045))
    ez = (sh_z + hand_z) / 2 + 0.01
    add('Arm' + s, (sx, arm_y, sh_z), (ex, arm_y, ez), 'Chest')
    add('Hand' + s, (ex, arm_y, ez), (sg * (arm_x0 + CFG.get('arm_out', 0.045) * 1.5), arm_y - 0.01, hand_z), 'Arm' + s)
    lx = sg * CFG.get('leg_x', 0.08)
    add('Leg' + s, (lx, 0, hip_z), (lx, 0, 0.10), 'Hips')
    add('Foot' + s, (lx, 0, 0.10), (lx, -0.07, 0.03), 'Leg' + s)
    if CFG.get('ears'):
        tip = Vector(CFG['ear' + s])
        base = Vector((tip.x * 0.72, tip.y * 0.8, tip.z - CFG.get('ear_len', 0.16)))
        add('Ear' + s, base, tip, 'Head')
if CFG.get('tail', True):
    if CFG.get('tail_chain', True):
        T = P[tail_guess & (np.linalg.norm(P - np.array(tail_root[:]), axis=1) > 0.02)]
        r0 = np.array(tail_root[:])
        d = np.linalg.norm(T - r0, axis=1)
        qs = np.quantile(d, [0.0, 0.33, 0.66, 0.98])
        pts = [r0]
        for k in range(1, 4):
            sel = (d >= qs[k - 1]) & (d <= qs[k])
            pts.append(T[sel].mean(0) if sel.any() else pts[-1] + np.array([0, 0.05, 0]))
        # v0.23: la cadena de la cola queda EXACTAMENTE donde estaba en 0.22.2 (la malla cosida tiene otros vértices y
        # la medida por cuantiles se corría unos milímetros): el reposo de los huesos que no son del brazo no cambia.
        try:
            _hb = json.load(open('/home/claude/w/rig4/huesos_v0222.json')).get(pet, {})
            if CFG.get('tail_fijo', True) and all(k in _hb for k in ('Tail', 'Tail2', 'Tail3')):
                pts = [np.array(_hb['Tail'][0]), np.array(_hb['Tail2'][0]), np.array(_hb['Tail3'][0]), np.array(_hb['Tail3'][1])]
        except Exception as _e:
            print('sin huesos de referencia', _e)
        # el primer punto: raíz pegada al cuerpo
        names = ['Tail', 'Tail2', 'Tail3']
        par = 'Hips'
        for k in range(3):
            add(names[k], pts[k], pts[k + 1], par); par = names[k]
    else:
        add('Tail', tail_root, (tail_root.x, tail_root.y + 0.08, tail_root.z + 0.03), 'Hips')
bpy.ops.object.mode_set(mode='OBJECT')
bones = [b.name for b in arm.bones if b.use_deform]

# ---- pesos automáticos (calor de los huesos)
bpy.ops.object.select_all(action='DESELECT')
ob.select_set(True); armob.select_set(True); bpy.context.view_layer.objects.active = armob
bpy.ops.object.parent_set(type='ARMATURE_AUTO')
Wm = {b: np.zeros(nv) for b in bones}
gidx = {g.index: g.name for g in ob.vertex_groups}
for v in me.vertices:
    for g in v.groups:
        n = gidx[g.group]
        if n in Wm: Wm[n][v.index] = g.weight

# ---- brazos nuevos: en estas figuras el brazo es un relieve pegado al costado. Se reemplaza por un brazo modelado
# (cápsula con mano redonda) que toma la textura del brazo original, y el costado del cuerpo se cierra.
if CFG.get('new_arms', True):
    exec(open('/home/claude/w/rig4/armfix.py').read())
else:
    cap_mask = np.zeros(nv, bool)

def sstep(t):
    t = np.clip(t, 0, 1); return t * t * (3 - 2 * t)

tb_names = [n for n in bones if n.startswith('Tail')]
if tb_names:
    tsum0 = sum(Wm[b] for b in tb_names)
    others = np.max(np.stack([Wm[b] for b in bones if not b.startswith('Tail')]), axis=0)
    tail_mask = (tsum0 > others) & (y > torso_back - 0.08) & (z < neck_z) & ~pack_mask
    if CFG.get('tail_side'):
        sx = CFG['tail_side']
        tail_mask |= (x * sx > arm_x0 + 0.06) & (y > torso_back - 0.02) & (z < neck_z - 0.06) & ~pack_mask
else:
    tail_mask = np.zeros(nv, bool)

# color de la textura en cada vértice (para separar piezas por color, p. ej. los audífonos de las orejas)
_vcol = None
def vertex_colors():
    global _vcol
    if _vcol is not None: return _vcol
    mat = me.materials[0]
    img = None
    for n in mat.node_tree.nodes:
        if n.type == 'TEX_IMAGE' and n.image is not None:
            for l in n.outputs['Color'].links:
                if l.to_socket.name == 'Base Color': img = n.image
    w, h = img.size
    px = np.empty(w * h * 4, np.float32); img.pixels.foreach_get(px); px = px.reshape(h, w, 4)
    uv = np.zeros((len(me.loops), 2)); me.uv_layers.active.data.foreach_get('uv', uv.ravel())
    vi = np.zeros(len(me.loops), int); me.loops.foreach_get('vertex_index', vi)
    acc = np.zeros((nv, 3)); cnt = np.zeros(nv)
    xs = np.clip((uv[:, 0] % 1) * (w - 1), 0, w - 1).astype(int); ys = np.clip((uv[:, 1] % 1) * (h - 1), 0, h - 1).astype(int)
    np.add.at(acc, vi, px[ys, xs, :3]); np.add.at(cnt, vi, 1)
    _vcol = acc / np.maximum(cnt, 1)[:, None]
    return _vcol
def hue_mask(h0, h1, smin=0.35):
    import colorsys
    c = vertex_colors()
    hs = np.array([colorsys.rgb_to_hsv(*cc) for cc in c])
    hue = hs[:, 0] * 360
    return (hue >= h0) & (hue <= h1) & (hs[:, 1] >= smin)

# ---- limpieza por regiones
armside = {}
for s, sg in (('L', 1), ('R', -1)):
    # el brazo solo mueve lo que está a su lado, entre la cadera y el cuello, y nunca la cola
    allow = sstep((x * sg - (arm_x0 - 0.05)) / 0.05) * sstep((neck_z + 0.01 - z) / 0.03) * (~tail_mask)
    for b in ('Arm' + s, 'Hand' + s):
        Wm[b] *= np.where(cap_mask, 1.0, 0.0 if CFG.get('new_arms', True) else allow)
    leg_allow = sstep((x * sg + 0.02) / 0.04) * sstep((hip_z + 0.04 - z) / 0.05) * (~tail_mask)
    for b in ('Leg' + s, 'Foot' + s):
        Wm[b] *= leg_allow
    if CFG.get('ears'):
        e = 'Ear' + s
        if CFG.get('ear_exclude_hue') is not None:
            Wm[e] *= ~hue_mask(*CFG['ear_exclude_hue'])
        base_z = Vector(CFG['ear' + s]).z - CFG.get('ear_len', 0.16)
        Wm[e] *= sstep((z - base_z) / 0.04) * sstep((x * sg - 0.02) / 0.04)
for b in [n for n in bones if n.startswith('Tail')]:
    Wm[b] *= tail_mask
# la cabeza es rígida: lo que está arriba del cuello va a Head (las orejas conservan lo suyo)
ear_tot = sum(Wm[n] for n in bones if n.startswith('Ear')) if CFG.get('ears') else np.zeros(nv)
hz = sstep((z - neck_z + 0.01) / 0.05) * (~tail_mask) * (~cap_mask)
for b in bones:
    if b == 'Head' or b.startswith('Ear'):
        continue
    Wm[b] *= (1 - hz)
Wm['Head'] = np.maximum(Wm['Head'], hz * (1 - np.clip(ear_tot, 0, 1)))
# la cola entera sigue a su cadena; donde el calor no llegó, se usa la distancia a la raíz
if CFG.get('tail', True):
    tb = [n for n in bones if n.startswith('Tail')]
    tsum = sum(Wm[b] for b in tb)
    miss = tail_mask & (tsum < 0.2)
    if miss.any():
        Wm[tb[0]][miss] = 1.0
    for b in bones:
        if not b.startswith('Tail'):
            Wm[b][tail_mask] *= 0.0
    Wm['Hips'][tail_mask] += 0.0
    # raíz de la cola: una transición suave con la cadera
    near_root = tail_mask & (np.linalg.norm(P - np.array(tail_root[:]), axis=1) < 0.04)
    Wm['Hips'][near_root] = 0.5; Wm[tb[0]][near_root] = 0.5

# la mochila es rígida y va con el pecho
for b in bones:
    Wm[b][pack_mask] = 0.0
Wm['Chest'][pack_mask] = 1.0

# vértices sin peso: copian al vecino más cercano con peso
tot = sum(Wm.values())
good = np.nonzero((tot > 0.05) & ~cap_mask)[0]
kd = kdtree.KDTree(len(good))
for i in good:
    kd.insert(P[i], int(i))
kd.balance()
for i in np.nonzero(tot <= 0.05)[0]:
    co, j, dist = kd.find(P[i])
    for b in bones:
        Wm[b][i] = Wm[b][j]
PARIDAD = False
if CFG.get('pesos_0222', True) and _os.path.exists(f'/home/claude/w/rig4/ref0222/{pet}.npz') and 'cap_mask' in globals() and 'plug_mask' in globals():
    exec(open('/home/claude/w/rig4/paridad23.py').read())
if 'SMOOTH' in globals() and not PARIDAD: SMOOTH()
if 'WELD' in globals(): WELD()
tot = sum(Wm.values()); tot[tot == 0] = 1
ob.vertex_groups.clear()
for b in bones:
    vg = ob.vertex_groups.new(name=b)
    wn = Wm[b] / tot
    for i in np.nonzero(wn > 0.002)[0]:
        vg.add([int(i)], float(wn[i]), 'REPLACE')
# a lo sumo 4 huesos por vértice (glTF)
bpy.ops.object.select_all(action='DESELECT')
ob.select_set(True); bpy.context.view_layer.objects.active = ob
bpy.ops.object.vertex_group_limit_total(group_select_mode='ALL', limit=4)
bpy.ops.object.vertex_group_normalize_all(group_select_mode='ALL', lock_active=False)
stats = {b: int(((Wm[b] / tot) > 0.5).sum()) for b in bones}
print('RIG2', pet, 'tail_verts', int(tail_mask.sum()), 'pack', int(pack_mask.sum()), stats)
# ---- zonas para personalizar (color por vértice, 4 canales): la app recolorea o esconde cada pieza por separado
# aunque dos piezas tengan el mismo tono (p. ej. la chaqueta y los audífonos del zorro).
ZM = CFG.get('zones', {})
def region(name):
    torso = (z > hip_z - 0.05) & (z < neck_z + 0.03) & ~pack_mask
    if name == 'torso': return torso | cap_mask
    if name == 'torso_alto': return ((z > hip_z - 0.05) & (z < neck_z + 0.12) & ~pack_mask) | cap_mask
    if name == 'pack': return pack_mask
    if name == 'pies': return (z < 0.12) & ~pack_mask & ~cap_mask
    if name == 'cabeza': return (z > neck_z - 0.02) & ~pack_mask & ~cap_mask
    if name == 'cima': return (z > CFG.get('cima_z', 0.82)) & ~pack_mask
    return np.zeros(nv, bool)
mask = np.zeros((nv, 4), np.float32)
for ch, name in enumerate(ZM.get('channels', [])):
    if name: mask[:, ch] = region(name).astype(np.float32)
attr = me.color_attributes.new(name='zmask', type='FLOAT_COLOR', domain='POINT')
attr.data.foreach_set('color', mask.ravel())
me.color_attributes.active_color = attr
me.color_attributes.render_color_index = me.color_attributes.find('zmask')
print('ZONAS', pet, ZM.get('channels'), [int(mask[:, i].sum()) for i in range(4)])
# ---- forro: caras traseras solo donde hay bordes abiertos de verdad (correas, dobladillos, cuello de la ropa)
if CFG.get('forro', True) and not _os.environ.get('SIN_FORRO'):
    exec(open('/home/claude/w/rig4/forro23.py').read())
bpy.ops.wm.save_as_mainfile(filepath=out_blend)
