# Animaciones v2 para el esqueleto nuevo (Blender 5.2, bpy) y exportación a GLB.
# Movimientos suaves: curvas senoidales y de facilidad (sin saltos), acción secundaria en cola y orejas (con desfase)
# y brazos dentro de ángulos que la malla aguanta sin deformarse.
# Uso: python3 anim2.py -- <rig.blend> <salida.glb> <salida_anim.blend>
import bpy, sys, math
from mathutils import Euler, Vector, Matrix

src, out_glb, out_blend = sys.argv[-3], sys.argv[-2], sys.argv[-1]
bpy.ops.wm.open_mainfile(filepath=src)
sc = bpy.context.scene
FPS = 30
sc.render.fps = FPS
armob = [o for o in bpy.data.objects if o.type == 'ARMATURE'][0]
ob = [o for o in bpy.data.objects if o.type == 'MESH'][0]
me = ob.data
sk = me.shape_keys.key_blocks['MouthOpen']
pbs = armob.pose.bones
rest = {b.name: b.bone.matrix_local.to_3x3() for b in pbs}
for pb in pbs: pb.rotation_mode = 'QUATERNION'
HAS = set(pbs.keys())
TAIL = [n for n in ('Tail', 'Tail2', 'Tail3') if n in HAS]
EARS = 'EarL' in HAS
PI2 = 2 * math.pi

def rot(name, rx=0.0, ry=0.0, rz=0.0):
    """Rotación en ejes del mundo, grados. rx>0 baja la cabeza (adelante); ry>0 inclina hacia +X (izquierda de la
    mascota); rz>0 gira hacia +X. Para el brazo derecho, ry>0 lo abre hacia afuera; para el izquierdo, ry<0."""
    if name not in HAS: return
    Rw = Euler((math.radians(rx), math.radians(ry), math.radians(rz)), 'XYZ').to_matrix()
    pbs[name].rotation_quaternion = (rest[name].inverted() @ Rw @ rest[name]).to_quaternion()

def loc(name, dx=0.0, dy=0.0, dz=0.0):
    if name not in HAS: return
    pbs[name].location = rest[name].inverted() @ Vector((dx, dy, dz))

def scl(name, sx=1.0, sy=1.0, sz=1.0):
    if name not in HAS: return
    v = rest[name].inverted() @ Vector((sx, sy, sz))
    pbs[name].scale = Vector((abs(v.x), abs(v.y), abs(v.z)))

def reset():
    for pb in pbs:
        pb.rotation_quaternion = (1, 0, 0, 0); pb.location = (0, 0, 0); pb.scale = (1, 1, 1)

def s(f, t, ph=0.0):  # senoide de frecuencia f (Hz)
    return math.sin(PI2 * (f * t + ph))

def c01(f, t, ph=0.0):  # 0..1 suave (coseno elevado)
    return 0.5 - 0.5 * math.cos(PI2 * (f * t + ph))

def ease(a):
    a = max(0.0, min(1.0, a)); return a * a * (3 - 2 * a)

def env(t, D, a=0.25, b=0.25):  # sube al inicio y baja al final (para clips de una vez)
    return ease(t / a) * ease((D - t) / b)

def arm_side(side, out=0.0, fwd=0.0, hand_out=0.0, hand_fwd=0.0, twist=0.0):
    """out: abrir hacia el costado; fwd: hacia adelante (negativo = atrás). Mano relativa."""
    sg = 1 if side == 'L' else -1
    rot('Arm' + side, -fwd, -sg * out, sg * twist)
    rot('Hand' + side, -hand_fwd, -sg * hand_out, 0)

def tail_wag(t, amp=12.0, f=0.8, lift=0.0):
    for i, n in enumerate(TAIL):
        rot(n, -lift * (1 + i * 0.3), 0, amp * (0.6 + 0.4 * i) * s(f, t, -0.12 * i))

def ears(t, perk=0.0, droop=0.0, twitch=0.0):
    if not EARS: return
    # perk: hacia adentro/arriba; droop: hacia afuera/abajo
    rot('EarL', droop * 0.5, -(droop - perk) * 0.7 - twitch, 0)
    rot('EarR', droop * 0.5, (droop - perk) * 0.7, 0)

def breathe(t, f=0.3, amp=0.012):
    b = s(f, t)
    scl('Chest', 1 + amp * 0.5 * b, 1 + amp * 0.5 * b, 1 + amp * b)
    return b

