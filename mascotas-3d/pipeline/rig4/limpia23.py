# v0.23 — limpieza del conjunto de caras del brazo (se ejecuta dentro del bucle por lado de armfix.py).
# El recorte anterior dejaba tres defectos que al mover el brazo se veían como picos y fragmentos sueltos:
#  1) lenguas largas y finas en el borde (triángulos alargados que seguían por el costado),
#  2) piezas ajenas unidas por un puente delgado (banda de los audífonos del pingüino, ribete de la casaca del búho,
#     dobladillo de la casaca del zorro),
#  3) islas chicas lejos del brazo.
# Aquí: apertura geodésica (se quita lo que mide menos de 2·r de ancho), una sola pieza principal más las piezas
# secundarias que estén dentro del volumen del brazo (la mano dentro de la manga), y nada por encima del hombro.
from scipy.sparse.csgraph import dijkstra as _dijkstra

def _vgraph():
    ev = np.unique(np.sort(np.r_[FV[:, [0, 1]], FV[:, [1, 2]], FV[:, [2, 0]]], 1), axis=0)
    ln = np.linalg.norm(P[ev[:, 0]] - P[ev[:, 1]], axis=1) + 1e-9
    return csr_matrix((np.r_[ln, ln], (np.r_[ev[:, 0], ev[:, 1]], np.r_[ev[:, 1], ev[:, 0]])), shape=(nv, nv))

def _geo(G, src, allow):
    """Distancia geodésica desde los vértices `src` caminando solo por los vértices `allow`."""
    idx = np.nonzero(allow)[0]
    out = np.full(nv, np.inf)
    s_loc = np.nonzero(src[idx])[0]
    if len(s_loc) == 0 or len(idx) == 0: return out
    sub = G[idx][:, idx]
    d_ = _dijkstra(sub, directed=False, indices=s_loc, min_only=True)
    out[idx] = d_
    return out

_G = _vgraph()
_area = 0.5 * np.linalg.norm(np.cross(P[FV[:, 1]] - P[FV[:, 0]], P[FV[:, 2]] - P[FV[:, 0]]), axis=1)
_n0 = int(armF.sum())
# ---- 1) apertura geodésica
_r = 0.0 if CFG.get('tube') else CFG.get('open_r', 0.007)
if _r > 0 and armF.sum() > 50:
    armV = np.zeros(nv, bool); armV[np.unique(FV[armF])] = True
    othV = np.zeros(nv, bool); othV[np.unique(FV[~armF])] = True
    rimV = armV & othV
    din = _geo(_G, rimV, armV)
    core = armV & (din >= _r)
    if core.sum() > 20:
        dc = _geo(_G, core, armV)
        keepV = armV & (dc <= _r * 1.25)
        armF &= keepV[FV].all(1)
