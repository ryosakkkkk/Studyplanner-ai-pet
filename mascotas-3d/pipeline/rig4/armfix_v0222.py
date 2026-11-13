# Brazos v0.22 (se ejecuta dentro de rig4.py, con sus variables globales).
# En estas figuras el brazo es un relieve pegado al costado. Se recorta el brazo ORIGINAL (forma y textura del
# modelo), se le pone una tapa del lado que mira al cuerpo, y el costado del cuerpo se cierra con un parche que sigue
# la curva del cuerpo y toma el color de lo que lo rodea (pintado en un espacio libre de la textura).
import colorsys, os
from mathutils import Vector
from scipy.interpolate import RBFInterpolator
from scipy import ndimage
from scipy.spatial import cKDTree
from scipy.sparse import csr_matrix, coo_matrix
from scipy.sparse.csgraph import connected_components
from scipy.sparse.linalg import spsolve

DBG = {}
# ---------------------------------------------------------------- triángulos
bm_ = bmesh.new(); bm_.from_mesh(me)
if any(len(f.verts) != 3 for f in bm_.faces):
    bmesh.ops.triangulate(bm_, faces=[f for f in bm_.faces if len(f.verts) != 3])
    bm_.to_mesh(me); me.update()
bm_.free()

# ---------------------------------------------------------------- corte limpio en el hombro (segunda pasada)
import json as _json
PLF = f'/home/claude/w/rig4/out/plano_{pet}.json'
if CFG.get('cut_t') is not None and not os.environ.get('PASS1') and os.path.exists(PLF):
    PL = _json.load(open(PLF))
    bm_ = bmesh.new(); bm_.from_mesh(me)
    for s_, pl in PL.items():
        p0 = Vector(pl['p0']); nr = Vector(pl['n']); sh0 = Vector(pl['sh'])
        sel_f = []
        for f in bm_.faces:
            c = f.calc_center_median()
            d_ = (c - p0).dot(nr)
            if abs(d_) > 0.03: continue
            q = c - sh0; radial = (q - nr * q.dot(nr)).length
            if radial < pl['rad']: sel_f.append(f)
        ed = list({e for f in sel_f for e in f.edges}); vs = list({v for f in sel_f for v in f.verts})
        r_ = bmesh.ops.bisect_plane(bm_, geom=sel_f + ed + vs, plane_co=p0, plane_no=nr, dist=1e-6)
        print('BISECT', s_, len(sel_f), 'caras', len([g for g in r_['geom_cut'] if isinstance(g, bmesh.types.BMVert)]), 'vértices nuevos')
    bmesh.ops.triangulate(bm_, faces=[f for f in bm_.faces if len(f.verts) != 3])
    bm_.to_mesh(me); bm_.free(); me.update()
    # vuelve a leer posiciones y pesos (los vértices nuevos interpolan sus grupos)
    nv_old = nv
    nv = len(me.vertices)
    P = np.array([v.co[:] for v in me.vertices]); x, y, z = P[:, 0], P[:, 1], P[:, 2]
    gidx = {g.index: g.name for g in ob.vertex_groups}
    Wm = {b: np.zeros(nv) for b in bones}
    for v in me.vertices:
        for g in v.groups:
            n_ = gidx[g.group]
            if n_ in Wm: Wm[n_][v.index] = g.weight
    pack_mask = np.r_[pack_mask, np.zeros(nv - nv_old, bool)]
    print('NUEVOS VERTICES', nv - nv_old)

# ---------------------------------------------------------------- textura (color y relieve)
def find_img(kind):
    for n in me.materials[0].node_tree.nodes:
        if n.type == 'TEX_IMAGE' and n.image is not None:
            for l in n.outputs['Color'].links:
                if kind == 'base' and l.to_socket.name == 'Base Color': return n.image
                if kind == 'normal' and l.to_node.type == 'NORMAL_MAP': return n.image
    return None
IMG = find_img('base'); NIMG = find_img('normal')
TW, TH = IMG.size
TEX = np.empty(TW * TH * 4, np.float32); IMG.pixels.foreach_get(TEX); TEX = TEX.reshape(TH, TW, 4).copy()
if NIMG is not None:
    NW, NH = NIMG.size
    NTEX = np.empty(NW * NH * 4, np.float32); NIMG.pixels.foreach_get(NTEX); NTEX = NTEX.reshape(NH, NW, 4).copy()

nL = len(me.loops)
uvL = np.zeros((nL, 2)); me.uv_layers.active.data.foreach_get('uv', uvL.ravel())
lv = np.zeros(nL, int); me.loops.foreach_get('vertex_index', lv)
def tex_at(uv):
    xs = np.clip((uv[:, 0] % 1) * TW, 0, TW - 1).astype(int); ys = np.clip((uv[:, 1] % 1) * TH, 0, TH - 1).astype(int)
    return TEX[ys, xs, :3]
lcol = tex_at(uvL)
vcol = np.zeros((nv, 3)); vcnt = np.zeros(nv); np.add.at(vcol, lv, lcol); np.add.at(vcnt, lv, 1); vcol /= np.maximum(vcnt, 1)[:, None]

npoly = len(me.polygons)
ls_ = np.zeros(npoly, int); me.polygons.foreach_get('loop_start', ls_)
FV = lv[ls_[:, None] + np.arange(3)]
FC = P[FV].mean(1)
FCOL = lcol[ls_[:, None] + np.arange(3)].mean(1)
FN = np.cross(P[FV[:, 1]] - P[FV[:, 0]], P[FV[:, 2]] - P[FV[:, 0]])
FN /= np.maximum(np.linalg.norm(FN, axis=1), 1e-12)[:, None]
VN = np.array([v.normal[:] for v in me.vertices])

# aristas compartidas -> caras vecinas
EK = np.sort(np.stack([FV[:, [0, 1]], FV[:, [1, 2]], FV[:, [2, 0]]], 1).reshape(-1, 2), 1)
key = EK[:, 0] * nv + EK[:, 1]; fid = np.repeat(np.arange(npoly), 3)
o_ = np.argsort(key, kind='stable'); ks = key[o_]; fs = fid[o_]
same = ks[1:] == ks[:-1]
PAIRS = np.stack([fs[:-1][same], fs[1:][same]], 1); PKEY = ks[1:][same]
ADJ = csr_matrix((np.ones(2 * len(PAIRS)), (np.r_[PAIRS[:, 0], PAIRS[:, 1]], np.r_[PAIRS[:, 1], PAIRS[:, 0]])), shape=(npoly, npoly))

def comps(mask):
    idx = np.nonzero(mask)[0]
    sub = ADJ[idx][:, idx]
    n, lab = connected_components(sub, directed=False)
    return idx, lab, n

def grow(mask):
    return mask | (ADJ @ mask.astype(float) > 0)

# ---------------------------------------------------------------- eje del cuerpo y coordenadas cilíndricas
torso = (z > hip_z) & (z < neck_z) & ~pack_mask
CY = float(np.median(y[torso]))
TH_ = np.arctan2(x, -(y - CY)); RR = np.hypot(x, y - CY)
R0 = float(np.median(RR[torso]))
S_ = TH_ * R0
RAD = np.stack([x, y - CY], 1) / np.maximum(RR, 1e-6)[:, None]
OUTW = (VN[:, 0] * RAD[:, 0] + VN[:, 1] * RAD[:, 1]) > 0.15
def to3d(s, zz, r):
    th = s / R0
    return np.stack([r * np.sin(th), CY - r * np.cos(th), zz], -1)

# ---------------------------------------------------------------- espacio libre de la textura
occ = np.zeros((TH, TW), bool)
from PIL import Image as _PI, ImageDraw as _PD
_m = _PI.new('L', (TW, TH), 0); _d = _PD.Draw(_m)
for f in range(npoly):
    pts = [(uvL[l, 0] * TW, uvL[l, 1] * TH) for l in range(ls_[f], ls_[f] + 3)]
    _d.polygon(pts, fill=255)
occ = ndimage.binary_dilation(np.array(_m) > 0, iterations=8)   # filas = v (desde abajo), como image.pixels
def alloc(w, h):
    global occ
    ii = np.cumsum(np.cumsum(occ.astype(np.int32), 0), 1)
    ii = np.pad(ii, ((1, 0), (1, 0)))
    for oy in range(8, TH - h - 8, 4):
        for ox in range(8, TW - w - 8, 4):
            sm = ii[oy + h, ox + w] - ii[oy, ox + w] - ii[oy + h, ox] + ii[oy, ox]
            if sm == 0:
                occ[max(oy - 6, 0):oy + h + 6, max(ox - 6, 0):ox + w + 6] = True
                return ox, oy
    dtm = ndimage.distance_transform_cdt(~occ, metric='chessboard')
    raise RuntimeError(f'sin espacio en la textura: pedido {w}x{h}, mayor cuadrado libre ~{2 * int(dtm.max())}')
def flat_normal(ox, oy, w, h):
    if NIMG is None: return
    sx, sy = NW / TW, NH / TH
    x0, y0 = int(ox * sx) - 2, int(oy * sy) - 2; x1, y1 = int((ox + w) * sx) + 3, int((oy + h) * sy) + 3
    NTEX[max(y0, 0):y1, max(x0, 0):x1, :3] = (0.5, 0.5, 1.0)