# ---------------------------------------------------------------- clips
def clip_idle(t, D):
    f = 1.0 / D  # un ciclo por clip
    b = breathe(t, f)
    rot('Hips', 0, 1.2 * s(f, t, 0.25), 1.5 * s(f, t))
    rot('Chest', 1.0 * b, 0, 0)
    rot('Head', 2.0 * s(f, t, 0.15), 3.0 * s(f, t, 0.4), 4.0 * s(f, t, 0.1))
    # v0.22: brazos casi en reposo (el brazo es la pieza original del modelo; quieto se ve entero)
    arm_side('L', out=1.2 + 1.0 * s(f, t), fwd=1.0 * s(f, t, 0.3), hand_out=1.5 * s(f, t, 0.2))
    arm_side('R', out=1.2 + 1.0 * s(f, t, 0.5), fwd=1.0 * s(f, t, 0.8), hand_out=1.5 * s(f, t, 0.7))
    tail_wag(t, 10, 2 * f)
    tw = math.exp(-((t - D * 0.62) / 0.06) ** 2)  # una sacudida de oreja
    ears(t, perk=2 * s(f, t), twitch=10 * tw)
    loc('Hips', 0, 0, 0.002 * b)
    return 0.0

def clip_talk(t, D):
    f = 1.0 / D
    m = (0.15 + 0.85 * c01(4 * f, t)) * (0.7 + 0.3 * c01(f, t, 0.3))
    rot('Head', 3 * s(2 * f, t), 2 * s(f, t, 0.2), 4 * s(f, t))
    rot('Chest', 1.5 * s(2 * f, t, 0.1), 0, 2 * s(f, t))
    arm_side('L', out=10 + 6 * c01(f, t), fwd=10 * c01(f, t, 0.25), hand_fwd=12 * c01(f, t, 0.3))
    arm_side('R', out=8 + 4 * c01(f, t, 0.5), fwd=6 * c01(f, t, 0.75), hand_fwd=8 * c01(f, t, 0.8))
    tail_wag(t, 10, 2 * f)
    ears(t, perk=4)
    breathe(t, f)
    return m

def clip_talk_body(t, D):
    f = 1.0 / D
    rot('Head', 3 * s(2 * f, t), 2 * s(f, t, 0.2), 5 * s(f, t))
    rot('Chest', 1.5 * s(2 * f, t, 0.1), 0, 3 * s(f, t))
    arm_side('L', out=12 + 8 * c01(f, t), fwd=14 * c01(f, t, 0.25), hand_fwd=18 * c01(f, t, 0.3), hand_out=6 * s(2 * f, t))
    arm_side('R', out=8 + 5 * c01(f, t, 0.5), fwd=8 * c01(f, t, 0.75), hand_fwd=10 * c01(f, t, 0.8))
    tail_wag(t, 12, 2 * f)
    ears(t, perk=4)
    breathe(t, f)
    return 0.0

def clip_happy(t, D):
    f = 2.0 / D  # dos saltitos
    hop = c01(f, t, -0.25)  # 0 en el suelo
    land = max(0.0, math.cos(PI2 * f * t)) ** 4  # aplastado al tocar el suelo
    loc('Hips', 0, 0, 0.045 * hop)
    scl('Hips', 1 + 0.05 * land, 1 + 0.05 * land, 1 - 0.07 * land + 0.03 * hop)
    rot('Head', -5 - 3 * hop, 7 * s(f / 2, t), 5 * s(f / 2, t, 0.25))
    rot('Chest', -3 * hop, 3 * s(f / 2, t), 0)
    arm_side('L', out=30 + 18 * hop, fwd=8, hand_out=20 + 15 * hop)
    arm_side('R', out=30 + 18 * hop, fwd=8, hand_out=20 + 15 * hop)
    rot('LegL', -10 * land, 0, 0); rot('LegR', -10 * land, 0, 0)
    tail_wag(t, 24, 2 * f)
    ears(t, perk=8 + 4 * hop)
    return 0.3 + 0.2 * hop

def clip_sad(t, D):
    f = 1.0 / D
    b = s(f, t)
    rot('Head', 16 + 2 * b, 0, 4 * s(f, t, 0.3))
    rot('Chest', 9 + 1.5 * b, 0, 0)
    rot('Hips', 2, 0, 0)
    arm_side('L', out=2, fwd=6 + 2 * b, hand_fwd=4)
    arm_side('R', out=2, fwd=6 + 2 * b, hand_fwd=4)
    for i, n in enumerate(TAIL): rot(n, 18 + 6 * i, 0, 4 * s(f, t, -0.1 * i))
    ears(t, droop=22 + 3 * b)
    scl('Chest', 1 - 0.01 * b, 1 - 0.01 * b, 1 - 0.02 * b)
    loc('Hips', 0, 0, -0.008)
    return 0.0

