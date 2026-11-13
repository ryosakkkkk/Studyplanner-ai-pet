# Escudos nítidos (se ejecuta dentro de armfix.py, antes de guardar la textura).
# Cada escudo del modelo (pecho, mochila, bolso) se reemplaza por el escudo real de la universidad, dibujado a buena
# resolución en un espacio libre de la textura. Las caras del escudo y su alrededor toman UV nuevas (proyección plana)
# y su textura se vuelve a hornear ahí, así el borde no se nota.
from mathutils.bvhtree import BVHTree
from PIL import Image as _PIL
LOGO = np.asarray(_PIL.open('/home/claude/w/logo/logo_shield.png').convert('RGBA')).astype(np.float64) / 255.0
LH, LW = LOGO.shape[:2]
LASP = LW / LH
TEX0 = TEX.copy()
bvh = BVHTree.FromPolygons([tuple(p) for p in P], [tuple(int(i) for i in f) for f in FV])
uvdata = me.uv_layers.active.data
LOGO_RECTS = []
AW, AH = 1024, 512                                  # textura propia de los escudos (segundo material)
ATLAS = np.zeros((AH, AW, 3)); ATLAS[:] = (0.5, 0.5, 0.5)
shelf_x = 4

def sample_bilinear(img, u, v):
    h_, w_ = img.shape[:2]
    xf = np.clip((u % 1.0) * w_ - 0.5, 0, w_ - 1.001); yf = np.clip((v % 1.0) * h_ - 0.5, 0, h_ - 1.001)
    x0 = np.floor(xf).astype(int); y0 = np.floor(yf).astype(int); fx = (xf - x0)[:, None]; fy = (yf - y0)[:, None]
    a = img[y0, x0]; b = img[y0, x0 + 1]; c = img[y0 + 1, x0]; d = img[y0 + 1, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy

if CFG.get('logos'):
    MAT_E = bpy.data.materials.new(pet + '_escudo')
    MAT_E.use_nodes = True
    nt_ = MAT_E.node_tree
    bsdf = [n for n in nt_.nodes if n.type == 'BSDF_PRINCIPLED'][0]
    bsdf.inputs['Roughness'].default_value = 0.9; bsdf.inputs['Metallic'].default_value = 0.0
    IMG_E = bpy.data.images.new(pet + '_escudo', AW, AH)
    tn = nt_.nodes.new('ShaderNodeTexImage'); tn.image = IMG_E
    nt_.links.new(tn.outputs['Color'], bsdf.inputs['Base Color'])
    me.materials.append(MAT_E)
for li_, L in enumerate(CFG.get('logos', [])):
    x0_, z0_ = L['c']; back = L.get('back', False)
    org = Vector((x0_, 2.0 if back else -2.0, z0_)); dr = Vector((0, -1.0 if back else 1.0, 0))
    hit = bvh.ray_cast(org, dr)
    if hit[0] is None:
        print('ESCUDO sin impacto', L); continue
    hp = np.array(hit[0][:]); hf = int(hit[2])
    n0 = np.array(hit[1][:]); n0 /= np.linalg.norm(n0)
    w_o, h_o = L['w'], L['h']
    hdec = max(h_o * 1.06, min(w_o * 1.06 / LASP, h_o * 1.2)); wdec = hdec * LASP
    R = 0.56 * max(wdec, hdec, w_o, h_o) + 0.008
    # caras de la misma capa alrededor del escudo, conectadas a la cara del impacto; si no alcanzan a cubrir el
    # escudo nuevo, se aflojan las condiciones (superficies más curvas)
    need_ = 0.5 * max(wdec, hdec) + 0.004
    for dtol, ntol in ((0.018, 0.15), (0.03, 0.0), (0.045, -0.25), (0.07, -0.5)):
        near_ = (np.linalg.norm(FC - hp, axis=1) < R) & (np.abs((FC - hp) @ n0) < dtol) & ((FN @ n0) > ntol) & (ARMF == 0)
        near_[hf] = True
        idx_, lab_, n_ = comps(near_)
        sel = idx_[lab_ == lab_[np.nonzero(idx_ == hf)[0][0]]]
        q_ = P[FV[sel]].reshape(-1, 3) - hp
        up0 = np.array([0, 0, 1.0]) - n0[2] * n0; up0 /= np.linalg.norm(up0); rt0 = np.cross(up0, n0)
        if min(-(q_ @ rt0).min(), (q_ @ rt0).max(), -(q_ @ up0).min(), (q_ @ up0).max()) >= need_: break
    selm = np.zeros(npoly, bool); selm[sel] = True
    nrm = (FN[sel] * np.linalg.norm(np.cross(P[FV[sel, 1]] - P[FV[sel, 0]], P[FV[sel, 2]] - P[FV[sel, 0]]), axis=1)[:, None]).sum(0)
    nrm /= np.linalg.norm(nrm)
    up = np.array([0, 0, 1.0]) - nrm[2] * nrm; up /= np.linalg.norm(up)
    rt = np.cross(up, nrm); rt /= np.linalg.norm(rt)
    # coordenadas planas (m) de cada esquina de las caras elegidas
    cor = P[FV[sel]] - hp                          # (n, 3, 3)
    A_ = cor @ rt; B_ = cor @ up
    amin, amax, bmin, bmax = A_.min(), A_.max(), B_.min(), B_.max()
    pad = 6
    scale = CFG.get('logo_px', 300) / max(amax - amin, bmax - bmin)   # texeles por metro
    Wr = int(math.ceil((amax - amin) * scale)) + 2 * pad; Hr = int(math.ceil((bmax - bmin) * scale)) + 2 * pad
    if shelf_x + Wr > AW - 4 or Hr > AH - 8:
        print('ESCUDO sin espacio', li_); continue
    ox, oy = shelf_x, 4; shelf_x += Wr + 8
    # hornear la textura actual de esas caras en el rectángulo
    buf = np.zeros((Hr, Wr, 3)); cov = np.zeros((Hr, Wr), bool); depth = np.full((Hr, Wr), -1e9)
    for k_, f in enumerate(sel):
        pa = np.c_[(A_[k_] - amin) * scale + pad, (B_[k_] - bmin) * scale + pad]   # (3, 2) en texeles
        uvs = uvL[ls_[f] + np.arange(3)]
        x0r = max(int(np.floor(pa[:, 0].min())), 0); x1r = min(int(np.ceil(pa[:, 0].max())), Wr - 1)
        y0r = max(int(np.floor(pa[:, 1].min())), 0); y1r = min(int(np.ceil(pa[:, 1].max())), Hr - 1)
        if x1r < x0r or y1r < y0r: continue
        X, Y = np.meshgrid(np.arange(x0r, x1r + 1) + 0.5, np.arange(y0r, y1r + 1) + 0.5)
        (ax, ay), (bx, by), (cx, cy) = pa
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-12: continue
        l1 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
        l2 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
        l3 = 1 - l1 - l2
        ins = (l1 >= -0.01) & (l2 >= -0.01) & (l3 >= -0.01)
        if not ins.any(): continue
        dpt = float((FC[f] - hp) @ nrm)
        yy, xx = Y[ins].astype(int), X[ins].astype(int)
        front = dpt >= depth[yy, xx]
        if not front.any(): continue
        yy, xx = yy[front], xx[front]
        uu = l1[ins][front][:, None] * uvs[0] + l2[ins][front][:, None] * uvs[1] + l3[ins][front][:, None] * uvs[2]
        buf[yy, xx] = sample_bilinear(TEX0[..., :3], uu[:, 0], uu[:, 1])
        cov[yy, xx] = True; depth[yy, xx] = dpt
    # bordes del rectángulo: el texel cubierto más cercano (para que el filtrado no traiga negro)
    if (~cov).any():
        ii = ndimage.distance_transform_edt(~cov, return_distances=False, return_indices=True)
        buf = buf[ii[0], ii[1]]
    # se borra el escudo viejo con el color de la tela de alrededor
    cxp = (0 - amin) * scale + pad; cyp = (0 - bmin) * scale + pad
    Yg, Xg = np.mgrid[0:Hr, 0:Wr] + 0.5
    ea = (Xg - cxp) / (w_o * CFG.get('logo_clear', 0.6) * scale); eb = (Yg - cyp) / (h_o * CFG.get('logo_clear', 0.6) * scale)
    inner = np.maximum(np.abs(ea), np.abs(eb)) < 1.0
    # el escudo viejo se borra rellenando con la tela de alrededor (interpolación suave desde el borde)
    known_ = ~inner
    buf = lap_fill(known_, buf)
    # escudo nuevo, nítido
    wpx = max(int(round(wdec * scale)), 8); hpx = max(int(round(hdec * scale)), 8)
    lg = np.asarray(_PIL.open('/home/claude/w/logo/logo_shield.png').convert('RGBA').resize((wpx, hpx), _PIL.LANCZOS)).astype(np.float64) / 255.0
    lg = lg[::-1]                                  # filas de abajo hacia arriba, como la textura
    xa = int(round(cxp - wpx / 2)); ya = int(round(cyp - hpx / 2))
    xs0, ys0 = max(xa, 0), max(ya, 0); xs1, ys1 = min(xa + wpx, Wr), min(ya + hpx, Hr)
    sub = lg[ys0 - ya:ys1 - ya, xs0 - xa:xs1 - xa]
    al = sub[..., 3:4]
    buf[ys0:ys1, xs0:xs1] = buf[ys0:ys1, xs0:xs1] * (1 - al) + sub[..., :3] * al
    ATLAS[oy:oy + Hr, ox:ox + Wr] = buf
    # UV nuevas para las caras elegidas
    for k_, f in enumerate(sel):
        for c_ in range(3):
            u_ = (ox + (A_[k_, c_] - amin) * scale + pad) / AW; v_ = (oy + (B_[k_, c_] - bmin) * scale + pad) / AH
            uvdata[ls_[f] + c_].uv = (u_, v_)
            uvL[ls_[f] + c_] = (u_, v_)
    me.polygons.foreach_get  # (las caras del escudo pasan al segundo material)
    for f in sel: me.polygons[int(f)].material_index = 1
    LOGO_RECTS.append([ox, oy, Wr, Hr])
    print('ESCUDO', li_, 'caras', len(sel), 'rect', Wr, 'x', Hr, 'texeles/m', int(scale), 'escudo px', wpx, 'x', hpx)
_json.dump(LOGO_RECTS, open(f'/home/claude/w/rig4/out/escudos_{pet}.json', 'w'))
if CFG.get('logos'):
    import os as _os
    _pth = f'/home/claude/w/rig4/tex/{pet}_escudo.jpg'
    _PIL.fromarray((np.clip(ATLAS[::-1], 0, 1) * 255 + 0.5).astype(np.uint8), 'RGB').save(_pth, quality=93, subsampling=0, optimize=True)
    IMG_E.filepath = _pth; IMG_E.source = 'FILE'; IMG_E.reload(); IMG_E.pack()
