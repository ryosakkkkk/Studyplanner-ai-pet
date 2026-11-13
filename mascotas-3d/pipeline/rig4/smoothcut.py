# Borde suave entre brazo y cuerpo (se ejecuta dentro de armfix.py).
# La separación por caras deja un borde en zigzag (los dientes de los triángulos), que se ve como «rompecabezas» al
# mover el brazo. Aquí el borde se vuelve a trazar como la curva de nivel 0,5 de un campo suave (la pertenencia al
# brazo, suavizada sobre la malla), y la malla se corta exactamente por esa curva (se parten aristas y caras; UV, pesos
# y forma de la boca se interpolan). En el gato y la coneja el plano del hombro queda fijo.
bm_ = bmesh.new(); bm_.from_mesh(me); bm_.verts.ensure_lookup_table(); bm_.faces.ensure_lookup_table()
EV = np.unique(np.sort(np.r_[FV[:, [0, 1]], FV[:, [1, 2]], FV[:, [2, 0]]], 1), axis=0)
AV = csr_matrix((np.ones(2 * len(EV)), (np.r_[EV[:, 0], EV[:, 1]], np.r_[EV[:, 1], EV[:, 0]])), shape=(nv, nv))
degv = np.maximum(np.asarray(AV.sum(1)).ravel(), 1)
FIELDS = {}
for sid, (s, sg) in ((1, ('L', 1)), (2, ('R', -1))):
    m_ = (ARMF == sid).astype(float)
    f0 = np.zeros(nv); c0 = np.zeros(nv)
    np.add.at(f0, FV.ravel(), np.repeat(m_, 3)); np.add.at(c0, FV.ravel(), 1)
    f = f0 / np.maximum(c0, 1)
    pin = np.zeros(nv, bool); pinv = np.zeros(nv)
    if s in AXIS:
        sh0_, u2_, ct_ = AXIS[s]
        tv_ = (P - sh0_) @ u2_ / (u2_ @ u2_)
        onp = np.abs(tv_ - ct_) < 1e-4
        bnd = (f > 0) & (f < 1)
        pin |= (tv_ < ct_ - 1e-4); pinv[tv_ < ct_ - 1e-4] = 0.0
        pin |= onp & bnd; pinv[onp & bnd] = 0.5
    # solo cerca del borde: lejos, el campo ya es 0 o 1
    near_b = (f > 0) & (f < 1)
    for it in range(3): near_b = near_b | (AV @ near_b.astype(float) > 0)
    for it in range(CFG.get('cut_smooth', 6)):
        fn_ = (AV @ f) / degv
        f = np.where(near_b, 0.4 * f + 0.6 * fn_, f)
        f[pin] = pinv[pin]
    FIELDS[sid] = f
f1, f2 = FIELDS[1], FIELDS[2]
vf = {v: (f1[v.index], f2[v.index]) for v in bm_.verts}
newv = set()
for e in list(bm_.edges):
    a_, b_ = e.verts
    fa, fb = vf[a_], vf[b_]
    for k in (0, 1):
        da, db = fa[k] - 0.5, fb[k] - 0.5
        if da * db < 0 and abs(da) > 1e-6 and abs(db) > 1e-6:
            t = da / (da - db)
            ne, nvx = bmesh.utils.edge_split(e, a_, t)
            vf[nvx] = tuple(fa[j] + t * (fb[j] - fa[j]) for j in (0, 1))
            vf[nvx] = tuple(0.5 if j == k else vf[nvx][j] for j in (0, 1))
            newv.add(nvx)
            break
iso = newv | {v for v in bm_.verts if abs(vf[v][0] - 0.5) < 1e-6 or abs(vf[v][1] - 0.5) < 1e-6}
nsplit = 0
for fc in list(bm_.faces):
    vs = [v for v in fc.verts if v in iso]
    if len(vs) != 2: continue
    a_, b_ = vs
    if any(b_ in (e.other_vert(a_),) for e in a_.link_edges): continue
    try:
        bmesh.utils.face_split(fc, a_, b_); nsplit += 1
    except Exception:
        pass
bmesh.ops.triangulate(bm_, faces=[fc for fc in bm_.faces if len(fc.verts) != 3])
bm_.verts.index_update()
FVAL = np.array([vf.get(v, (0.0, 0.0)) for v in bm_.verts])
bm_.to_mesh(me); bm_.free(); me.update()
print('CORTE SUAVE', 'vertices nuevos', len(newv), 'caras partidas', nsplit)

# ---- volver a leer todo lo que depende de la malla
nv_old = nv; P_old = P.copy(); pack_old = pack_mask.copy()
nv = len(me.vertices)
P = np.array([v.co[:] for v in me.vertices]); x, y, z = P[:, 0], P[:, 1], P[:, 2]
dpk, jpk = cKDTree(P_old).query(P); pack_mask = pack_old[jpk]
nL = len(me.loops)
uvL = np.zeros((nL, 2)); me.uv_layers.active.data.foreach_get('uv', uvL.ravel())
lv = np.zeros(nL, int); me.loops.foreach_get('vertex_index', lv)
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
EK = np.sort(np.stack([FV[:, [0, 1]], FV[:, [1, 2]], FV[:, [2, 0]]], 1).reshape(-1, 2), 1)
key = EK[:, 0] * nv + EK[:, 1]; fid = np.repeat(np.arange(npoly), 3)
o_ = np.argsort(key, kind='stable'); ks = key[o_]; fs = fid[o_]
same = ks[1:] == ks[:-1]
PAIRS = np.stack([fs[:-1][same], fs[1:][same]], 1); PKEY = ks[1:][same]
ADJ = csr_matrix((np.ones(2 * len(PAIRS)), (np.r_[PAIRS[:, 0], PAIRS[:, 1]], np.r_[PAIRS[:, 1], PAIRS[:, 0]])), shape=(npoly, npoly))
TH_ = np.arctan2(x, -(y - CY)); RR = np.hypot(x, y - CY)
S_ = TH_ * R0
RAD = np.stack([x, y - CY], 1) / np.maximum(RR, 1e-6)[:, None]
OUTW = (VN[:, 0] * RAD[:, 0] + VN[:, 1] * RAD[:, 1]) > 0.15
# lado de cada cara según el campo
fm = FVAL[FV].mean(1)
ARMF = np.zeros(npoly, int)
ARMF[fm[:, 0] > 0.5] = 1
ARMF[(fm[:, 1] > 0.5) & (ARMF == 0)] = 2
for sid in (1, 2):
    idx_, lab_, n_ = comps(ARMF == sid)
    if n_:
        sz_ = np.bincount(lab_, minlength=n_)
        small = idx_[~np.isin(lab_, np.nonzero(sz_ >= 0.03 * sz_.max())[0])]
        ARMF[small] = 0
# alturas respecto del cuerpo ajustado (para el parche)
for si, (s, sg) in enumerate((('L', 1), ('R', -1))):
    pv = np.full(nv, np.nan); side = x * sg > -0.02
    pv[side] = RR[side] - FIT[s](np.c_[S_[side], z[side]])
    PV[s] = pv
print('BRAZOS tras corte suave', int((ARMF == 1).sum()), int((ARMF == 2).sum()))