def clip_surprised(t, D):
    k = ease(t / 0.18) * (1 - 0.35 * ease((t - 0.35) / 0.5))
    jump = math.sin(math.pi * min(1.0, t / 0.35)) if t < 0.35 else 0.0
    loc('Hips', 0, 0.01 * k, 0.03 * jump)
    rot('Head', -10 * k, 0, 0)
    rot('Chest', -6 * k, 0, 0)
    arm_side('L', out=38 * k, fwd=18 * k, hand_out=20 * k)
    arm_side('R', out=38 * k, fwd=18 * k, hand_out=20 * k)
    for i, n in enumerate(TAIL): rot(n, -15 * k, 0, 0)
    ears(t, perk=12 * k)
    return 0.8 * k

def clip_angry(t, D):
    f = 2.0 / D
    stomp = max(0.0, math.sin(PI2 * f * t)) ** 3
    loc('Hips', 0, 0, -0.012 * stomp)
    rot('Head', 6, 0, 6 * s(f, t))
    rot('Chest', -4, 0, 0)
    scl('Chest', 1.03, 1.03, 1.02)
    arm_side('L', out=24, fwd=-8, hand_out=-30, hand_fwd=10)
    arm_side('R', out=24, fwd=-8, hand_out=-30, hand_fwd=10)
    rot('LegL', -14 * stomp * (s(f / 2, t) > 0), 0, 0); rot('LegR', -14 * stomp * (s(f / 2, t) <= 0), 0, 0)
    for i, n in enumerate(TAIL): rot(n, -10, 0, 18 * s(2 * f, t, -0.1 * i))
    ears(t, droop=10)
    return 0.12

def clip_think(t, D):
    f = 1.0 / D
    tap = c01(2 * f, t)
    rot('Head', -6, 9, 7 + 2 * s(f, t))
    rot('Chest', 0, 3, 3)
    arm_side('R', out=12, fwd=70, hand_fwd=42 + 6 * tap, hand_out=-10)
    arm_side('L', out=8, fwd=20, hand_fwd=30, hand_out=-35)
    tail_wag(t, 8, f)
    ears(t, perk=3, twitch=6 * c01(f, t, 0.5))
    breathe(t, f)
    return 0.0

def clip_sleep(t, D):
    f = 1.0 / D
    b = s(f, t)
    rot('Head', 22 + 2 * b, 10, 0)
    rot('Chest', 8 + 2 * b, 2, 0)
    scl('Chest', 1 + 0.012 * b, 1 + 0.012 * b, 1 + 0.025 * b)
    arm_side('L', out=2, fwd=4); arm_side('R', out=2, fwd=4)
    for i, n in enumerate(TAIL): rot(n, 10 + 4 * i, 0, 10 + 6 * i)
    ears(t, droop=14)
    loc('Hips', 0, 0, -0.006 + 0.002 * b)
    return 0.0

def clip_run(t, D):
    f = 1.0 / D
    st = s(f, t)
    loc('Hips', 0, 0, 0.018 * c01(2 * f, t, 0.25))
    rot('Hips', -6, 0, 4 * st)
    rot('Chest', -4, 0, -5 * st)
    rot('Head', 4, 0, 3 * st)
    rot('LegL', 30 * st, 0, 0); rot('LegR', -30 * st, 0, 0)
    rot('FootL', 10 * max(0, st), 0, 0); rot('FootR', 10 * max(0, -st), 0, 0)
    arm_side('L', out=12, fwd=-35 * st, hand_fwd=-20 * st)
    arm_side('R', out=12, fwd=35 * st, hand_fwd=20 * st)
    tail_wag(t, 14, 2 * f, lift=8)
    ears(t, droop=8)
    return 0.15

def clip_walk(t, D):
    f = 1.0 / D
    st = s(f, t)
    loc('Hips', 0, 0, 0.008 * c01(2 * f, t, 0.25))
    rot('Hips', 0, 2 * st, 3 * st)
    rot('Head', 0, -2 * st, -2 * st)
    rot('LegL', 18 * st, 0, 0); rot('LegR', -18 * st, 0, 0)
    arm_side('L', out=6, fwd=-18 * st, hand_fwd=-8 * st)
    arm_side('R', out=6, fwd=18 * st, hand_fwd=8 * st)
    tail_wag(t, 10, 2 * f)
    ears(t, perk=2)
    return 0.0

