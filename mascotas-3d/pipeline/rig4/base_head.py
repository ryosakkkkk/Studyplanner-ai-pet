# Esqueleto v2 para las mascotas de StudyPlanner (Blender 5.2, bpy).
# Toma el GLB lite (malla, textura y forma MouthOpen), quita el esqueleto viejo de 8 huesos y arma uno nuevo:
# Root, Hips, Chest, Head, orejas, brazo + mano, pierna + pie y cola en cadena. Los pesos salen del calor de los
# huesos (automáticos de Blender) y luego se limpian por regiones para que ninguna parte se estire de más.
# Uso: python3 rig2.py -- <mascota> <salida.blend>
import bpy, bmesh, sys, math, json
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
nv = len(me.vertices)
P = np.array([v.co[:] for v in me.vertices])
x, y, z = P[:, 0], P[:, 1], P[:, 2]

hip_z = oldb['Hips'][0].z
neck_z = oldb['Head'][0].z
sh = oldb['ArmL'][0]
arm_x0, sh_z = abs(sh.x), sh.z
tail_root = oldb['Tail'][0] if 'Tail' in oldb else Vector((0, 0.2, 0.25))

CFG = json.load(open('/home/claude/w/rig2/cfg.json'))[pet]
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