# ---- 2) eje robusto del brazo y piezas
dirh_ = hand_ - shoulder_; dirh_ = dirh_ / np.linalg.norm(dirh_)
idxc, labc, ncp = comps(armF)
if ncp:
    ar_c = np.bincount(labc, weights=_area[idxc], minlength=ncp)
    mainc = int(np.argmax(ar_c))
    mf = idxc[labc == mainc]
    pr = FC[mf] @ dirh_
    far_ = mf[pr >= np.quantile(pr, 0.92)]
    tip_ = np.average(FC[far_], axis=0, weights=_area[far_] + 1e-12)
    uax = tip_ - shoulder_; Lax = float(np.linalg.norm(uax)); uax = uax / Lax
    dF = (FC - shoulder_) @ uax
    rF = np.linalg.norm((FC - shoulder_) - np.outer(dF, uax), axis=1)
    o_ = np.argsort(dF[mf]); cw = np.cumsum(_area[mf][o_]); cw /= cw[-1]
    d_lo = float(dF[mf][o_][np.searchsorted(cw, 0.03)]); d_hi = float(dF[mf].max())
    R90 = float(np.quantile(rF[mf], 0.90))
    keep_c = [mainc]; drop_info = []
    for c_ in range(ncp):
        if c_ == mainc: continue
        fc_ = idxc[labc == c_]
        cen_c = np.average(FC[fc_], axis=0, weights=_area[fc_] + 1e-12)
        dcn = float((cen_c - shoulder_) @ uax); rcn = float(np.linalg.norm((cen_c - shoulder_) - dcn * uax))
        ok_ = (ar_c[c_] >= CFG.get('comp_min', 0.04) * ar_c[mainc]) and (d_lo - 0.005 <= dcn <= d_hi + 0.03) and (rcn <= 1.15 * R90 + 0.005)
        if ok_: keep_c.append(c_)
        else: drop_info.append((len(fc_), round(dcn, 3), round(rcn, 3)))
    armF[:] = False; armF[idxc[np.isin(labc, keep_c)]] = True
    # ---- 3) nada por encima del hombro (en las mascotas sin corte plano)
    if CFG.get('cut_t') is None:
        armF &= ~(dF < CFG.get('d_min', -0.05))
    _mid = mf[(dF[mf] > 0.45 * Lax) & (dF[mf] < 0.85 * Lax)]
    Rt_ = float(np.quantile(rF[_mid], 0.8)) if len(_mid) > 10 else R90
    # ---- 4) línea central del brazo (centros de dos tramos de la parte limpia, lejos del hombro) y recorte por radio:
    #         lo que queda mucho más lejos de esa línea que el propio brazo (triángulos de la espalda, del cuello) no es brazo
    s1 = mf[(dF[mf] > 0.42 * Lax) & (dF[mf] < 0.62 * Lax)]; s2 = mf[(dF[mf] > 0.70 * Lax) & (dF[mf] < 0.90 * Lax)]
    nclip = 0
    if len(s1) > 10 and len(s2) > 10:
        c1 = np.average(FC[s1], axis=0, weights=_area[s1] + 1e-12); c2 = np.average(FC[s2], axis=0, weights=_area[s2] + 1e-12)
        ucl = c2 - c1; ucl = ucl / np.linalg.norm(ucl)
        qc = FC - c1; tc = qc @ ucl; rc = np.linalg.norm(qc - np.outer(tc, ucl), axis=1)
        mm_ = mf[(dF[mf] > 0.40 * Lax) & (dF[mf] < 0.90 * Lax)]
        o_ = np.argsort(rc[mm_]); cw = np.cumsum(_area[mm_][o_]); cw /= cw[-1]
        Rm = float(rc[mm_][o_][np.searchsorted(cw, 0.5)])
        if not CFG.get('tube'):
            fr = np.clip(1 - dF / max(Lax, 1e-6), 0, 1)                       # 1 en el hombro, 0 en la punta
            Rclip = (CFG.get('r_clip', 1.45) + CFG.get('r_clip_top', 0.5) * fr) * Rm + 0.012
            far_ = armF & (rc > Rclip)
            nclip = int(far_.sum()); armF &= ~far_
            idx8, lab8, n8 = comps(armF)
            if n8 > 1:
                ar8 = np.bincount(lab8, weights=_area[idx8], minlength=n8)
                armF[:] = False; armF[idx8[lab8 == int(np.argmax(ar8))]] = True
    else:
        c1 = shoulder_ + uax * 0.5 * Lax; ucl = uax.copy(); Rm = R90
    # ---- 5) islas: caras que no quedaron en el brazo pero están rodeadas por él (una mancha de otro color en la manga,
    #         un parche que la regla de color sacó). Si se quedan en el cuerpo, al levantar el brazo flotan sueltas.
    nisl = 0
    if armF.sum() > 50:
        idx9, lab9, n9 = comps(~armF)
        if n9 > 1:
            ar9 = np.bincount(lab9, weights=_area[idx9], minlength=n9)
            arm_area = float(_area[armF].sum())
            for c_ in range(n9):
                if ar9[c_] > CFG.get('isla_max', 0.25) * arm_area: continue
                mm_ = np.zeros(npoly, bool); mm_[idx9[lab9 == c_]] = True
                ring_ = grow(mm_) & ~mm_
                if ring_.any() and armF[ring_].all():
                    armF |= mm_; nisl += int(mm_.sum())
    ARMAX[s] = (shoulder_.copy(), uax.copy(), Lax, d_lo, d_hi, Rt_, tip_.copy(), c1.copy(), ucl.copy(), Rm)
    print('LINEA CENTRAL', s, 'punto', np.round(c1, 3), 'dirección', np.round(ucl, 2), 'radio medio', round(Rm, 3), 'recortadas por radio', nclip)
    print('LIMPIEZA', s, 'caras', _n0, '->', int(armF.sum()), '| islas devueltas', nisl, '| piezas', ncp, 'conservadas', len(keep_c), 'quitadas', drop_info[:6],
          '| eje', np.round(uax, 2), 'largo', round(Lax, 3), 'd', round(d_lo, 3), round(d_hi, 3), 'radio90', round(R90, 3))