def clip_jump(t, D):
    # anticipación, salto, aterrizaje con aplastamiento
    u = t / D
    crouch = ease(u / 0.2) * (1 - ease((u - 0.2) / 0.08)) + ease((u - 0.72) / 0.08) * (1 - ease((u - 0.82) / 0.18))
    air = math.sin(math.pi * max(0.0, min(1.0, (u - 0.25) / 0.5)))
    loc('Hips', 0, 0, -0.03 * crouch + 0.12 * air)
    scl('Hips', 1 + 0.06 * crouch, 1 + 0.06 * crouch, 1 - 0.08 * crouch + 0.04 * air)
    rot('Head', -6 * air + 6 * crouch, 0, 0)
    arm_side('L', out=20 + 45 * air, fwd=-15 * crouch + 15 * air, hand_out=20 * air)
    arm_side('R', out=20 + 45 * air, fwd=-15 * crouch + 15 * air, hand_out=20 * air)
    rot('LegL', -18 * crouch + 10 * air, 0, 0); rot('LegR', -18 * crouch + 10 * air, 0, 0)
    for i, n in enumerate(TAIL): rot(n, 15 * air - 10 * crouch, 0, 0)
    ears(t, perk=10 * air, droop=6 * crouch)
    return 0.3 * air

def clip_celebrate(t, D):
    f = 2.0 / D
    hop = c01(f, t, -0.25)
    land = max(0.0, math.cos(PI2 * f * t)) ** 4
    wave = s(2 * f, t)
    loc('Hips', 0, 0, 0.06 * hop)
    scl('Hips', 1 + 0.05 * land, 1 + 0.05 * land, 1 - 0.07 * land + 0.03 * hop)
    rot('Head', -8, 4 * s(f / 2, t), 6 * s(f / 2, t, 0.25))
    arm_side('L', out=52 + 8 * wave, fwd=12, hand_out=34 + 12 * wave)
    arm_side('R', out=52 - 8 * wave, fwd=12, hand_out=34 - 12 * wave)
    rot('LegL', -10 * land, 0, 0); rot('LegR', -10 * land, 0, 0)
    tail_wag(t, 26, 2 * f)
    ears(t, perk=10)
    return 0.45 + 0.2 * hop

def clip_wave(t, D):
    up = env(t, D, 0.35, 0.4)
    w = s(2.2, t)
    rot('Head', -3 * up, -6 * up, -7 * up)
    rot('Chest', 0, -3 * up, -3 * up)
    arm_side('R', out=62 * up, fwd=12 * up, hand_out=(44 + 24 * w) * up, twist=-10 * up)
    arm_side('L', out=5, fwd=3)
    loc('Hips', 0, 0, 0.006 * up * c01(2.2, t))
    tail_wag(t, 16 * (0.4 + up), 1.5)
    ears(t, perk=6 * up)
    return 0.25 * up

def clip_yes(t, D):
    k = env(t, D, 0.12, 0.2)
    rot('Head', 13 * s(2.0 / D, t) * k, 0, 0)
    rot('Chest', 3 * s(2.0 / D, t, -0.1) * k, 0, 0)
    tail_wag(t, 12, 2.0)
    ears(t, perk=4)
    return 0.15 * k

def clip_no(t, D):
    k = env(t, D, 0.12, 0.2)
    rot('Head', 0, 0, 16 * s(2.0 / D, t) * k)
    rot('Chest', 0, 0, -3 * s(2.0 / D, t, -0.1) * k)
    tail_wag(t, 8, 1.0)
    ears(t, droop=4)
    return 0.0

def clip_dance(t, D):
    f = 1.0 / D
    st = s(2 * f, t)
    bob = c01(4 * f, t)
    loc('Hips', 0.015 * st, 0, 0.012 * bob)
    rot('Hips', 0, 6 * st, 0)
    rot('Chest', 0, -5 * st, 5 * s(f, t))
    rot('Head', -3, 8 * s(2 * f, t, 0.15), 6 * s(f, t, 0.25))
    arm_side('L', out=30 + 25 * c01(2 * f, t), fwd=10, hand_out=25 * c01(2 * f, t, 0.1))
    arm_side('R', out=30 + 25 * c01(2 * f, t, 0.5), fwd=10, hand_out=25 * c01(2 * f, t, 0.6))
    rot('LegL', -12 * max(0, st), 0, 0); rot('LegR', -12 * max(0, -st), 0, 0)
    tail_wag(t, 22, 2 * f)
    ears(t, perk=6, twitch=6 * c01(4 * f, t))
    return 0.2 * bob