# ---------------------------------------------------------------- un brazo
ARMF = np.zeros(npoly, int)         # 0 cuerpo, 1 brazo izq., 2 brazo der.
AXIS = {}; NEWPOS = {}; SKIN = {}
FIT = {}; PV = {}
for si, (s, sg) in enumerate((('L', 1), ('R', -1))):
    aw = Wm['Arm' + s] + Wm['Hand' + s]
    fa = aw[FV].mean(1)
    fpack = pack_mask[FV].any(1)
    cand = (fa > CFG.get('cand_w', 0.25)) & ~fpack & (FC[:, 2] < neck_z + CFG.get('cand_ztop', 0.0)) & (FC[:, 0] * sg > 0.02)
    cv = np.unique(FV[cand])
    inC = np.zeros(nv, bool); inC[cv] = True
    dC, _ = cKDTree(P[cv]).query(P, distance_upper_bound=0.07)
    ring = (dC < 0.07) & ~inC & ~pack_mask & (x * sg > 0) & OUTW & (z < neck_z + 0.07) & (z > 0.06)
    Q = np.c_[S_[ring], z[ring]]; Rv = RR[ring]
    rng = np.random.default_rng(0)
    sel = np.arange(len(Q)) if len(Q) <= 1800 else rng.choice(len(Q), 1800, replace=False)
    keep = np.ones(len(sel), bool)
    for it in range(4):
        rbf = RBFInterpolator(Q[sel][keep], Rv[sel][keep], kernel='thin_plate_spline', smoothing=CFG.get('fit_smooth', 2e-4))
        res = Rv[sel] - rbf(Q[sel])
        sd = float(np.std(res[keep]))
        keep = np.abs(res) < max(0.004, 2.5 * sd)
    resall = Rv - rbf(Q)
    print('AJUSTE', s, 'anillo', int(ring.sum()), 'dentro', int(keep.sum()), 'desv mm', round(sd * 1000, 2))
    FIT[s] = rbf
    near = (dC < 0.07) | inC
    pv = np.full(nv, np.nan); pv[near] = RR[near] - rbf(np.c_[S_[near], z[near]])
    PV[s] = pv
    pf = np.nanmean(pv[FV], 1)
    # núcleo del brazo: lo que sobresale del cuerpo
    core = cand & (pf > CFG.get('arm_p', 0.004))
    idx, lab, n = comps(core)
    sizes = np.bincount(lab, minlength=n)
    zc = np.array([FC[idx[lab == c], 2].mean() for c in range(n)])
    big = sizes.max()
    keepc = [c for c in range(n) if sizes[c] >= CFG.get('min_comp', 0.12) * big and zc[c] < sh_z + 0.02]
    if not keepc: keepc = [int(np.argmax(sizes))]
    armF = np.zeros(npoly, bool); armF[idx[np.isin(lab, keepc)]] = True
    # huecos dentro del brazo (caras rodeadas por el brazo)
    idx2, lab2, n2 = comps(~armF)
    outside = set(np.unique(lab2[np.isin(idx2, np.nonzero(~cand)[0])]).tolist())
    encl = idx2[~np.isin(lab2, list(outside))]
    encl = encl[np.nan_to_num(pf[encl], nan=-1) > -0.004]
    armF[encl] = True
    # piezas ajenas pegadas al hombro (bufanda, audífonos, chaleco): su color no está en el brazo de más abajo
    shoulder_ = np.array([sg * arm_x0, arm_y, sh_z])
    hand_ = np.array([sg * (arm_x0 + CFG.get('arm_out', 0.045) * 1.5), arm_y - 0.01, hand_z])
    u_ax = hand_ - shoulder_
    tF = np.clip(((FC - shoulder_) @ u_ax) / (u_ax @ u_ax), 0, 1)
    dAx = np.linalg.norm(FC - (shoulder_ + tF[:, None] * u_ax), axis=1)
    shoulder_ = np.array([sg * arm_x0, arm_y, sh_z])
    hand_ = np.array([sg * (arm_x0 + CFG.get('arm_out', 0.045) * 1.5), arm_y - 0.01, hand_z])
    u_ax = hand_ - shoulder_
    tF = np.clip(((FC - shoulder_) @ u_ax) / (u_ax @ u_ax), 0, 1)
    dAx = np.linalg.norm(FC - (shoulder_ + tF[:, None] * u_ax), axis=1)
    if CFG.get('alien'):
        # piezas ajenas pegadas al brazo (bufanda, audífonos, chaleco), por color: tono/saturación/valor
        hsv = np.array([colorsys.rgb_to_hsv(*c) for c in FCOL]); hue = hsv[:, 0] * 360
        alien = np.zeros(npoly, bool)
        for rule in CFG['alien']:
            h0, h1 = rule['h']
            inh = ((hue >= h0) & (hue <= h1)) if h0 <= h1 else ((hue >= h0) | (hue <= h1))
            m_ = inh & (hsv[:, 1] >= rule.get('s', 0)) & (hsv[:, 1] <= rule.get('smax', 1)) & (hsv[:, 2] >= rule.get('v', 0)) & (hsv[:, 2] <= rule.get('vmax', 1))
            if 'tmax' in rule: m_ &= tF <= rule['tmax']
            alien |= m_
        alien &= armF
        DBG['alien_' + s] = alien.copy()
        armF &= ~alien
        idx3, lab3, n3 = comps(armF)
        if n3:
            sz3 = np.bincount(lab3, minlength=n3)
            armF[:] = False; armF[idx3[np.isin(lab3, np.nonzero(sz3 >= 0.05 * sz3.max())[0])]] = True
        print('AJENO', s, int(alien.sum()))
    # corte en el hombro: lo que queda detrás del plano del hombro (perpendicular al brazo) es del cuerpo
    if CFG.get('cut_t') is not None:
        dirh = hand_ - shoulder_; dirh /= np.linalg.norm(dirh)
        tipF = np.nonzero(armF)[0][np.argmax(FC[armF] @ dirh)]
        u2 = FC[tipF] - shoulder_
        tC = ((FC - shoulder_) @ u2) / (u2 @ u2)
        cutm = armF & (tC < CFG['cut_t'])
        armF &= ~cutm
        # lejos del eje cerca de la base (membranas entre brazo y torso): se quedan en el cuerpo
        un2 = u2 / np.linalg.norm(u2)
        qq = FC - shoulder_; dline = np.linalg.norm(qq - np.outer(qq @ un2, un2), axis=1)
        mid = armF & (tC > 0.45) & (tC < 0.85)
        if mid.sum() > 20:
            rlim = float(np.percentile(dline[mid], 90)) * CFG.get('web_k', 1.3) + 0.008
            web = armF & (tC < 0.45) & (dline > rlim)
            armF &= ~web
            print('MEMBRANA', s, int(web.sum()), 'radio', round(rlim, 3))
        idx5, lab5, n5 = comps(armF)
        if n5:
            sz5 = np.bincount(lab5, minlength=n5)
            armF[:] = False; armF[idx5[np.isin(lab5, np.nonzero(sz5 >= 0.05 * sz5.max())[0])]] = True
        print('CORTE', s, int(cutm.sum()), 'punta', np.round(FC[tipF], 3))
        AXIS[s] = (shoulder_.copy(), u2.copy(), float(CFG['cut_t']))
    # base del brazo: caras vecinas del color del brazo que apenas sobresalen (el «filete» de la unión)
    sureA = armF & (pf > 0.012)
    bodyF = ~armF & ~fpack & (np.abs(np.nan_to_num(pf, nan=1)) < 0.006) & (FC[:, 0] * sg > 0) & (FN[:, 0] * sg > -0.2)
    if sureA.sum() > 10 and bodyF.sum() > 10:
        ta = cKDTree(FCOL[sureA]); tb = cKDTree(FCOL[bodyF])
        da = ta.query(FCOL)[0]; db = tb.query(FCOL)[0]
        # solo si el color del brazo se distingue del cuerpo (si la manga y la polera son iguales, no se toma nada)
        armlike = (da < db * CFG.get('fillet_ratio', 0.7)) & (db > CFG.get('fillet_db', 0.07))
        for it in range(CFG.get('fillet_rings', 5)):
            nb = grow(armF) & ~armF
            add_ = nb & armlike & (np.nan_to_num(pf, nan=-1) > -0.001) & ~fpack & (FC[:, 2] < sh_z + 0.03)
            if not add_.any(): break
            armF |= add_
    # lo que está entre el brazo y el torso y MIRA hacia el brazo (correa, costado de la casaca) es del cuerpo:
    # la superficie propia del brazo mira hacia afuera de su eje; la del cuerpo bajo el brazo mira hacia el eje.
    # Si se la lleva el brazo, al levantarlo aparece como una aleta plana colgando de la axila.
    if CFG.get('face_k', -0.3) is not None and armF.sum() > 50:
        dirh_ = hand_ - shoulder_; dirh_ /= np.linalg.norm(dirh_)
        tip3 = np.nonzero(armF)[0][np.argmax(FC[armF] @ dirh_)]
        u3 = FC[tip3] - shoulder_
        tt3 = np.clip(((FC - shoulder_) @ u3) / (u3 @ u3), 0, 1)
        area3 = 0.5 * np.linalg.norm(np.cross(P[FV[:, 1]] - P[FV[:, 0]], P[FV[:, 2]] - P[FV[:, 0]]), axis=1)
        sure3 = armF & (np.nan_to_num(pf, nan=0) > 0.012)
        nb3 = 14; bins3 = np.minimum((tt3 * nb3).astype(int), nb3 - 1)
        cen3 = np.full((nb3, 3), np.nan)
        for b_ in range(nb3):
            m_ = sure3 & (bins3 == b_)
            if m_.sum() >= 6: cen3[b_] = np.average(FC[m_], axis=0, weights=area3[m_] + 1e-12)
        tb_ = (np.arange(nb3) + 0.5) / nb3
        line3 = shoulder_ + tb_[:, None] * u3
        cen3 = np.where(np.isnan(cen3), line3, cen3)
        for it in range(2): cen3[1:-1] = 0.5 * cen3[1:-1] + 0.25 * (cen3[:-2] + cen3[2:])
        cf3 = np.stack([np.interp(tt3, tb_, cen3[:, k]) for k in range(3)], 1)
        rv3 = FC - cf3; rn3 = rv3 / np.maximum(np.linalg.norm(rv3, axis=1), 1e-9)[:, None]
        dot3 = (FN * rn3).sum(1)
        medial = (rn3[:, 0] * sg) < CFG.get('face_med', 0.2)            # del lado del torso o de frente/atrás, no por fuera
        facing = armF & (dot3 < CFG.get('face_k', -0.3)) & medial & (tt3 < 0.92)
        # bolsillos del propio brazo (entre dedos, pliegues) rodeados de brazo: se devuelven
        idx6, lab6, n6 = comps(facing)
        back_ = np.zeros(npoly, bool)
        for c_ in range(n6):
            fc_ = idx6[lab6 == c_]
            mm_ = np.zeros(npoly, bool); mm_[fc_] = True
            ring_ = grow(mm_) & ~mm_
            if ring_.any() and (armF[ring_].mean() > 0.97): back_[fc_] = True
        facing &= ~back_
        armF &= ~facing
        idx7, lab7, n7 = comps(armF)
        if n7:
            sz7 = np.bincount(lab7, minlength=n7)
            armF[:] = False; armF[idx7[np.isin(lab7, np.nonzero(sz7 >= 0.05 * sz7.max())[0])]] = True
        DBG['facing_' + s] = facing.copy()
        print('CARA AL BRAZO', s, int(facing.sum()), 'devueltas', int(back_.sum()))
    # borde parejo: se quitan las puntas (caras con un solo vecino en el brazo) y se rellenan las muescas
    for it in range(3):
        nbc = ADJ @ armF.astype(float)
        armF &= ~(armF & (nbc < 2))
    for it in range(2):
        nbc = ADJ @ armF.astype(float)
        armF |= (~armF) & (nbc >= 3) & (ARMF == 0)
    idx4, lab4, n4 = comps(armF)
    if n4:
        sz4 = np.bincount(lab4, minlength=n4)
        armF[:] = False; armF[idx4[np.isin(lab4, np.nonzero(sz4 >= 0.05 * sz4.max())[0])]] = True
    ARMF[armF] = si + 1
    np.savez(f'/home/claude/w/rig4/out/dbg_{pet}_{s}.npz', alien=DBG.get('alien_' + s, np.zeros(npoly, bool)), pal=DBG.get('pal_' + s, np.zeros(npoly, bool)), tF=tF, dAx=dAx, FC=FC, FCOL=FCOL, armF=armF, cand=cand, pf=pf, shoulder=np.array([sg * arm_x0, arm_y, sh_z]), neck=neck_z, hip=hip_z)
    print('BRAZO', s, 'caras', int(armF.sum()), 'nucleo', int(core.sum()), 'candidatas', int(cand.sum()))

