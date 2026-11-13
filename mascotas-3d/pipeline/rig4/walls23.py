# v0.23 — paredes de la unión brazo/cuerpo (se ejecuta dentro de armfix.py, en lugar del parche por ajuste y la falda).
# Comprobado en los seis modelos: el brazo es un relieve SIN cara interior y el cuerpo NO tiene superficie debajo
# (al quitar el brazo queda un hueco por el que se ve el fondo). Las dos superficies que faltan se reconstruyen sobre
# el mismo borde (ya parejo), cada una con su forma y su color:
#   - pared del brazo: cierra el brazo por debajo; abombada hacia el cuerpo para que el brazo se vea redondo; color de
#     la manga a la misma altura del brazo (franjas del puño incluidas).
#   - pared del cuerpo: cierra el costado a ras de la ropa que rodea el hueco, con la curvatura que traen los bordes y
#     el color del borde rellenado hacia el centro; nunca sobresale del brazo en reposo.
# Las dos comparten el borde con la malla original: no hay rendija en reposo ni al mover.
from scipy.spatial import Delaunay as _Del
from mathutils.bvhtree import BVHTree as _BVH

_keyp = np.round(P / 1e-6).astype(np.int64)
_, REP = np.unique(_keyp, axis=0, return_inverse=True); REP = REP.ravel()
FIRST = {}
for v_ in range(nv): FIRST.setdefault(int(REP[v_]), v_)
_AREA = 0.5 * np.linalg.norm(np.cross(P[FV[:, 1]] - P[FV[:, 0]], P[FV[:, 2]] - P[FV[:, 0]]), axis=1)

def _walk_loops(bed):
    nbr = {}
    for a_, b_ in bed:
        nbr.setdefault(int(a_), []).append(int(b_)); nbr.setdefault(int(b_), []).append(int(a_))
    used = set(); loops = []
    for a0, b0 in bed:
        e0 = (int(min(a0, b0)), int(max(a0, b0)))
        if e0 in used: continue
        path = [int(a0)]; pos = {int(a0): 0}; cur = int(b0); used.add(e0)
        for it in range(200000):
            if cur in pos:
                k_ = pos[cur]; sub = path[k_:]
                if len(sub) >= 8: loops.append(sub)
                for v_ in sub[1:]: pos.pop(v_, None)
                path = path[:k_ + 1]
            else:
                pos[cur] = len(path); path.append(cur)
            nxt = None
            for c_ in nbr[cur]:
                ek = (min(cur, c_), max(cur, c_))
                if ek not in used:
                    nxt = c_; break
            if nxt is None: break
            used.add((min(cur, nxt), max(cur, nxt))); cur = nxt
    return loops, nbr

def _color_strip(cols):
    """Tira de textura (una fila de colores a lo largo del brazo). Devuelve función d01 -> uv."""
    n_ = len(cols); padw = 4; hh = 6
    strip = np.repeat(cols[None, :, :], hh, axis=0)
    strip = np.pad(strip, ((padw, padw), (padw, padw), (0, 0)), mode='edge')
    ox_, oy_ = alloc(strip.shape[1], strip.shape[0])
    TEX[oy_:oy_ + strip.shape[0], ox_:ox_ + strip.shape[1], :3] = strip
    flat_normal(ox_, oy_, strip.shape[1], strip.shape[0])
    return lambda d01: ((ox_ + padw + 0.5 + np.clip(d01, 0, 1) * (n_ - 1)) / TW, (oy_ + padw + hh / 2) / TH)