CLIPS = [('idle', 4.0, clip_idle), ('talk', 2.0, clip_talk), ('talk_body', 2.0, clip_talk_body), ('happy', 2.0, clip_happy),
         ('sad', 4.0, clip_sad), ('surprised', 1.5, clip_surprised), ('angry', 2.0, clip_angry), ('think', 4.0, clip_think),
         ('sleep', 5.0, clip_sleep), ('run', 0.6, clip_run), ('walk', 1.0, clip_walk), ('jump', 1.2, clip_jump),
         ('celebrate', 2.0, clip_celebrate), ('wave', 2.4, clip_wave), ('yes', 1.2, clip_yes), ('no', 1.2, clip_no),
         ('dance', 2.0, clip_dance)]

# ---------------------------------------------------------------- hornear
for a in list(bpy.data.actions): bpy.data.actions.remove(a)
armob.animation_data_create(); me.shape_keys.animation_data_create()
for t in list(armob.animation_data.nla_tracks): armob.animation_data.nla_tracks.remove(t)
for t in list(me.shape_keys.animation_data.nla_tracks): me.shape_keys.animation_data.nla_tracks.remove(t)

def push(ad, action, name):
    tr = ad.nla_tracks.new(); tr.name = name
    st = tr.strips.new(name, 0, action)
    try:
        st.action_slot = action.slots[0]
    except Exception:
        pass

from mathutils import Quaternion
def rim_follow(c0=math.radians(4.0), cap=math.radians(5.0)):
    # ArmRim copia el giro del brazo hasta c0 y luego se satura suavemente en 'cap'
    for s_ in ('L', 'R'):
        if 'ArmRim' + s_ not in HAS: continue
        q = pbs['Arm' + s_].rotation_quaternion.copy(); q.normalize()
        ang = q.angle
        if ang > math.pi: ang = 2 * math.pi - ang; q = Quaternion((-q.w, -q.x, -q.y, -q.z))
        if ang < 1e-6:
            pbs['ArmRim' + s_].rotation_quaternion = (1, 0, 0, 0); continue
        a2 = ang if ang <= c0 else c0 + (cap - c0) * math.tanh((ang - c0) / (cap - c0))
        pbs['ArmRim' + s_].rotation_quaternion = Quaternion(q.axis, a2)
        pbs['ArmRim' + s_].location = pbs['Arm' + s_].location.copy()
for name, D, fn in CLIPS:
    armob.animation_data.action = None; me.shape_keys.animation_data.action = None
    n = int(round(D * FPS))
    for fr in range(n + 1):
        reset()
        m = fn(fr / FPS, D)
        rim_follow()
        for pb in pbs:
            if not pb.bone.use_deform and pb.name != 'Root': continue
            pb.keyframe_insert('rotation_quaternion', frame=fr)
            pb.keyframe_insert('location', frame=fr)
            pb.keyframe_insert('scale', frame=fr)
        sk.value = max(0.0, min(1.0, m)); sk.keyframe_insert('value', frame=fr)
    act = armob.animation_data.action; act.name = name
    sact = me.shape_keys.animation_data.action; sact.name = name + '_boca'
    push(armob.animation_data, act, name)
    push(me.shape_keys.animation_data, sact, name)
    act.use_fake_user = True; sact.use_fake_user = True
armob.animation_data.action = None; me.shape_keys.animation_data.action = None
reset(); sk.value = 0
sc.frame_start, sc.frame_end = 0, 120
bpy.ops.wm.save_as_mainfile(filepath=out_blend)

# ---------------------------------------------------------------- exportar
bpy.ops.object.select_all(action='DESELECT')
ob.select_set(True); armob.select_set(True); bpy.context.view_layer.objects.active = armob
bpy.ops.export_scene.gltf(
    filepath=out_glb, export_format='GLB', use_selection=True,
    export_animations=True, export_animation_mode='NLA_TRACKS', export_force_sampling=True,
    export_frame_step=1, export_optimize_animation_size=True,
    export_skins=True, export_def_bones=False, export_morph=True, export_morph_normal=True,
    export_image_format='AUTO', export_apply=False, export_yup=True,
    export_vertex_color='ACTIVE')
print('EXPORTADO', out_glb)
