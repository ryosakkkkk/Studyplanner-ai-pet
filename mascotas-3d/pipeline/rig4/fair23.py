# v0.23 — borde parejo (se ejecuta dentro de armfix.py, después del corte suave).
# El corte entre brazo y cuerpo sigue la curva de nivel de un campo, pero donde la malla tiene triángulos largos o
# arrugas la curva todavía ondula. Aquí se alisa como CURVA: cada vértice del borde se acerca al punto que le toca
# entre sus dos vecinos del borde (pesos 1/largo: casi no se desliza a lo largo), varias vueltas, con paso de Taubin
# para que el lazo no se encoja. El plano del hombro y los cruces quedan fijos. Brazo y cuerpo comparten todavía esos
# vértices, así que en reposo no se abre nada; la forma de la boca (MouthOpen) se mueve igual.
_a1 = ARMF[PAIRS[:, 0]]; _a2 = ARMF[PAIRS[:, 1]]
_m = (_a1 != _a2) & ((_a1 > 0) | (_a2 > 0))
_ek = PKEY[_m]; _ed = np.c_[_ek // nv, _ek % nv]
_side_of = np.maximum(_a1[_m], _a2[_m])
_nb = {}
for (a_, b_), sd_ in zip(_ed, _side_of):
    _nb.setdefault(int(a_), []).append(int(b_)); _nb.setdefault(int(b_), []).append(int(a_))
_rv = np.array(sorted(_nb.keys()), int)
_pin = np.zeros(nv, bool)
for v_ in _rv:
    if len(_nb[v_]) != 2: _pin[v_] = True
for s_, (sh0_, u2_, ct_) in AXIS.items():
    tv_ = (P - sh0_) @ u2_ / (u2_ @ u2_)
    _pin |= np.abs(tv_ - ct_) < 1e-4
_free = np.array([v_ for v_ in _rv if not _pin[v_]], int)
_P0 = P.copy(); _Pn = P.copy()
if len(_free):
    _n1 = np.array([_nb[v_][0] for v_ in _free]); _n2 = np.array([_nb[v_][1] for v_ in _free])
    _lam, _mu = CFG.get('fair_lam', 0.6), CFG.get('fair_mu', -0.62)
    for it in range(CFG.get('fair_iters', 24)):
        for k_ in (_lam, _mu):
            l1 = np.linalg.norm(_Pn[_n1] - _Pn[_free], axis=1) + 1e-5; l2 = np.linalg.norm(_Pn[_n2] - _Pn[_free], axis=1) + 1e-5
            tgt = (_Pn[_n1] / l1[:, None] + _Pn[_n2] / l2[:, None]) / (1 / l1 + 1 / l2)[:, None]
            _Pn[_free] = _Pn[_free] + k_ * (tgt - _Pn[_free])
    # unas vueltas finales solo de acercamiento (quitan el diente de sierra que Taubin conserva a medias)
    for it in range(CFG.get('fair_post', 4)):
        l1 = np.linalg.norm(_Pn[_n1] - _Pn[_free], axis=1) + 1e-5; l2 = np.linalg.norm(_Pn[_n2] - _Pn[_free], axis=1) + 1e-5
        tgt = (_Pn[_n1] / l1[:, None] + _Pn[_n2] / l2[:, None]) / (1 / l1 + 1 / l2)[:, None]
        _Pn[_free] = _Pn[_free] + 0.5 * (tgt - _Pn[_free])
    _dv = _Pn - _P0; _dl = np.linalg.norm(_dv, axis=1)
    _mx = CFG.get('fair_max', 0.010)
    _sc = np.where(_dl > _mx, _mx / np.maximum(_dl, 1e-12), 1.0)
    _dv *= _sc[:, None]
    bm_ = bmesh.new(); bm_.from_mesh(me); bm_.verts.ensure_lookup_table()
    _layers = list(bm_.verts.layers.shape.values())
    for v_ in _free:
        d_ = Vector(_dv[v_]); bv = bm_.verts[int(v_)]
        bv.co = bv.co + d_
        for ly in _layers: bv[ly] = bv[ly] + d_
    bm_.to_mesh(me); bm_.free(); me.update()
    print('BORDE PAREJO', 'vértices del borde', len(_rv), 'movidos', len(_free), 'fijos', int(_pin[_rv].sum()),
          'mm medio', round(float(np.linalg.norm(_dv[_free], axis=1).mean()) * 1000, 2), 'máx', round(float(np.linalg.norm(_dv[_free], axis=1).max()) * 1000, 2))
    # ---- volver a leer lo que depende de las posiciones (la topología no cambió)
    P = np.array([v.co[:] for v in me.vertices]); x, y, z = P[:, 0], P[:, 1], P[:, 2]
    FC = P[FV].mean(1)
    FN = np.cross(P[FV[:, 1]] - P[FV[:, 0]], P[FV[:, 2]] - P[FV[:, 0]])
    FN /= np.maximum(np.linalg.norm(FN, axis=1), 1e-12)[:, None]
    VN = np.array([v.normal[:] for v in me.vertices])
    TH_ = np.arctan2(x, -(y - CY)); RR = np.hypot(x, y - CY)
    S_ = TH_ * R0
    RAD = np.stack([x, y - CY], 1) / np.maximum(RR, 1e-6)[:, None]
    OUTW = (VN[:, 0] * RAD[:, 0] + VN[:, 1] * RAD[:, 1]) > 0.15
    for si, (s, sg) in enumerate((('L', 1), ('R', -1))):
        pv = np.full(nv, np.nan); side = x * sg > -0.02
        pv[side] = RR[side] - FIT[s](np.c_[S_[side], z[side]])
        PV[s] = pv