def build_walls(si, s, sg):
    armF = ARMF == si + 1
    if armF.sum() < 50: return
    av = np.unique(FV[armF]); ca_ = np.average(FC[armF], axis=0, weights=_AREA[armF] + 1e-12)
    FVa = REP[FV[armF]]
    ea_ = np.sort(np.r_[FVa[:, [0, 1]], FVa[:, [1, 2]], FVa[:, [2, 0]]], 1)
    ue_, cnt_ = np.unique(ea_, axis=0, return_counts=True)
    bed = ue_[cnt_ == 1]
    a1 = ARMF[PAIRS[:, 0]] == si + 1; a2 = ARMF[PAIRS[:, 1]] == si + 1
    ck = PKEY[a1 != a2]
    cutset = set(tuple(sorted((int(REP[k_ // nv]), int(REP[k_ % nv])))) for k_ in ck)
    loops, nbr = _walk_loops(bed)
    # ---- colores: manga por tramos a lo largo del brazo; cuerpo en cada vértice del borde
    jt, uax, Lax, d_lo, d_hi = ARMAX.get(s, (np.array([sg * arm_x0, arm_y, sh_z]), np.array([sg * 0.3, 0, -0.95]), 0.2, 0.0, 0.2))[:5]
    dFa = (FC[armF] - jt) @ uax
    d_a, d_b = float(dFa.min()), float(dFa.max())
    NB = 48
    bins = np.clip(((dFa - d_a) / max(d_b - d_a, 1e-6) * NB).astype(int), 0, NB - 1)
    fcol_a = FCOL[armF]; w_a = _AREA[armF] + 1e-12
    band = np.full((NB, 3), np.nan)
    for b_ in range(NB):
        m_ = bins == b_
        if m_.sum() >= 3:
            c_ = fcol_a[m_]; ww = w_a[m_]
            # color dominante del tramo (dos grupos; gana el de más área): la franja del puño no se mezcla con la manga
            lab2 = (np.linalg.norm(c_ - c_[np.argmax(c_.sum(1))], axis=1) > np.linalg.norm(c_ - c_[np.argmin(c_.sum(1))], axis=1)).astype(int)
            for it in range(6):
                if lab2.min() == lab2.max(): break
                ce = [np.average(c_[lab2 == g], axis=0, weights=ww[lab2 == g]) for g in (0, 1)]
                nl = (np.linalg.norm(c_ - ce[1], axis=1) < np.linalg.norm(c_ - ce[0], axis=1)).astype(int)
                if (nl == lab2).all(): break
                lab2 = nl
            g_ = int(ww[lab2 == 1].sum() > ww[lab2 == 0].sum())
            band[b_] = np.average(c_[lab2 == g_], axis=0, weights=ww[lab2 == g_])
    ok_ = ~np.isnan(band[:, 0])
    if ok_.sum() >= 2:
        for k in range(3): band[:, k] = np.interp(np.arange(NB), np.nonzero(ok_)[0], band[ok_, k])
    else:
        band[:] = np.median(fcol_a, 0)
    band[1:-1] = 0.25 * band[:-2] + 0.5 * band[1:-1] + 0.25 * band[2:]
    band_pal = band.copy()
    if CFG.get('band_mode', 'local') == 'liso':
        # brazo de un solo color (ala, aleta, brazo del panda): la pared lleva el color dominante de todo el brazo,
        # con una variación suave a lo largo; sin esto los brillos pintados en la textura se copiaban como franjas.
        c_ = fcol_a; ww = w_a
        lab2 = (np.linalg.norm(c_ - c_[np.argmax(c_.sum(1))], axis=1) > np.linalg.norm(c_ - c_[np.argmin(c_.sum(1))], axis=1)).astype(int)
        for it in range(8):
            if lab2.min() == lab2.max(): break
            ce = [np.average(c_[lab2 == g], axis=0, weights=ww[lab2 == g]) for g in (0, 1)]
            nl = (np.linalg.norm(c_ - ce[1], axis=1) < np.linalg.norm(c_ - ce[0], axis=1)).astype(int)
            if (nl == lab2).all(): break
            lab2 = nl
        g_ = int(ww[lab2 == 1].sum() > ww[lab2 == 0].sum()) if lab2.min() != lab2.max() else int(lab2[0])
        dom = np.average(c_[lab2 == g_], axis=0, weights=ww[lab2 == g_])
        sm = band.copy()
        for it in range(40): sm[1:-1] = 0.25 * sm[:-2] + 0.5 * sm[1:-1] + 0.25 * sm[2:]
        band = (1 - CFG.get('band_liso', 0.75)) * sm + CFG.get('band_liso', 0.75) * dom[None, :]
    uv_band = _color_strip(band)
    cB = {}; nBn = {}
    for f in np.nonzero(ARMF == 0)[0]:
        for k in range(3):
            r_ = int(REP[FV[f][k]])
            if r_ in nbr:
                cB.setdefault(r_, []).append(lcol[ls_[f] + k])
                nBn[r_] = nBn.get(r_, np.zeros(3)) + FN[f] * _AREA[f]
    skin_fb = np.median(np.array([np.mean(v_, 0) for v_ in cB.values()]), 0) if cB else np.median(fcol_a, 0)
    # Color LIMPIO del cuerpo para la pared: junto al borde la textura trae la sombra pintada del brazo y restos de su
    # color (en el panda, negro sobre blanco). Se toma el color del cuerpo un poco más afuera (1,2 a 2,8 cm del borde,
    # caminando sobre el cuerpo), sin las piezas que sobresalen (correas) ni la mochila, y cada punto del borde recibe
    # el color de lo que tiene enfrente. Así el costado bajo el brazo continúa la ropa o el pelaje que lo rodea.
    cFar = {}
    if CFG.get('bwall_far', True):
        fb_ = np.nonzero(ARMF == 0)[0]
        eb_ = np.unique(np.sort(np.r_[FV[fb_][:, [0, 1]], FV[fb_][:, [1, 2]], FV[fb_][:, [2, 0]]], 1), axis=0)
        lb_ = np.linalg.norm(P[eb_[:, 0]] - P[eb_[:, 1]], axis=1) + 1e-9
        Gb = csr_matrix((np.r_[lb_, lb_], (np.r_[eb_[:, 0], eb_[:, 1]], np.r_[eb_[:, 1], eb_[:, 0]])), shape=(nv, nv))
        rimv_ = np.array(sorted(FIRST[r_] for r_ in nbr.keys()), int)
        from scipy.sparse.csgraph import dijkstra as _dj
        dd_, pr_, src_ = _dj(Gb, directed=False, indices=rimv_, min_only=True, return_predecessors=True, limit=0.09)
        d1_, d2_ = CFG.get('bwall_far_d', [0.02, 0.06])
        bodyv = np.zeros(nv, bool); bodyv[np.unique(FV[fb_])] = True
        far_ = (dd_ >= d1_) & (dd_ <= d2_) & ~pack_mask & bodyv
        # relieve LOCAL: lo que sobresale de la superficie alisada (correas, bufanda) no es el cuerpo que queda bajo el
        # brazo. Se alisa la malla del cuerpo unas vueltas y se mide cuánto queda cada vértice por encima.
        Ab_ = (Gb > 0).astype(float); dgb_ = np.maximum(np.asarray(Ab_.sum(1)).ravel(), 1)
        Ps_ = P.copy()
        for it in range(CFG.get('bump_it', 10)):
            Pn_ = (Ab_ @ Ps_) / dgb_[:, None]
            Ps_[bodyv] = 0.5 * Ps_[bodyv] + 0.5 * Pn_[bodyv]
        hb_ = ((P - Ps_) * VN).sum(1)
        pvs = hb_
        # piezas ajenas por color (correas de la mochila, bufanda): reglas por mascota, como las del recorte del brazo
        if CFG.get('bwall_skip'):
            hsv_ = np.array([colorsys.rgb_to_hsv(*np.clip(c_, 0, 1)) for c_ in vcol]); hue_ = hsv_[:, 0] * 360
            skip_ = np.zeros(nv, bool)
            for rule in CFG['bwall_skip']:
                h0, h1 = rule['h']
                inh = ((hue_ >= h0) & (hue_ <= h1)) if h0 <= h1 else ((hue_ >= h0) | (hue_ <= h1))
                skip_ |= inh & (hsv_[:, 1] >= rule.get('s', 0)) & (hsv_[:, 1] <= rule.get('smax', 1)) & (hsv_[:, 2] >= rule.get('v', 0)) & (hsv_[:, 2] <= rule.get('vmax', 1))
            far_ &= ~skip_
        if far_.sum() >= 6:
            far_ &= hb_ < (np.percentile(hb_[far_], 25) + CFG.get('bwall_far_raised', 0.005))
        acc_ = {}
        for v_ in np.nonzero(far_)[0]:
            acc_.setdefault(int(REP[src_[v_]]), []).append(vcol[v_])
        for r_, cs_ in acc_.items():
            cs_ = np.array(cs_)
            if len(cs_) >= 4:
                # la mitad más clara: las sombras pintadas junto a correas y pliegues no oscurecen el color
                vv_ = cs_.max(1); cs_ = cs_[vv_ >= np.median(vv_)]
            cFar[r_] = cs_.mean(0)
        _m0 = (dd_ >= d1_) & (dd_ <= d2_) & ~pack_mask & bodyv
        if _m0.any(): print('  relieve mm en la franja lejana, percentiles 5 20 50 80 95:', np.round(np.percentile(pvs[_m0], [5, 20, 50, 80, 95]) * 1000, 1))
        print('COLOR LIMPIO', s, 'borde', len(rimv_), '| a distancia', int(((dd_ >= d1_) & (dd_ <= d2_)).sum()), 'sin mochila', int(((dd_ >= d1_) & (dd_ <= d2_) & ~pack_mask).sum()),
              'sin relieve', int(far_.sum()), '| fuentes con muestra', len(acc_), 'con 2+', len(cFar), '| alcanzados', int(np.isfinite(dd_).sum()))
    bvh_arm = _BVH.FromPolygons([tuple(p_) for p_ in P], [tuple(int(i_) for i_ in f_) for f_ in FV[armF]])
    Va, Fa, UVa, Vb, Fb, UVb = [], [], [], [], [], []
    cClean = {}
    for loop in loops:
        Lp = np.array([P[FIRST[r_]] for r_ in loop])
        segs = np.linalg.norm(np.roll(Lp, -1, 0) - Lp, axis=1); per = float(segs.sum())
        nb_ = len(loop)
        ncut = sum(1 for k in range(nb_) if tuple(sorted((loop[k], loop[(k + 1) % nb_]))) in cutset)
        if per < 0.03 or ncut < 0.3 * nb_: continue
        th = 2 * math.pi * np.r_[0, np.cumsum(segs)[:-1]] / per
        B2 = np.c_[np.cos(th), np.sin(th)]
        sp = max(2 * math.pi / nb_ * 1.15, CFG.get('wall_sp', 0.15))
        gg = np.arange(-1, 1 + sp, sp)
        GX, GY = np.meshgrid(gg, gg); GX = GX.ravel(); GY = GY.ravel()
        GX = GX + (np.round((GY + 1) / sp) % 2) * sp / 2
        inn = np.hypot(GX, GY) < 1 - 1.0 * sp
        nr_ = max(nb_ // 2, 12); ar_ = 2 * math.pi * (np.arange(nr_) + 0.5) / nr_
        I2 = np.r_[np.c_[GX[inn], GY[inn]], (1 - 0.5 * sp) * np.c_[np.cos(ar_), np.sin(ar_)]]
        X2 = np.r_[B2, I2]
        tri = _Del(X2).simplices
        nI = len(I2); nT = nb_ + nI
        if nI < 3: continue
        ee = np.unique(np.sort(np.r_[tri[:, [0, 1]], tri[:, [1, 2]], tri[:, [2, 0]]], 1), axis=0)
        Ad = csr_matrix((np.ones(2 * len(ee)), (np.r_[ee[:, 0], ee[:, 1]], np.r_[ee[:, 1], ee[:, 0]])), shape=(nT, nT))
        dg = np.asarray(Ad.sum(1)).ravel()
        Lm = csr_matrix((dg, (np.arange(nT), np.arange(nT))), shape=(nT, nT)) - Ad
        ii_i = np.arange(nb_, nT)
        Lii = Lm[ii_i][:, ii_i].tocsc(); Lib = Lm[ii_i][:, :nb_]
        X3 = np.zeros((nT, 3)); X3[:nb_] = Lp
        for c in range(3): X3[nb_:, c] = spsolve(Lii, -(Lib @ Lp[:, c]))
        uu = np.zeros(nT); u_i = spsolve(Lii, np.ones(nI)); uu[nb_:] = u_i / max(u_i.max(), 1e-9)
        nrm_ = np.zeros(3)
        for t_ in tri: nrm_ += np.cross(X3[t_[1]] - X3[t_[0]], X3[t_[2]] - X3[t_[0]])
        nrm_ /= max(np.linalg.norm(nrm_), 1e-12)
        if np.dot(nrm_, X3[nb_:].mean(0) - ca_) < 0: nrm_ = -nrm_          # hacia el cuerpo (lejos del brazo)
        n_out = -nrm_
        # semiejes del lazo en su plano medio
        cL = Lp.mean(0); q_ = (Lp - cL) - np.outer((Lp - cL) @ nrm_, nrm_)
        ev_, evec = np.linalg.eigh(np.cov(q_.T))
        o3 = np.argsort(ev_)[::-1]
        a_semi = float(np.sqrt(2 * max(ev_[o3[0]], 1e-12))); b_semi = float(np.sqrt(2 * max(ev_[o3[1]], 1e-12)))
        m_hat = evec[:, o3[1]]
        tri = [tuple(int(k) for k in t_) for t_ in tri]
        # altura del relieve del brazo sobre cada punto de la membrana (rayo hacia afuera)
        hgt = np.zeros(nT); hsg = np.full(nT, np.nan)
        for k in range(nb_, nT):
            hit = bvh_arm.ray_cast(Vector(X3[k] - n_out * 0.02), Vector(n_out), 0.25)
            if hit[0] is not None:
                hsg[k] = hit[3] - 0.02; hgt[k] = max(hsg[k], 0.0)
        # ================= pared del brazo
        # El brazo es medio tubo (la mitad de afuera). La pared completa la otra mitad: en cada tramo a lo largo del
        # brazo baja tanto como sube el relieve allí (brazo redondo, ni plano ni inflado), y nada en el borde.
        d01 = ((X3 - jt) @ uax - d_a) / max(d_b - d_a, 1e-6)
        NBW = 14
        bw_i = np.clip((d01 * NBW).astype(int), 0, NBW - 1)
        Hb = np.full(NBW, np.nan); Ub = np.full(NBW, np.nan)
        for b_ in range(NBW):
            m_ = (bw_i == b_) & (np.arange(nT) >= nb_)
            if m_.sum() >= 3:
                Hb[b_] = float(np.quantile(hgt[m_], 0.85)); Ub[b_] = float(uu[m_].max())
        okb = ~np.isnan(Hb)
        if okb.sum() >= 2:
            Hb = np.interp(np.arange(NBW), np.nonzero(okb)[0], Hb[okb]); Ub = np.interp(np.arange(NBW), np.nonzero(okb)[0], Ub[okb])
        else:
            Hb = np.full(NBW, b_semi * 0.5); Ub = np.ones(NBW)
        Hb[1:-1] = 0.25 * Hb[:-2] + 0.5 * Hb[1:-1] + 0.25 * Hb[2:]
        xb = np.clip(d01 * NBW - 0.5, 0, NBW - 1)
        Hk = np.interp(xb, np.arange(NBW), Hb); Uk = np.maximum(np.interp(xb, np.arange(NBW), Ub), 0.2)
        bul = np.clip(CFG.get('wall_k', 0.85) * Hk * np.clip(uu / Uk, 0, 1), 0.0, CFG.get('wall_max', 0.04))
        bul = np.maximum(bul, CFG.get('wall_min', 0.006) * uu)
        for it in range(3):                                                    # se alisa sobre la malla del disco
            bn = (Ad @ bul) / np.maximum(dg, 1); bul[nb_:] = 0.5 * bul[nb_:] + 0.5 * bn[nb_:]
        bul[:nb_] = 0.0
        B_arm = float(bul.max())
        Xa = X3 + bul[:, None] * nrm_
        tria = tri
        sgn = sum(np.dot(np.cross(Xa[t_[1]] - Xa[t_[0]], Xa[t_[2]] - Xa[t_[0]]), nrm_) for t_ in tri)
        if sgn < 0: tria = [(a_, c_, b_) for (a_, b_, c_) in tri]          # la cara mira hacia el cuerpo (afuera del brazo)
        ua_, va_ = uv_band(d01)
        k0 = len(Va)
        for k in range(nT):
            Va.append(Xa[k]); UVa.append((float(ua_[k]), float(va_)))
        Fa += [(a_ + k0, b_ + k0, c_ + k0) for (a_, b_, c_) in tria]
        # ================= pared del cuerpo
        if CFG.get('body_wall', True):
            # curvatura del cuerpo: el lazo va sobre un cuerpo redondo de radio Rm; a lo ancho del lazo (medio arco bS)
            # la superficie del cuerpo sobresale de la cuerda una flecha bS^2 / (2 Rm). La pared sube eso en el centro.
            S_l = np.arctan2(Lp[:, 0], -(Lp[:, 1] - CY)) * R0
            bS = 0.5 * float(np.quantile(S_l, 0.95) - np.quantile(S_l, 0.05))
            Rm = float(np.mean(np.hypot(Lp[:, 0], Lp[:, 1] - CY)))
            beta = math.atan2(bS, max(Rm, 1e-3))
            h_b = float(np.clip(CFG.get('bwall_arc', 1.0) * bS * bS / (2 * max(Rm, 0.05)), 0.0, min(CFG.get('bwall_k', 0.35) * b_semi, CFG.get('bwall_max', 0.02))))
            disp = h_b * uu
            # nunca por encima del brazo en reposo: se mide la altura del brazo sobre cada punto
            lim = np.where(np.isnan(hsg), np.inf, hsg - CFG.get('bwall_gap', 0.003))
            disp = np.minimum(disp, lim)
            disp = np.maximum(disp, -CFG.get('bwall_sink', 0.008))
            disp[:nb_] = 0.0
            Xb = X3 + disp[:, None] * n_out
            trib = [(a_, c_, b_) for (a_, b_, c_) in tria]
            Tp = 40; padw = 4
            cols_c = np.array([np.mean(cB[r_], 0) if r_ in cB else skin_fb for r_ in loop])
            okf = np.array([r_ in cFar for r_ in loop])
            nfar = int(okf.sum())
            if nfar >= CFG.get('bwall_far_min', 3):
                # color limpio: los puntos del borde sin muestra (tapados por una correa) lo toman de sus vecinos del lazo
                cf_ = np.array([cFar[r_] if r_ in cFar else np.zeros(3) for r_ in loop])
                ii_ = np.nonzero(okf)[0]
                xi_ = np.r_[ii_ - nb_, ii_, ii_ + nb_]
                for c in range(3): cols_c[:, c] = np.interp(np.arange(nb_), xi_, np.r_[cf_[ii_, c], cf_[ii_, c], cf_[ii_, c]])
                for it in range(CFG.get('bwall_far_smooth', 8)): cols_c = 0.25 * np.roll(cols_c, 1, 0) + 0.5 * cols_c + 0.25 * np.roll(cols_c, -1, 0)
                # sombras fuera: donde el color es el mismo y solo cambia la claridad (blanco y gris bajo la bufanda,
                # azul y azul oscuro en un pliegue) se lleva a la claridad normal de ese color en el lazo
                hs_ = np.array([colorsys.rgb_to_hsv(*np.clip(c_, 0, 1)) for c_ in cols_c])
                v_new = hs_[:, 2].copy()
                for i_ in range(nb_):
                    dh_ = np.abs(hs_[:, 0] - hs_[i_, 0]); dh_ = np.minimum(dh_, 1 - dh_) * 360
                    grp = (np.abs(hs_[:, 1] - hs_[i_, 1]) < 0.18) & ((np.minimum(hs_[:, 1], hs_[i_, 1]) < 0.15) | (dh_ < 30))
                    v_new[i_] = max(hs_[i_, 2], float(np.percentile(hs_[grp, 2], 75)))
                cols_c = np.array([colorsys.hsv_to_rgb(h_, s_, v_) for (h_, s_, _), v_ in zip(hs_, v_new)])
                # un solo color cuando hay uno que manda: si el otro grupo de color ocupa menos del 30 % del lazo o viene
                # en trozos sueltos (las franjas del dobladillo, un botón), la pared lleva el color principal entero;
                # con dos zonas grandes y seguidas (pingüino: azul atrás, blanco adelante) se conservan las dos
                lab2 = (np.linalg.norm(cols_c - cols_c[np.argmax(cols_c.sum(1))], axis=1) > np.linalg.norm(cols_c - cols_c[np.argmin(cols_c.sum(1))], axis=1)).astype(int)
                for it in range(8):
                    if lab2.min() == lab2.max(): break
                    ce2 = [cols_c[lab2 == g].mean(0) for g in (0, 1)]
                    nl2 = (np.linalg.norm(cols_c - ce2[1], axis=1) < np.linalg.norm(cols_c - ce2[0], axis=1)).astype(int)
                    if (nl2 == lab2).all(): break
                    lab2 = nl2
                if lab2.min() != lab2.max():
                    ce2 = [cols_c[lab2 == g].mean(0) for g in (0, 1)]
                    if np.linalg.norm(ce2[0] - ce2[1]) > CFG.get('bwall_two_min', 0.18):
                        seg2 = np.r_[0, segs[:-1]] + segs; seg2 = 0.5 * seg2 / seg2.sum() * 2      # peso de cada punto: su tramo de lazo
                        w2 = [float(seg2[lab2 == g].sum()) for g in (0, 1)]
                        g_min = int(w2[1] < w2[0]); frac = w2[g_min] / max(w2[0] + w2[1], 1e-9)
                        runs = int(np.sum((lab2 == g_min) & (np.roll(lab2, 1) != g_min)))
                        if frac < CFG.get('bwall_two_frac', 0.30) or runs > 1:
                            cols_c = np.where((lab2 == g_min)[:, None], ce2[1 - g_min][None, :], cols_c)
                            # y dentro del color principal, sin gradientes: el color medio del grupo, variando muy poco
                            cols_c = 0.25 * cols_c + 0.75 * ce2[1 - g_min][None, :]
                for it in range(3): cols_c = 0.25 * np.roll(cols_c, 1, 0) + 0.5 * cols_c + 0.25 * np.roll(cols_c, -1, 0)
                for k_, r_ in enumerate(loop): cClean[r_] = cols_c[k_]
            else:
                # el color del borde se alisa a lo largo del lazo (un punto suelto de otra pieza no mancha la pared)
                for it in range(2): cols_c = 0.25 * np.roll(cols_c, 1, 0) + 0.5 * cols_c + 0.25 * np.roll(cols_c, -1, 0)
            gy_, gx_ = np.mgrid[0:Tp, 0:Tp] + 0.5
            ux = gx_ / Tp * 2 - 1; uy = gy_ / Tp * 2 - 1; rr = np.hypot(ux, uy)
            kn = rr >= 0.9
            ang_ = np.mod(np.arctan2(uy, ux), 2 * math.pi)
            valsw = np.zeros((Tp, Tp, 3))
            for c in range(3): valsw[..., c] = np.interp(ang_, np.r_[th, 2 * math.pi], np.r_[cols_c[:, c], cols_c[0, c]])
            patchb = np.pad(lap_fill(kn, valsw), ((padw, padw), (padw, padw), (0, 0)), mode='edge')
            oxb, oyb = alloc(patchb.shape[1], patchb.shape[0])
            TEX[oyb:oyb + patchb.shape[0], oxb:oxb + patchb.shape[1], :3] = patchb
            flat_normal(oxb, oyb, patchb.shape[1], patchb.shape[0])
            k1 = len(Vb)
            for k in range(nT):
                Vb.append(Xb[k])
                UVb.append(((oxb + padw + (X2[k, 0] * 0.5 + 0.5) * Tp) / TW, (oyb + padw + (X2[k, 1] * 0.5 + 0.5) * Tp) / TH))
            Fb += [(a_ + k1, b_ + k1, c_ + k1) for (a_, b_, c_) in trib]
        else:
            beta = h_b = 0.0
        print('PARED', s, 'borde', nb_, 'interiores', nI, 'perímetro cm', round(per * 100, 1), 'corte', ncut, '| semiejes cm', round(a_semi * 100, 1), round(b_semi * 100, 1),
              '| brazo: fondo mm', round(B_arm * 1000, 1), '| cuerpo: inclinación°', round(math.degrees(beta), 1), 'altura mm', round(h_b * 1000, 1), '| color limpio en', nfar if CFG.get('body_wall', True) else 0, 'puntos')
    # ================= anillo del cuerpo junto al borde: restos del color del brazo
    # Comprobado al quitar el brazo: alrededor del hueco la textura del cuerpo trae pintado el color del brazo y su
    # sombra (un cerco negro en el panda, crema en la casaca azul del zorro). Pegado al brazo no se nota; al levantarlo
    # queda como un borde dentado suelto. Se repinta SOLO eso: los texeles del cuerpo a menos de 2,5 cm del borde que
    # se parecen al brazo toman el color limpio del cuerpo que tienen enfrente. Lo demás de la textura no se toca.
    nrep = 0
    if CFG.get('ring_paint', True) and cClean and CFG.get('bwall_far', True):
        Dr = CFG.get('ring_d', 0.025)
        ctrue = np.zeros((nv, 3)); has = np.zeros(nv, bool)
        for v_ in np.nonzero((dd_ <= Dr * 1.6) & bodyv)[0]:
            r_ = int(REP[src_[v_]]) if src_[v_] >= 0 else -1
            if r_ in cClean: ctrue[v_] = cClean[r_]; has[v_] = True
        for it in range(3):                                           # continuidad entre sectores vecinos
            cn_ = (Ab_ @ (ctrue * has[:, None])) / np.maximum((Ab_ @ has.astype(float)), 1e-9)[:, None]
            ctrue[has] = 0.5 * ctrue[has] + 0.5 * cn_[has]
        pal_ = np.unique(np.round(np.r_[band_pal, fcol_a[::max(1, len(fcol_a) // 96)]], 2), axis=0)
        for f in fb_:
            v3 = FV[f]
            if not has[v3].all() or dd_[v3].min() > Dr: continue
            l0 = ls_[f]; uvp = uvL[l0:l0 + 3] * np.array([TW, TH])
            x0 = int(np.floor(uvp[:, 0].min())) - 1; x1 = int(np.ceil(uvp[:, 0].max())) + 1
            y0 = int(np.floor(uvp[:, 1].min())) - 1; y1 = int(np.ceil(uvp[:, 1].max())) + 1
            x0 = max(x0, 0); y0 = max(y0, 0); x1 = min(x1, TW - 1); y1 = min(y1, TH - 1)
            if x1 <= x0 or y1 <= y0 or (x1 - x0) * (y1 - y0) > 250000: continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            a_, b_, c_ = uvp
            den = (b_[1] - c_[1]) * (a_[0] - c_[0]) + (c_[0] - b_[0]) * (a_[1] - c_[1])
            if abs(den) < 1e-9: continue
            w0 = ((b_[1] - c_[1]) * (gx - c_[0]) + (c_[0] - b_[0]) * (gy - c_[1])) / den
            w1 = ((c_[1] - a_[1]) * (gx - c_[0]) + (a_[0] - c_[0]) * (gy - c_[1])) / den
            w2 = 1 - w0 - w1
            tol = 1.0 / max(np.sqrt(abs(den)), 1.0)
            ins = (w0 >= -tol) & (w1 >= -tol) & (w2 >= -tol)
            if not ins.any(): continue
            ys_, xs_ = np.nonzero(ins); ys_ = ys_ + y0; xs_ = xs_ + x0
            B3 = np.clip(np.stack([w0[ins], w1[ins], w2[ins]], 1), 0, 1); B3 /= B3.sum(1)[:, None]
            dpx = B3 @ dd_[v3]; ct = B3 @ ctrue[v3]
            orig = TEX[ys_, xs_, :3].astype(np.float64)
            da = np.linalg.norm(orig[:, None, :] - pal_[None, :, :], axis=2).min(1)
            dt = np.linalg.norm(orig - ct, axis=1)
            m = sstep_np((dt - da + 0.03) / 0.12) * (1 - sstep_np((da - CFG.get('ring_da', 0.20)) / 0.12)) * (1 - sstep_np((dpx - 0.5 * Dr) / (0.5 * Dr)))
            if (m > 0.02).any():
                TEX[ys_, xs_, :3] = (orig + m[:, None] * (ct - orig)).astype(np.float32); nrep += int((m > 0.5).sum())
    # fleco del lado del brazo: a menos de 7 mm del borde, los texeles del brazo toman el color de su tramo (el color
    # que manda a esa altura del brazo). Donde la manga ya es de ese color no cambia nada; desaparecen los dientes de
    # otro color que la textura traía justo en la unión (se veían como borde dentado bajo el brazo levantado).
    nrep_a = 0
    if CFG.get('ring_paint_arm', True):
        Da = CFG.get('ring_d_arm', 0.007)
        fa_ = np.nonzero(armF)[0]
        ea_2 = np.unique(np.sort(np.r_[FV[fa_][:, [0, 1]], FV[fa_][:, [1, 2]], FV[fa_][:, [2, 0]]], 1), axis=0)
        la_2 = np.linalg.norm(P[ea_2[:, 0]] - P[ea_2[:, 1]], axis=1) + 1e-9
        Ga = csr_matrix((np.r_[la_2, la_2], (np.r_[ea_2[:, 0], ea_2[:, 1]], np.r_[ea_2[:, 1], ea_2[:, 0]])), shape=(nv, nv))
        from scipy.sparse.csgraph import dijkstra as _dj2
        rim_a = np.array(sorted(FIRST[r_] for r_ in nbr.keys()), int)
        da_v = _dj2(Ga, directed=False, indices=rim_a, min_only=True, limit=Da * 3)
        d01v = np.clip(((P - jt) @ uax - d_a) / max(d_b - d_a, 1e-6), 0, 1)
        for f in fa_:
            v3 = FV[f]
            if not np.isfinite(da_v[v3]).all() or da_v[v3].min() > Da: continue
            l0 = ls_[f]; uvp = uvL[l0:l0 + 3] * np.array([TW, TH])
            x0 = max(int(np.floor(uvp[:, 0].min())) - 1, 0); x1 = min(int(np.ceil(uvp[:, 0].max())) + 1, TW - 1)
            y0 = max(int(np.floor(uvp[:, 1].min())) - 1, 0); y1 = min(int(np.ceil(uvp[:, 1].max())) + 1, TH - 1)
            if x1 <= x0 or y1 <= y0 or (x1 - x0) * (y1 - y0) > 250000: continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            a_, b_, c_ = uvp
            den = (b_[1] - c_[1]) * (a_[0] - c_[0]) + (c_[0] - b_[0]) * (a_[1] - c_[1])
            if abs(den) < 1e-9: continue
            w0 = ((b_[1] - c_[1]) * (gx - c_[0]) + (c_[0] - b_[0]) * (gy - c_[1])) / den
            w1 = ((c_[1] - a_[1]) * (gx - c_[0]) + (a_[0] - c_[0]) * (gy - c_[1])) / den
            w2 = 1 - w0 - w1
            tol = 1.0 / max(np.sqrt(abs(den)), 1.0)
            ins = (w0 >= -tol) & (w1 >= -tol) & (w2 >= -tol)
            if not ins.any(): continue
            ys_, xs_ = np.nonzero(ins); ys_ = ys_ + y0; xs_ = xs_ + x0
            B3 = np.clip(np.stack([w0[ins], w1[ins], w2[ins]], 1), 0, 1); B3 /= B3.sum(1)[:, None]
            dpx = B3 @ da_v[v3]; tpx = B3 @ d01v[v3]
            xb_ = np.clip(tpx * (NB - 1), 0, NB - 1); i0 = np.floor(xb_).astype(int); i1 = np.minimum(i0 + 1, NB - 1); fr_ = (xb_ - i0)[:, None]
            ct = band[i0] * (1 - fr_) + band[i1] * fr_
            orig = TEX[ys_, xs_, :3].astype(np.float64)
            m = 1 - sstep_np(dpx / Da)
            TEX[ys_, xs_, :3] = (orig + m[:, None] * (ct - orig)).astype(np.float32)
            nrep_a += int(((m > 0.5) & (np.linalg.norm(orig - ct, axis=1) > 0.15)).sum())
    print('ANILLO', s, 'texeles repintados (más de la mitad): cuerpo', nrep, '| fleco del brazo', nrep_a)
    if Fa: extra.append(make_obj('pared_' + s, np.array(Va), Fa, UVa, 9 + si))
    if Fb: extra.append(make_obj('pared_cuerpo_' + s, np.array(Vb), Fb, UVb, 8))