if os.environ.get('PASS1'):
    out_ = {}
    for s_, (sh0, u2, ct) in AXIS.items():
        nr = u2 / np.linalg.norm(u2); p0 = sh0 + ct * u2
        armF_ = ARMF == (1 if s_ == 'L' else 2)
        # radio: lo más lejos del eje entre las caras del brazo cerca del plano (más un margen)
        near_ = armF_ | (ADJ @ armF_.astype(float) > 0)
        tt = (FC - sh0) @ u2 / (u2 @ u2)
        q = FC - sh0; rad_ = np.linalg.norm(q - np.outer(q @ nr, nr), axis=1)
        band = near_ & (np.abs(tt - ct) < 0.1)
        rad = float(np.percentile(rad_[band], 95)) + 0.015 if band.any() else 0.08
        out_[s_] = {'p0': p0.tolist(), 'n': nr.tolist(), 'sh': sh0.tolist(), 'rad': rad}
    _json.dump(out_, open(PLF, 'w'))
    print('PLANO', out_)
    sys.exit(0)
if os.environ.get('SEGONLY'):
    red = bpy.data.materials.new('r'); red.diffuse_color = (1, 0, 0, 1); me.materials.append(red)
    grn = bpy.data.materials.new('g'); grn.diffuse_color = (0, 1, 0.2, 1); me.materials.append(grn)
    mi = np.zeros(npoly, int); mi[ARMF == 1] = 1; mi[ARMF == 2] = 2
    me.polygons.foreach_set('material_index', mi); me.update()
    armob.hide_render = True
    sc = bpy.context.scene
    for o in bpy.data.objects:
        if o.type == 'MESH' and o is not ob: o.hide_render = True
    sc.render.engine = 'BLENDER_WORKBENCH'; sc.display.shading.color_type = 'TEXTURE'
    sc.display.shading.show_backface_culling = True
    sc.render.resolution_x, sc.render.resolution_y = 300, 360
    cam_data = bpy.data.cameras.new('c'); cam = bpy.data.objects.new('c', cam_data); sc.collection.objects.link(cam); sc.camera = cam
    piv = bpy.data.objects.new('piv', None); sc.collection.objects.link(piv); cam.parent = piv
    cam.location = (0, -2.3, 0.5); cam.rotation_euler = (math.radians(90), 0, 0)
    for i, ang in enumerate((0, 90, 180, 270)):
        piv.rotation_euler = (0, 0, math.radians(ang))
        sc.render.filepath = f'/home/claude/w/rig4/shots/seg2_{pet}_{i}.png'; bpy.ops.render.render(write_still=True)
    sys.exit(0)
# ---------------------------------------------------------------- borde suave (curva de nivel)
if CFG.get('smooth_cut', True):
    exec(open('/home/claude/w/rig4/smoothcut.py').read())
# ---------------------------------------------------------------- piezas nuevas
def make_obj(name, V, F, UV, part):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(v) for v in V], [], [tuple(f) for f in F]); mesh.update()
    o = bpy.data.objects.new(name, mesh); bpy.context.scene.collection.objects.link(o)
    uvl = mesh.uv_layers.new(name=me.uv_layers.active.name).data
    for p_ in mesh.polygons:
        p_.use_smooth = True
        for k, li in enumerate(p_.loop_indices):
            uvl[li].uv = UV[F[p_.index][k]]
    mesh.materials.append(me.materials[0])
    a = mesh.attributes.new('part', 'INT', 'POINT'); a.data.foreach_set('value', [part] * len(V))
    return o

def lap_fill(known, vals):
    """Rellena una grilla (ny, nx, 3) resolviendo Laplace con los valores conocidos fijos."""
    ny, nx = known.shape
    N = ny * nx; idn = np.arange(N).reshape(ny, nx)
    a1 = np.r_[idn[:, :-1].ravel(), idn[:-1, :].ravel()]; b1 = np.r_[idn[:, 1:].ravel(), idn[1:, :].ravel()]
    rows = np.r_[a1, b1]; cols = np.r_[b1, a1]
    deg = np.bincount(rows, minlength=N).astype(float)
    L = coo_matrix((-np.ones(len(rows)), (rows, cols)), shape=(N, N)).tocsr()
    L = L + csr_matrix((deg, (np.arange(N), np.arange(N))), shape=(N, N))
    V0 = vals.reshape(N, 3).copy()
    unk = ~known.ravel()
    Luu = L[unk][:, unk].tocsc(); Luk = L[unk][:, ~unk]
    for c in range(3):
        V0[unk, c] = spsolve(Luu, -(Luk @ V0[~unk, c]))
    return V0.reshape(ny, nx, 3)

# ---------------------------------------------------------------- escudos nítidos (textura y material propios)
if CFG.get('logos'):
    exec(open('/home/claude/w/rig4/logos.py').read())
extra = []
base_uvname = me.uv_layers.active.name
cutcol = {}
for si, (s, sg) in enumerate((('L', 1), ('R', -1))):
    armF = ARMF == si + 1
    rbf = FIT[s]; pv = PV[s]
    h = CFG.get('plug_h', 0.006)
    av = np.unique(FV[armF])
    s0, s1 = S_[av].min() - 0.02, S_[av].max() + 0.02
    z0, z1 = z[av].min() - 0.02, min(z[av].max() + 0.02, neck_z + 0.05)
    gs = np.arange(s0, s1 + h, h); gz = np.arange(z0, z1 + h, h)
    nx, ny = len(gs), len(gz)
    # celdas tapadas por el brazo y por el cuerpo que queda (proyección en (s, z))
    def raster(fmask):
        m = np.zeros((ny - 1, nx - 1), bool)
        cxs = (gs[:-1] + h / 2); czs = (gz[:-1] + h / 2)
        for f in np.nonzero(fmask)[0]:
            A = np.c_[S_[FV[f]], z[FV[f]]]
            i0 = max(int((A[:, 0].min() - s0) / h) - 1, 0); i1 = min(int((A[:, 0].max() - s0) / h) + 2, nx - 1)
            j0 = max(int((A[:, 1].min() - z0) / h) - 1, 0); j1 = min(int((A[:, 1].max() - z0) / h) + 2, ny - 1)
            if i1 <= i0 or j1 <= j0: continue
            X, Z = np.meshgrid(cxs[i0:i1], czs[j0:j1])
            (ax, az), (bx, bz), (cx, cz) = A
            den = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
            if abs(den) < 1e-14:
                continue
            l1 = ((bz - cz) * (X - cx) + (cx - bx) * (Z - cz)) / den
            l2 = ((cz - az) * (X - cx) + (ax - cx) * (Z - cz)) / den
            ins = (l1 >= -0.05) & (l2 >= -0.05) & (1 - l1 - l2 >= -0.05)
            m[j0:j1, i0:i1] |= ins
        return m
    Mcell = ndimage.binary_fill_holes(raster(armF))
    bodyF = (ARMF == 0) & (np.abs(np.nan_to_num(np.nanmean(pv[FV], 1), nan=1)) < 0.010) & (FN[:, 0] * sg > -0.3) & (FC[:, 0] * sg > 0)
    Bcell = raster(bodyF)
    need = Mcell & ~ndimage.binary_erosion(Bcell, iterations=1)
    if CFG.get('plug_full', True):
        # el parche cubre toda la huella del brazo (y un borde): lo que quedaba del cuerpo bajo el brazo tiene huecos
        # y caras al revés que, al levantar el brazo, dejaban ver el fondo
        plugc = ndimage.binary_dilation(Mcell, iterations=CFG.get('plug_grow', 2))
    else:
        plugc = ndimage.binary_dilation(need, iterations=2) & ndimage.binary_dilation(Mcell, iterations=2)
    lab_, nl_ = ndimage.label(plugc)
    if nl_:
        szs = ndimage.sum(plugc, lab_, range(1, nl_ + 1))
        plugc = np.isin(lab_, 1 + np.nonzero(szs >= 4)[0])
    print('PARCHE', s, 'celdas', int(plugc.sum()), 'brazo', int(Mcell.sum()), 'cuerpo', int(Bcell.sum()))
    # vértices de la grilla
    vused = np.zeros((ny, nx), bool)
    for dj in (0, 1):
        for di in (0, 1):
            vused[dj:ny - 1 + dj, di:nx - 1 + di] |= plugc
    GS, GZ = np.meshgrid(gs, gz)
    GR = rbf(np.c_[GS.ravel(), GZ.ravel()]).reshape(ny, nx) - CFG.get('plug_depth', 0.0015)
    G3 = to3d(GS, GZ, GR)
    # color: Laplace desde el cuerpo que rodea al hueco
    okv = np.zeros(nv, bool)
    okv[np.unique(FV[(ARMF == 0)])] = True
    okv &= ~pack_mask & (np.abs(np.nan_to_num(pv, nan=1)) < 0.003) & OUTW & (x * sg > 0)
    okv[np.unique(FV[ARMF > 0])] = False
    # solo la piel del cuerpo: fuera los vértices de caras que sobresalen (bufanda, correas)
    pf_s = np.nan_to_num(np.nanmean(pv[FV], 1), nan=1)
    okv[np.unique(FV[pf_s > 0.005])] = False
    tb = cKDTree(P[okv]); cols_ok = vcol[okv]
    dd, ii = tb.query(G3.reshape(-1, 3), k=4, distance_upper_bound=0.03)
    known = np.zeros(ny * nx, bool); vals = np.zeros((ny * nx, 3))
    for k_ in range(ny * nx):
        m_ = np.isfinite(dd[k_])
        if m_.sum() >= 2 and dd[k_][m_].min() < 0.010:
            w_ = 1 / np.maximum(dd[k_][m_], 1e-4) ** 2
            vals[k_] = (cols_ok[ii[k_][m_]] * w_[:, None]).sum(0) / w_.sum(); known[k_] = True
    Mv = np.zeros((ny, nx), bool)
    for dj in (0, 1):
        for di in (0, 1):
            Mv[dj:ny - 1 + dj, di:nx - 1 + di] |= Mcell
    known = known.reshape(ny, nx) & ~ndimage.binary_erosion(Mv, iterations=1)
    # solo los colores principales de la piel alrededor (sin correas, bufanda ni bordes de otro color)
    kv = vals.reshape(ny, nx, 3)[known]
    if len(kv) > 20:
        from scipy.cluster.vq import kmeans2
        k_ = min(4, len(kv) // 10)
        cen_, lab_k = kmeans2(kv.astype(np.float64), k_, seed=1, minit='++')
        share = np.bincount(lab_k, minlength=k_) / len(kv)
        okc = share >= CFG.get('skin_share', 0.3)
        if not okc.any(): okc[np.argmax(share)] = True
        kk_ = known.copy(); kk_[known] = okc[lab_k]
        known = kk_
        print('PIEL', s, [(tuple((cen_[c] * 255).astype(int)), round(float(share[c]), 2)) for c in range(k_) if okc[c]])
        SKIN[s] = (cen_[okc] * share[okc][:, None]).sum(0) / share[okc].sum()
    if known.sum() < 5:
        known[0, :] = True; vals.reshape(ny, nx, 3)[0, :] = np.median(cols_ok, 0)
    # el borde del parche toma el color exacto del cuerpo que lo rodea (sin correr el filtro de piel): así el contorno
    # del hueco (que sigue las aristas de la malla) no se ve como un borde dentado de otro color
    if CFG.get('plug_rimcol', True):
        armv_ = np.zeros(nv, bool); armv_[np.unique(FV[armF])] = True
        cB = np.zeros((nv, 3)); nB = np.zeros(nv)
        bf_ = np.nonzero(ARMF == 0)[0]
        li_ = (ls_[bf_][:, None] + np.arange(3)).ravel()
        np.add.at(cB, lv[li_], lcol[li_]); np.add.at(nB, lv[li_], 1)
        rimv_ = np.nonzero(armv_ & (nB > 0))[0]
        if len(rimv_):
            cr_ = cB[rimv_] / nB[rimv_][:, None]
            ii_r = np.clip(np.round((S_[rimv_] - s0) / h).astype(int), 0, nx - 1)
            jj_r = np.clip(np.round((z[rimv_] - z0) / h).astype(int), 0, ny - 1)
            kn2 = known.reshape(ny, nx); vv2 = vals.reshape(ny, nx, 3)
            acc = np.zeros((ny, nx, 3)); cnt = np.zeros((ny, nx))
            np.add.at(acc, (jj_r, ii_r), cr_); np.add.at(cnt, (jj_r, ii_r), 1)
            hit = cnt > 0
            vv2[hit] = acc[hit] / cnt[hit][:, None]; kn2[hit] = True
            known = kn2
    COL = lap_fill(known, vals.reshape(ny, nx, 3))
    # textura del parche: un texel cada medio paso de la grilla
    pad = 6
    jj = np.nonzero(vused.any(1))[0]; ii_ = np.nonzero(vused.any(0))[0]
    ja, jb, ia, ib = jj.min(), jj.max(), ii_.min(), ii_.max()
    sub = COL[ja:jb + 1, ia:ib + 1]
    hh_, ww_ = (jb - ja) * 2 + 1, (ib - ia) * 2 + 1
    yy_ = np.linspace(0, jb - ja, hh_); xx_ = np.linspace(0, ib - ia, ww_)
    Y_, X_ = np.meshgrid(yy_, xx_, indexing='ij')
    up = np.stack([ndimage.map_coordinates(sub[..., c], [Y_, X_], order=1, mode='nearest') for c in range(3)], -1)
    up = np.pad(up, ((pad, pad), (pad, pad), (0, 0)), mode='edge')
    ox, oy = alloc(up.shape[1], up.shape[0])
    TEX[oy:oy + up.shape[0], ox:ox + up.shape[1], :3] = up
    flat_normal(ox, oy, up.shape[1], up.shape[0])
    vid = -np.ones((ny, nx), int); Vl = []; UVl = []
    for j in range(ny):
        for i in range(nx):
            if vused[j, i]:
                vid[j, i] = len(Vl); Vl.append(G3[j, i])
                UVl.append(((ox + pad + 2 * (i - ia) + 0.5) / TW, (oy + pad + 2 * (j - ja) + 0.5) / TH))
    Fl = []
    for j in range(ny - 1):
        for i in range(nx - 1):
            if plugc[j, i]:
                a_, b_, c_, d_ = vid[j, i], vid[j, i + 1], vid[j + 1, i + 1], vid[j + 1, i]
                Fl.append((a_, b_, c_)); Fl.append((a_, c_, d_))
    Vl = np.array(Vl)
    # orientación hacia afuera
    f0 = Fl[len(Fl) // 2]; nrm = np.cross(Vl[f0[1]] - Vl[f0[0]], Vl[f0[2]] - Vl[f0[0]])
    cen = Vl[list(f0)].mean(0); outv = np.array([cen[0], cen[1] - CY, 0])
    if np.dot(nrm, outv) < 0: Fl = [(a_, c_, b_) for (a_, b_, c_) in Fl]
    extra.append(make_obj('parche_' + s, Vl, Fl, UVl, 3 + si))
    # tapas: el borde del corte entre brazo y cuerpo
    cutAll = []
    for (f1, f2), kk in zip(PAIRS, PKEY):
        if (ARMF[f1] == si + 1) != (ARMF[f2] == si + 1) and (ARMF[f1] == si + 1 or ARMF[f2] == si + 1):
            cutAll.append((int(kk // nv), int(kk % nv)))
    cutAll = list(set(cutAll))
    # solo el borde principal (los huequitos sueltos dentro del brazo darían astillas)
    if cutAll:
        ev = np.array(cutAll); uvv, inv = np.unique(ev.ravel(), return_inverse=True); inv = inv.reshape(-1, 2)
        Ag = csr_matrix((np.ones(len(inv)), (inv[:, 0], inv[:, 1])), shape=(len(uvv), len(uvv)))
        nc_, lc_ = connected_components(Ag, directed=False)
        ecomp = lc_[inv[:, 0]]; ecount = np.bincount(ecomp, minlength=nc_)
        keepc_ = np.nonzero(ecount >= max(6, 0.2 * ecount.max()))[0]
        cutMain = [e_ for e_, c_ in zip(cutAll, ecomp) if c_ in keepc_]
    else:
        cutMain = []
    cutPlane = []
    if s in AXIS and cutAll:
        sh0_, u2_, ct_ = AXIS[s]
        tvv = lambda v_: float((P[v_] - sh0_) @ u2_ / (u2_ @ u2_))
        cutPlane = [e_ for e_ in cutAll if abs(tvv(e_[0]) - ct_) < 0.004 and abs(tvv(e_[1]) - ct_) < 0.004]
    print('BORDE', s, 'todo', len(cutAll), 'principal', len(cutMain), 'plano', len(cutPlane))
    def fan(edges, center, ref, uvc, rad_px=6):
        """Abanico de dos caras (se ve desde ambos lados) del borde al centro."""
        vs_ = np.unique(np.array(edges).ravel())
        V_ = [center]; UV_ = [tuple(uvc)]; mp = {}
        for k_, v_ in enumerate(vs_):
            mp[int(v_)] = len(V_); V_.append(P[v_])
            ang = 2 * math.pi * k_ / len(vs_)
            UV_.append(tuple(uvc + np.array((math.cos(ang), math.sin(ang))) * rad_px / np.array((TW, TH))))
        V_ = np.array(V_); F_ = []
        for (a_, b_) in edges:
            t_ = (0, mp[a_], mp[b_])
            if np.dot(np.cross(V_[t_[1]] - V_[0], V_[t_[2]] - V_[0]), ref) < 0: t_ = (0, mp[b_], mp[a_])
            F_.append(t_)
        return V_, F_, UV_
    def color_rect(col):
        cw = 20; cx_, cy_ = alloc(cw, cw)
        TEX[cy_:cy_ + cw, cx_:cx_ + cw, :3] = col; flat_normal(cx_, cy_, cw, cw)
        return np.array(((cx_ + cw / 2) / TW, (cy_ + cw / 2) / TH))
    if cutPlane and not (CFG.get('arm_wall', True) and CFG.get('body_wall', True)):
        # tapa del cuerpo en el plano del hombro (gato, coneja), color de la piel
        skin = SKIN.get(s, np.median(vcol[okv], 0))
        bvp = np.unique(np.array(cutPlane).ravel())
        Vb_, Fb, UVb = fan(cutPlane, P[bvp].mean(0), AXIS[s][1], color_rect(skin))
        extra.append(make_obj('tapa_cuerpo_' + s, Vb_, Fb, UVb, 8))
    if CFG.get('arm_wall', True) and armF.sum() > 50:
        # pared interior del brazo: el brazo era un relieve pegado al costado, abierto del lado del cuerpo. Su borde se
        # cierra con una membrana suave (mapa armónico de un disco, un poco abombada hacia el cuerpo, donde queda
        # escondida en reposo) con el color de la manga junto al borde. Al levantar el brazo se ve macizo.
        from scipy.spatial import Delaunay as _Del
        keyp = np.round(P / 1e-6).astype(np.int64)
        _, rep = np.unique(keyp, axis=0, return_inverse=True); rep = rep.ravel()
        first_ = {}
        for v_ in range(nv): first_.setdefault(int(rep[v_]), v_)
        FVa = rep[FV[armF]]
        ea_ = np.sort(np.r_[FVa[:, [0, 1]], FVa[:, [1, 2]], FVa[:, [2, 0]]], 1)
        ue_, cnt_ = np.unique(ea_, axis=0, return_counts=True)
        bed = ue_[cnt_ == 1]
        cutset = set(tuple(sorted((int(rep[a_]), int(rep[b_])))) for (a_, b_) in cutAll)
        nbr = {}
        for a_, b_ in bed:
            nbr.setdefault(int(a_), []).append(int(b_)); nbr.setdefault(int(b_), []).append(int(a_))
        used = set(); loops = []
        for a0, b0 in bed:
            e0 = (int(min(a0, b0)), int(max(a0, b0)))
            if e0 in used: continue
            path = [int(a0)]; pos = {int(a0): 0}; prev, cur = int(a0), int(b0); used.add(e0)
            for it in range(100000):
                if cur in pos:
                    # se cerró un lazo (en el inicio o en un vértice donde se tocan dos bordes): se separa
                    k_ = pos[cur]; sub = path[k_:]
                    if len(sub) >= 8: loops.append(sub)
                    for v_ in sub[1:]: pos.pop(v_, None)
                    path = path[:k_ + 1]
                    if k_ == 0 and len(path) == 1:
                        # ¿quedan aristas libres desde el inicio?
                        pass
                else:
                    pos[cur] = len(path); path.append(cur)
                nxt = None
                for c_ in nbr[cur]:
                    ek = (min(cur, c_), max(cur, c_))
                    if ek not in used:
                        nxt = c_; break
                if nxt is None: break
                used.add((min(cur, nxt), max(cur, nxt))); prev, cur = cur, nxt
        ca_ = P[av].mean(0)
        # color de la manga en cada vértice del borde
        vcolA = {}
        for f in np.nonzero(armF)[0]:
            for k in range(3):
                vcolA.setdefault(int(rep[FV[f][k]]), []).append(lcol[ls_[f] + k])
        Vw_, Fw_, UVw_ = [], [], []
        Vb2, Fb2, UVb2 = [], [], []
        vcolB = {}
        for f in np.nonzero(ARMF == 0)[0]:
            for k in range(3):
                r_ = int(rep[FV[f][k]])
                if r_ in nbr: vcolB.setdefault(r_, []).append(lcol[ls_[f] + k])
        for loop in loops:
            Lp = np.array([P[first_[r_]] for r_ in loop])
            segs = np.linalg.norm(np.roll(Lp, -1, 0) - Lp, axis=1); per = segs.sum()
            ncut = sum(1 for k in range(len(loop)) if tuple(sorted((loop[k], loop[(k + 1) % len(loop)]))) in cutset)
            if per < 0.03 or ncut < 0.3 * len(loop): continue
            th = 2 * math.pi * np.r_[0, np.cumsum(segs)[:-1]] / per
            nb_ = len(loop)
            B2 = np.c_[np.cos(th), np.sin(th)]
            # interior más ralo que el borde (pocas caras): una rejilla hexagonal y un anillo intermedio
            sp = max(2 * math.pi / nb_ * 1.15, CFG.get('wall_sp', 0.17))
            gg = np.arange(-1, 1 + sp, sp)
            GX, GY = np.meshgrid(gg, gg); GX = GX.ravel(); GY = GY.ravel()
            GX = GX + (np.round((GY + 1) / sp) % 2) * sp / 2
            inn = np.hypot(GX, GY) < 1 - 1.0 * sp
            nr_ = max(nb_ // 2, 12); ar_ = 2 * math.pi * (np.arange(nr_) + 0.5) / nr_
            I2 = np.r_[np.c_[GX[inn], GY[inn]], (1 - 0.5 * sp) * np.c_[np.cos(ar_), np.sin(ar_)]]
            X2 = np.r_[B2, I2]
            tri = _Del(X2).simplices
            nI = len(I2); nT = nb_ + nI
            # Laplace (pesos uniformes) para la posición 3D de los puntos interiores
            ee = np.r_[tri[:, [0, 1]], tri[:, [1, 2]], tri[:, [2, 0]]]
            ee = np.unique(np.sort(ee, 1), axis=0)
            Ad = csr_matrix((np.ones(2 * len(ee)), (np.r_[ee[:, 0], ee[:, 1]], np.r_[ee[:, 1], ee[:, 0]])), shape=(nT, nT))
            dg = np.asarray(Ad.sum(1)).ravel()
            Lm = csr_matrix((dg, (np.arange(nT), np.arange(nT))), shape=(nT, nT)) - Ad
            ii_i = np.arange(nb_, nT)
            X3 = np.zeros((nT, 3)); X3[:nb_] = Lp
            if nI:
                Lii = Lm[ii_i][:, ii_i].tocsc(); Lib = Lm[ii_i][:, :nb_]
                for c in range(3): X3[nb_:, c] = spsolve(Lii, -(Lib @ Lp[:, c]))
                # abombado hacia el cuerpo (lejos del centro del brazo): u = solución de Poisson, 0 en el borde
                uu = spsolve(Lii, np.ones(nI)); uu = uu / max(uu.max(), 1e-9)
                nrm_ = np.zeros(3)
                for t_ in tri:
                    nrm_ += np.cross(X3[t_[1]] - X3[t_[0]], X3[t_[2]] - X3[t_[0]])
                nrm_ /= max(np.linalg.norm(nrm_), 1e-12)
                if np.dot(nrm_, X3[nb_:].mean(0) - ca_) < 0: nrm_ = -nrm_
                rad_ = float(np.median(np.linalg.norm(Lp - Lp.mean(0), axis=1)))
                X3[nb_:] += (min(CFG.get('wall_bulge', 0.25) * rad_, CFG.get('wall_bulge_max', 0.012)) * uu)[:, None] * nrm_
            # orientación: hacia afuera del brazo
            tri = [tuple(int(k) for k in t_) for t_ in tri]
            sgn = 0.0
            for t_ in tri:
                n3 = np.cross(X3[t_[1]] - X3[t_[0]], X3[t_[2]] - X3[t_[0]]); sgn += np.dot(n3, X3[list(t_)].mean(0) - ca_)
            if sgn < 0: tri = [(a_, c_, b_) for (a_, b_, c_) in tri]
            # textura propia: el color del borde, rellenado hacia el centro
            Tp = 40; padw = 4
            cols_b = np.array([np.mean(vcolA.get(r_, [np.median(FCOL[armF], 0)]), 0) for r_ in loop])
            gy_, gx_ = np.mgrid[0:Tp, 0:Tp] + 0.5
            ux = gx_ / Tp * 2 - 1; uy = gy_ / Tp * 2 - 1; rr = np.hypot(ux, uy)
            kn = rr >= 0.9
            ang_ = np.mod(np.arctan2(uy, ux), 2 * math.pi)
            valsw = np.zeros((Tp, Tp, 3))
            for c in range(3): valsw[..., c] = np.interp(ang_, np.r_[th, 2 * math.pi], np.r_[cols_b[:, c], cols_b[0, c]])
            patch = lap_fill(kn, valsw)
            patch = np.pad(patch, ((padw, padw), (padw, padw), (0, 0)), mode='edge')
            oxw, oyw = alloc(patch.shape[1], patch.shape[0])
            TEX[oyw:oyw + patch.shape[0], oxw:oxw + patch.shape[1], :3] = patch
            flat_normal(oxw, oyw, patch.shape[1], patch.shape[0])
            k0 = len(Vw_)
            for k in range(nT):
                Vw_.append(X3[k])
                UVw_.append(((oxw + padw + (X2[k, 0] * 0.5 + 0.5) * Tp) / TW, (oyw + padw + (X2[k, 1] * 0.5 + 0.5) * Tp) / TH))
            Fw_ += [(a_ + k0, b_ + k0, c_ + k0) for (a_, b_, c_) in tri]
            print('PARED', s, 'borde', nb_, 'interiores', nI, 'perímetro cm', round(per * 100, 1), 'corte', ncut)
            if CFG.get('body_wall', True) and nI:
                # la misma membrana del lado del cuerpo (mirando al brazo), hundida en el cuerpo: cierra el hueco del
                # costado y la boca del hombro; casi toda queda bajo el parche, solo asoma donde hace falta
                Xb = X3.copy(); Xb[nb_:] = X3[nb_:] - (min(CFG.get('wall_bulge', 0.25) * rad_, CFG.get('wall_bulge_max', 0.012)) * uu)[:, None] * nrm_
                Xb[nb_:] += (min(CFG.get('bwall_bulge', 0.5) * rad_, CFG.get('bwall_bulge_max', 0.025)) * uu)[:, None] * nrm_
                trib = [(a_, c_, b_) for (a_, b_, c_) in tri]
                cols_c = np.array([np.mean(vcolB.get(r_, [SKIN.get(s, np.median(FCOL[armF], 0))]), 0) for r_ in loop])
                for c in range(3): valsw[..., c] = np.interp(ang_, np.r_[th, 2 * math.pi], np.r_[cols_c[:, c], cols_c[0, c]])
                patchb = np.pad(lap_fill(kn, valsw), ((padw, padw), (padw, padw), (0, 0)), mode='edge')
                oxb, oyb = alloc(patchb.shape[1], patchb.shape[0])
                TEX[oyb:oyb + patchb.shape[0], oxb:oxb + patchb.shape[1], :3] = patchb
                flat_normal(oxb, oyb, patchb.shape[1], patchb.shape[0])
                k1 = len(Vb2)
                for k in range(nT):
                    Vb2.append(Xb[k])
                    UVb2.append(((oxb + padw + (X2[k, 0] * 0.5 + 0.5) * Tp) / TW, (oyb + padw + (X2[k, 1] * 0.5 + 0.5) * Tp) / TH))
                Fb2 += [(a_ + k1, b_ + k1, c_ + k1) for (a_, b_, c_) in trib]
        if Fw_:
            extra.append(make_obj('pared_' + s, np.array(Vw_), Fw_, UVw_, 9 + si))
        if Fb2:
            extra.append(make_obj('pared_cuerpo_' + s, np.array(Vb2), Fb2, UVb2, 8))
    elif cutMain:
        # falda del brazo: el borde se extiende hacia dentro del cuerpo y se cierra; quieto queda escondida dentro del
        # cuerpo; al mover el brazo llena la unión con el color de la manga (el brazo se ve macizo, sin hueco)
        rimF = armF & np.isin(FV, np.unique(np.array(cutMain).ravel())).any(1)
        loopc = P[np.unique(np.array(cutMain).ravel())].mean(0)
        dep = CFG.get('skirt_d', 0.028)
        def inward(p_):
            d_ = np.array([-p_[0], CY - p_[1], 0.0]); return d_ / (np.linalg.norm(d_) + 1e-9)
        # UV de cada vértice del borde según la cara del brazo que lo toca
        uvrim = {}
        for f in np.nonzero(rimF)[0]:
            for k in range(3): uvrim.setdefault(int(FV[f][k]), uvL[ls_[f] + k])
        Vs, Fs, UVs = [], [], []
        dsum = np.zeros(3)
        for (a_, b_) in cutMain:
            pa, pb = P[a_], P[b_]; qa = pa + dep * inward(pa); qb = pb + dep * inward(pb); dsum += inward(pa)
            ua = uvrim.get(a_, uvrim.get(b_, (0.5, 0.5))); ub = uvrim.get(b_, ua)
            k0 = len(Vs); Vs += [pa, pb, qb, qa]; UVs += [tuple(ua), tuple(ub), tuple(ub), tuple(ua)]
            nrm = np.cross(pb - pa, qa - pa); mid = (pa + pb) / 2
            dm = inward(mid); outv = (mid - loopc) - dm * np.dot(mid - loopc, dm)
            if np.dot(nrm, outv) >= 0: Fs += [(k0, k0 + 1, k0 + 2), (k0, k0 + 2, k0 + 3)]
            else: Fs += [(k0, k0 + 2, k0 + 1), (k0, k0 + 3, k0 + 2)]
        # tapa del fondo de la falda
        ccol = np.median(FCOL[rimF], 0) if rimF.sum() >= 3 else np.median(FCOL[armF], 0)
        cuv = color_rect(ccol)
        dmean = dsum / (np.linalg.norm(dsum) + 1e-9)
        inner = {}
        for (a_, b_) in cutMain:
            for v_ in (a_, b_):
                if v_ not in inner: inner[v_] = P[v_] + dep * inward(P[v_])
        cen = np.mean(list(inner.values()), 0)
        kc = len(Vs); Vs.append(cen); UVs.append(tuple(cuv)); idxi = {}
        for k_, (v_, q_) in enumerate(inner.items()):
            idxi[v_] = len(Vs); Vs.append(q_)
            ang = 2 * math.pi * k_ / len(inner); UVs.append(tuple(cuv + np.array((math.cos(ang), math.sin(ang))) * 6 / np.array((TW, TH))))
        Vs_ = np.array(Vs)
        for (a_, b_) in cutMain:
            t_ = (kc, idxi[a_], idxi[b_])
            if np.dot(np.cross(Vs_[t_[1]] - Vs_[kc], Vs_[t_[2]] - Vs_[kc]), dmean) < 0: t_ = (kc, idxi[b_], idxi[a_])
            Fs.append(t_)
        extra.append(make_obj('falda_' + s, Vs_, Fs, UVs, 9 + si))
        print('FALDA', s, 'aristas', len(cutMain), 'color', (ccol * 255).astype(int))
    if cutMain and CFG.get('arm_cap', False):
        # tapa del brazo: todo el borde, con el centro un poco dentro del brazo
        bvm = np.unique(np.array(cutMain).ravel())
        cb = P[bvm].mean(0); ca = P[av].mean(0)
        cpt = cb + CFG.get('cap_in', 0.3) * (ca - cb)
        # color de la tapa: el de la manga junto al borde (no la mezcla de todo el brazo)
        rimF = armF & np.isin(FV, bvm).any(1)
        ccol = np.median(FCOL[rimF], 0) if rimF.sum() >= 3 else np.median(FCOL[armF], 0); cutcol[s] = ccol
        Vc_, Fc, UVc = fan(cutMain, cpt, cb - ca, color_rect(ccol))
        extra.append(make_obj('tapa_' + s, Vc_, Fc, UVc, 5 + si))
        print('TAPA', s, 'aristas', len(cutMain), 'color', (ccol * 255).astype(int))
    # faldón: donde el corte pasa por una pieza que sobresale (bufanda, cuello), una pared baja del borde hasta el
    # parche, con el color del borde, para que no se vea el hueco al levantar el brazo
    Vw, Fw, UVw = [], [], []
    ztop_arm = float(P[av, 2].max())
    for (f1, f2), kk in zip(PAIRS, PKEY):
        a1, a2 = ARMF[f1] == si + 1, ARMF[f2] == si + 1
        if a1 == a2: continue
        fb = f2 if a1 else f1
        if ARMF[fb] != 0: continue
        va, vb = int(kk // nv), int(kk % nv)
        if max(np.nan_to_num(pv[va], nan=0), np.nan_to_num(pv[vb], nan=0)) < CFG.get('wall_p', 10.0): continue
        lk = {int(FV[fb][k]): ls_[fb] + k for k in range(3)}
        ua, ub = uvL[lk[va]], uvL[lk[vb]]
        pa, pb = P[va], P[vb]
        dep = CFG.get('plug_depth', 0.0015) + 0.002
        qa = to3d(np.array(S_[va]), np.array(z[va]), np.array(rbf(np.array([[S_[va], z[va]]]))[0] - dep))
        qb = to3d(np.array(S_[vb]), np.array(z[vb]), np.array(rbf(np.array([[S_[vb], z[vb]]]))[0] - dep))
        # si el borde ya está casi sobre el cuerpo, no hace falta pared
        if np.linalg.norm(pa - qa) < 0.003 and np.linalg.norm(pb - qb) < 0.003: continue
        # solo en el hombro (bufanda, cuello) y paredes cortas; abajo se verían como rayas al levantar el brazo
        if max(pa[2], pb[2]) < ztop_arm - CFG.get('wall_band', 0.07): continue
        if max(np.linalg.norm(pa - qa), np.linalg.norm(pb - qb)) > CFG.get('wall_max', 0.03): continue
        b0 = len(Vw); Vw += [pa, pb, qb, qa]; UVw += [tuple(ua), tuple(ub), tuple(ub), tuple(ua)]
        nrm = np.cross(pb - pa, qa - pa); outd = (pa + pb) / 2 - FC[fb]
        if np.dot(nrm, outd) >= 0: Fw += [(b0, b0 + 1, b0 + 2), (b0, b0 + 2, b0 + 3)]
        else: Fw += [(b0, b0 + 2, b0 + 1), (b0, b0 + 3, b0 + 2)]
    if Fw:
        extra.append(make_obj('faldon_' + s, np.array(Vw), Fw, UVw, 7))
    print('FALDON', s, 'paredes', len(Fw) // 2)

# ---------------------------------------------------------------- guardar la textura pintada
import tempfile, os
from PIL import Image as _PI
def save_img(im, arr, name):
    pth = f'/home/claude/w/rig4/tex/{pet}_{name}.png'
    os.makedirs(os.path.dirname(pth), exist_ok=True)
    _PI.fromarray((np.clip(arr[::-1, :, :3], 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB').save(pth)
    if name == 'color':
        jp = pth.replace('.png', '.jpg'); _PI.open(pth).save(jp, quality=88, subsampling=0, optimize=True); pth = jp
    im.unpack(method='REMOVE') if im.packed_file else None
    im.filepath = pth; im.source = 'FILE'; im.reload(); im.pack()
save_img(IMG, TEX, 'color')
# el visor no usa la textura de rugosidad/metal (material mate): se quita para que el archivo pese menos
_nt = me.materials[0].node_tree
for n in list(_nt.nodes):
    if n.type == 'TEX_IMAGE' and n.image is not None and n.image is not IMG and n.image is not NIMG:
        _nt.nodes.remove(n)
for n in _nt.nodes:
    if n.type == 'BSDF_PRINCIPLED':
        for l in list(n.inputs['Roughness'].links) + list(n.inputs['Metallic'].links): _nt.links.remove(l)
        n.inputs['Roughness'].default_value = 0.9; n.inputs['Metallic'].default_value = 0.0
if NIMG is not None: save_img(NIMG, NTEX, 'normal')

P_ORIG = P.copy(); VN_ORIG = VN.copy()
# ---------------------------------------------------------------- cuerpo sin los brazos + piezas
def copy_with_faces(name, keep):
    o2 = ob.copy(); o2.data = ob.data.copy(); o2.name = name
    bpy.context.scene.collection.objects.link(o2)
    for m in list(o2.modifiers): o2.modifiers.remove(m)
    o2.parent = None; o2.vertex_groups.clear()
    if o2.data.shape_keys: o2.shape_key_clear()
    b2 = bmesh.new(); b2.from_mesh(o2.data); b2.faces.ensure_lookup_table()
    keep = set(int(k) for k in keep)
    bmesh.ops.delete(b2, geom=[f for f in b2.faces if f.index not in keep], context='FACES')
    b2.to_mesh(o2.data); b2.free()
    return o2
pieces = []
for si, s in enumerate(('L', 'R')):
    o2 = copy_with_faces('brazo_' + s, np.nonzero(ARMF == si + 1)[0])
    if NEWPOS:
        Vo = np.array([v.co[:] for v in o2.data.vertices]); kt = cKDTree(Vo)
        for k_, npos in NEWPOS.items():
            d_, j_ = kt.query(P[k_])
            if d_ < 1e-7: o2.data.vertices[int(j_)].co = tuple(npos)
    if 'part' in o2.data.attributes: o2.data.attributes.remove(o2.data.attributes['part'])
    a = o2.data.attributes.new('part', 'INT', 'POINT'); a.data.foreach_set('value', [1 + si] * len(o2.data.vertices))
    pieces.append(o2)
bm_ = bmesh.new(); bm_.from_mesh(me); bm_.faces.ensure_lookup_table()
bmesh.ops.delete(bm_, geom=[f for f in bm_.faces if ARMF[f.index] > 0], context='FACES_ONLY')
loose = [v for v in bm_.verts if not v.link_faces]
bmesh.ops.delete(bm_, geom=loose, context='VERTS')
bm_.to_mesh(me); bm_.free(); me.update()
a = me.attributes.new('part', 'INT', 'POINT'); a.data.foreach_set('value', [0] * len(me.vertices))
for m in list(ob.modifiers): ob.modifiers.remove(m)
ob.vertex_groups.clear(); ob.parent = None
bpy.ops.object.select_all(action='DESELECT')
for o2 in pieces + extra: o2.select_set(True)
ob.select_set(True); bpy.context.view_layer.objects.active = ob
bpy.ops.object.join()
me = ob.data
PART = np.zeros(len(me.vertices), int); me.attributes['part'].data.foreach_get('value', PART)
me.attributes.remove(me.attributes['part'])
if os.environ.get('KEEPPART'):
    pal_ = np.array([[0.85, 0.85, 0.85], [1, 0.2, 0.2], [0.2, 0.4, 1], [1, 1, 0.2], [1, 0.6, 0.1], [0.1, 0.9, 0.2], [0.1, 0.9, 0.9], [1, 0, 1], [0.6, 0.2, 0.9], [0.5, 0.1, 0.1], [0.1, 0.2, 0.5]])
    dc = me.color_attributes.new(name='dbgcol', type='FLOAT_COLOR', domain='POINT')
    dc.data.foreach_set('color', np.c_[pal_[PART], np.ones(len(PART))].ravel())
nv = len(me.vertices)
P = np.array([v.co[:] for v in me.vertices]); x, y, z = P[:, 0], P[:, 1], P[:, 2]
cap_mask = (PART == 1) | (PART == 2) | (PART == 5) | (PART == 6) | (PART == 9) | (PART == 10)
plug_mask = (PART == 3) | (PART == 4) | (PART == 7) | (PART == 8)
print('PIEZAS', {k: int((PART == k).sum()) for k in range(11)})
# normales: el cuerpo y los brazos conservan las del modelo entero, así la unión no se marca con luz
kt_ = cKDTree(P_ORIG)
me.update()
NV_ = np.array([v.normal[:] for v in me.vertices])
keepn = (PART == 0) | (PART == 1) | (PART == 2)
dd_, jj_ = kt_.query(P[keepn])
okn = dd_ < 1e-6
tmpn = NV_[keepn]; tmpn[okn] = VN_ORIG[jj_[okn]]; NV_[keepn] = tmpn

NV_ /= np.maximum(np.linalg.norm(NV_, axis=1), 1e-9)[:, None]
me.normals_split_custom_set_from_vertices([tuple(n) for n in NV_])
print('NORMALES conservadas', int(okn.sum()), 'de', int(keepn.sum()))

def SMOOTH():
    # Pesos del cuerpo sin saltos (antes de la unión de los brazos):
    # 1) piezas sueltas chicas (botones, parches, correas) toman los pesos de la superficie donde están pegadas; el
    #    cálculo automático a veces les daba la cabeza o la cola y se estiraban como picos al moverse.
    # 2) unas vueltas de suavizado de Laplace en el cuerpo: el paso de un hueso a otro se reparte en unos centímetros.
    ed = np.array([e.vertices[:] for e in me.edges])
    A_ = csr_matrix((np.ones(len(ed)), (ed[:, 0], ed[:, 1])), shape=(nv, nv)); A_ = ((A_ + A_.T) > 0).astype(float)
    ncomp, lab = connected_components(A_, directed=False)
    body = (PART == 0) & ~pack_mask
    cnt = np.bincount(lab[body], minlength=ncomp)
    main = int(np.argmax(cnt))
    mainv = np.nonzero(body & (lab == main))[0]
    kt = cKDTree(P[mainv])
    fixed = []
    for c in np.unique(lab[body]):
        if c == main: continue
        vs = np.nonzero(body & (lab == c))[0]
        if len(vs) > CFG.get('loose_max', 4000): continue
        tot_ = {b: float(Wm[b][vs].sum()) for b in bones}
        dom = max(tot_, key=tot_.get)
        if dom.startswith('Ear') or dom.startswith('Tail'): continue
        if dom == 'Head' and P[vs, 2].mean() > neck_z - 0.02: continue
        d_, j_ = kt.query(P[vs])
        for b in bones: Wm[b][vs] = Wm[b][mainv[j_]]
        fixed.append((len(vs), dom))
    print('PIEZAS SUELTAS corregidas', len(fixed), fixed[:8])
    # cabeza y orejas no mueven nada que esté bajo el cuello (botones, parches del pecho)
    low = body & (z < neck_z - 0.04)
    nbad = int((low & (Wm['Head'] > 0.05)).sum())
    for b in bones:
        if b == 'Head' or b.startswith('Ear'): Wm[b][low] = 0.0
    totw = sum(Wm[b] for b in bones)
    empty = body & (totw < 0.05)
    if empty.any():
        okb = np.nonzero(body & ~empty & (z < neck_z - 0.04))[0]
        if len(okb) >= 50:
            d_, j_ = cKDTree(P[okb]).query(P[empty])
            for b in bones: Wm[b][empty] = Wm[b][okb[j_]]
            far = np.nonzero(empty)[0][d_ > 0.05]
        else:
            far = np.nonzero(empty)[0]
        # sin donantes cerca (búho: el cálculo automático daba la cabeza a todo el cuerpo): cadera y pecho por altura
        if len(far):
            wc = sstep_np((z[far] - hip_z) / max(neck_z - hip_z, 1e-3))
            for b in bones: Wm[b][far] = 0.0
            Wm['Chest'][far] = wc; Wm['Hips'][far] = 1 - wc
        print('SIN DONANTE (por altura)', len(far))
    print('CABEZA bajo el cuello quitada en', nbad, 'vértices; sin peso', int(empty.sum()))
    mov = (PART == 0) & ~pack_mask
    deg = np.maximum(np.asarray(A_.sum(1)).ravel(), 1)
    Wmat = np.stack([Wm[b] for b in bones], 1)
    for it in range(CFG.get('w_smooth', 4)):
        Wn = (A_ @ Wmat) / deg[:, None]
        Wmat[mov] = 0.5 * Wmat[mov] + 0.5 * Wn[mov]
    # donde un hueso cambia de golpe a otro (cabeza-cuerpo, cola pegada al cuerpo, base de las orejas), una franja más
    # ancha (unos 3 cm) con más vueltas: el estiramiento se reparte en vez de concentrarse en una fila de aristas
    dW = np.abs(Wmat[ed[:, 0]] - Wmat[ed[:, 1]]).sum(1)
    hard = np.zeros(nv, bool); hard[ed[dW > CFG.get('w_hard', 0.5), 0]] = True; hard[ed[dW > CFG.get('w_hard', 0.5), 1]] = True
    hard &= mov
    band = hard.copy()
    for it in range(CFG.get('w_band_rings', 7)): band = band | ((A_ @ band.astype(float)) > 0)
    band &= mov
    for it in range(CFG.get('w_band_iters', 14)):
        Wn = (A_ @ Wmat) / deg[:, None]
        Wmat[band] = 0.5 * Wmat[band] + 0.5 * Wn[band]
    print('SUAVIZADO franja', int(band.sum()), 'vértices; bordes duros', int(hard.sum()))
    # puntos a menos de 3 mm entre sí (aristas diminutas, piezas que se tocan sin compartir aristas: audífonos, cuello de
    # la casaca, lazo) toman el mismo peso: con pesos distintos se estiraban como picos al girar o inclinar la cabeza
    if CFG.get('w_spatial', True):
        te_ = np.zeros(nv)
        for k, b in enumerate(bones):
            if b.startswith('Tail') or b.startswith('Ear'): te_ += Wmat[:, k]
        movi = np.nonzero(mov & (te_ < 0.05))[0]
        pr_ = cKDTree(P[movi]).query_pairs(CFG.get('w_micro', 0.003), output_type='ndarray')
        if len(pr_):
            dW_ = np.abs(Wmat[movi[pr_[:, 0]]] - Wmat[movi[pr_[:, 1]]]).sum(1)
            pr_ = pr_[dW_ > 0.1]
        if len(pr_):
            n_ = len(movi)
            Ac = csr_matrix((np.ones(2 * len(pr_)), (np.r_[pr_[:, 0], pr_[:, 1]], np.r_[pr_[:, 1], pr_[:, 0]])), shape=(n_, n_))
            dg_ = np.asarray(Ac.sum(1)).ravel(); hit_ = dg_ > 0
            Wl = Wmat[movi].copy()
            for it in range(CFG.get('w_micro_iters', 8)):
                Wn_ = (Ac @ Wl + Wl) / (dg_ + 1)[:, None]
                Wl[hit_] = Wn_[hit_]
            Wmat[movi] = Wl
            print('PESOS iguales en puntos casi juntos', int(hit_.sum()), 'vértices')
    for k, b in enumerate(bones): Wm[b] = Wmat[:, k]
    # el parche y las tapas del cuerpo toman los pesos (ya suaves) del cuerpo más cercano
    pm = (PART == 3) | (PART == 4) | (PART == 8)
    if pm.any():
        bi_ = np.nonzero((PART == 0) & ~pack_mask)[0]; d_, j_ = cKDTree(P[bi_]).query(P[pm])
        for b in bones: Wm[b][pm] = Wm[b][bi_[j_]]
        # el parche está en el costado del torso: nunca va con la cabeza ni las orejas; y se suaviza en su propia malla
        for b in bones:
            if b == 'Head' or b.startswith('Ear'): Wm[b][pm] = 0.0
        totp = sum(Wm[b] for b in bones); emp = pm & (totp < 0.05); Wm['Chest'][emp] = 1.0
        Wmat = np.stack([Wm[b] for b in bones], 1)
        for it in range(20):
            Wn = (A_ @ Wmat) / deg[:, None]
            Wmat[pm] = 0.5 * Wmat[pm] + 0.5 * Wn[pm]
        for k, b in enumerate(bones): Wm[b] = Wmat[:, k]

def WELD():
    # El borde del cuerpo junto al brazo sigue a un hueso ayudante (ArmRim) que copia el giro del brazo cuando el
    # giro es chico (reposo, respirar, hablar): la unión no se abre. Con giros grandes el ayudante se queda en pocos
    # grados y el brazo se separa sobre el parche del costado.
    for si_, (s_, sg_) in enumerate((('L', 1), ('R', -1))):
        armv = np.nonzero(PART == 1 + si_)[0]
        if len(armv) == 0: continue
        bodyv = np.nonzero((PART == 0) | (PART == 8) | (PART == 3 + si_))[0]
        d_, j_ = cKDTree(P[armv]).query(P[bodyv])
        rimm = d_ < 1e-6
        rim = bodyv[rimm]
        if len(rim) < 3: continue
        dr, _ = cKDTree(P[rim]).query(P[bodyv])
        bl = 1 - sstep_np(dr / CFG.get('rim_band', 0.022))
        # lo que va con la cabeza (pingüino, búho: el ala llega a la cabeza) no se ata al brazo
        bl = bl * (1 - np.clip(Wm['Head'][bodyv] / np.maximum(sum(Wm[b][bodyv] for b in bones), 1e-6), 0, 1))
        # piezas que sobresalen del cuerpo (correas, bufanda) no se arrastran con el borde: se estirarían como astillas
        if CFG.get('rim_skip_raised', True) and s_ in FIT:
            pb_ = P[bodyv]; th_ = np.arctan2(pb_[:, 0], -(pb_[:, 1] - CY)) * R0; rr_ = np.hypot(pb_[:, 0], pb_[:, 1] - CY)
            pvb = rr_ - FIT[s_](np.c_[th_, pb_[:, 2]])
            bl = bl * (1 - sstep_np((pvb - 0.004) / 0.006))
        rb = 'ArmRim' + s_
        for bn in bones:
            Wm[bn][bodyv] = (1 - bl) * Wm[bn][bodyv]
        Wm[rb][bodyv] += bl
        print('UNION', s_, 'borde', len(rim), 'franja', int((bl > 0.5).sum()))

# mochila: pieza suelta detrás del cuerpo (los índices cambiaron)
bm_ = bmesh.new(); bm_.from_mesh(me); bm_.verts.ensure_lookup_table()
ed = np.array([(e.verts[0].index, e.verts[1].index) for e in bm_.edges]); bm_.free()
A_ = csr_matrix((np.ones(len(ed)), (ed[:, 0], ed[:, 1])), shape=(nv, nv))
ncomp, labv = connected_components(A_, directed=False)
pack_mask = np.zeros(nv, bool)
cnts = np.bincount(labv, minlength=ncomp)
for c in np.argsort(-cnts)[1:]:
    pi_ = np.nonzero(labv == c)[0]
    if len(pi_) <= 300: break
    if cap_mask[pi_].any() or plug_mask[pi_].any(): continue
    cc = P[pi_].mean(0)
    if cc[1] > 0.08 and hip_z - 0.1 < cc[2] < neck_z + 0.08:
        pack_mask[pi_] = True
bpy.ops.object.select_all(action='DESELECT')
armob.select_set(True); bpy.context.view_layer.objects.active = armob
bpy.ops.object.mode_set(mode='EDIT')
for s_ in ('L', 'R'):
    src_b = armob.data.edit_bones['Arm' + s_]
    nb_ = armob.data.edit_bones.new('ArmRim' + s_); nb_.head = src_b.head.copy(); nb_.tail = src_b.tail.copy(); nb_.roll = src_b.roll
    nb_.parent = armob.data.edit_bones['Chest']; nb_.use_deform = True
bpy.ops.object.mode_set(mode='OBJECT')
bones = bones + ['ArmRimL', 'ArmRimR']
bpy.ops.object.select_all(action='DESELECT')
ob.select_set(True); armob.select_set(True); bpy.context.view_layer.objects.active = armob
bpy.ops.object.parent_set(type='ARMATURE_AUTO')
Wm = {b: np.zeros(nv) for b in bones}
gidx = {g.index: g.name for g in ob.vertex_groups}
for v in me.vertices:
    for g in v.groups:
        n_ = gidx[g.group]
        if n_ in Wm: Wm[n_][v.index] = g.weight
# brazo: rígido con su hueso; la mano (lo más lejos del hombro) con el hueso de la mano, con transición suave
for si, (s, sg) in enumerate((('L', 1), ('R', -1))):
    side = (PART == 1 + si) | (PART == 5 + si) | (PART == 9 + si)
    Q_ = P[side]
    shoulder = np.array([sg * arm_x0, arm_y, sh_z])
    far = Q_[np.argmax(np.linalg.norm(Q_ - shoulder, axis=1))]
    u_ = far - shoulder; L_ = np.linalg.norm(u_); u_ /= L_
    t_ = ((Q_ - shoulder) @ u_) / L_
    hw = sstep_np((t_ - CFG.get('elbow_t0', 0.40)) / CFG.get('elbow_w', 0.38))   # codo: transición ancha
    for bn in bones: Wm[bn][side] = 0.0
    Wm['Hand' + s][side] = hw; Wm['Arm' + s][side] = 1 - hw
    # la falda copia los pesos del vértice del borde del que sale (misma altura y mismo ángulo): no se estira
    sk = np.nonzero(PART == 9 + si)[0]; pc = np.nonzero(PART == 1 + si)[0]
    if len(sk) and len(pc) and not CFG.get('arm_wall', True):
        dsk, jsk = cKDTree(P[pc]).query(P[sk])
        rimv = pc[np.unique(jsk[dsk < 1e-6])]
        if len(rimv):
            thr = np.arctan2(x[rimv], -(y[rimv] - CY)) * R0; ths = np.arctan2(x[sk], -(y[sk] - CY)) * R0
            _, jr = cKDTree(np.c_[thr, z[rimv]]).query(np.c_[ths, z[sk]])
            for bn in ('Arm' + s, 'Hand' + s):
                Wm[bn][sk] = Wm[bn][rimv[jr]]
Wm['ArmRimL'][:] = 0.0; Wm['ArmRimR'][:] = 0.0
# el parche toma los pesos del cuerpo más cercano (sin brazos)
body_ok = (PART == 0)
kd_ = cKDTree(P[body_ok]); bi = np.nonzero(body_ok)[0]
for k_ in np.nonzero(plug_mask)[0]:
    j_ = bi[kd_.query(P[k_])[1]]
    for bn in bones: Wm[bn][k_] = Wm[bn][j_] if not (bn.startswith('Arm') or bn.startswith('Hand')) else 0.0
