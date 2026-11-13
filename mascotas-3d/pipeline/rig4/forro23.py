# v0.23 — forro (se ejecuta al final de rig4.py, con la malla y los pesos terminados).
# Comprobado con el visor: después de coser las costuras quedan bordes abiertos que son parte del diseño, no fallas
# de la malla: correas y cintas de una sola capa, el dobladillo y el cuello de la ropa, la boca de las botas, el borde
# de la mochila. Esas piezas son cáscaras de una sola cara: vistas por detrás (o al asomarse por el borde cuando el
# cuerpo se mueve) la cara no se dibuja y se ve el fondo a través. No se activa el dibujo por ambas caras en todo el
# material: se agrega la cara interior SOLO en una franja junto a esos bordes (unas pocas caras), con la misma
# textura, la normal invertida y los mismos pesos, 0,3 mm hacia adentro. Por fuera no cambia nada.
import bmesh, os, json
from scipy.sparse import csr_matrix as _csr
from scipy.sparse.csgraph import dijkstra as _dij
_D = CFG.get('forro_d', 0.015)
_nv0 = len(me.vertices); _nf0 = len(me.polygons)
# normales actuales por vértice (las que se exportan)
_ln = np.zeros(len(me.loops) * 3); me.corner_normals.foreach_get('vector', _ln); _ln = _ln.reshape(-1, 3)
_lv = np.zeros(len(me.loops), int); me.loops.foreach_get('vertex_index', _lv)
_NV = np.zeros((_nv0, 3)); np.add.at(_NV, _lv, _ln); _NV /= np.maximum(np.linalg.norm(_NV, axis=1), 1e-9)[:, None]
_bm = bmesh.new(); _bm.from_mesh(me)
_bm.verts.ensure_lookup_table(); _bm.edges.ensure_lookup_table(); _bm.faces.ensure_lookup_table()
def _k(v): return (round(v.co.x * 1e6), round(v.co.y * 1e6), round(v.co.z * 1e6))
_be = [e for e in _bm.edges if len(e.link_faces) == 1]
_cnt = {}
for e in _be:
    kk = tuple(sorted((_k(e.verts[0]), _k(e.verts[1])))); _cnt[kk] = _cnt.get(kk, 0) + 1
# bordes abiertos de verdad: no los comparte otra pieza unida (el borde del brazo lo comparten brazo, cuerpo y paredes)
_open = [e for e in _be if _cnt[tuple(sorted((_k(e.verts[0]), _k(e.verts[1]))))] == 1]
_olen = sum(e.calc_length() for e in _open)
if _open:
    _ed = np.array([(e.verts[0].index, e.verts[1].index) for e in _bm.edges])
    _Pv = np.array([v.co[:] for v in _bm.verts])
    _el = np.linalg.norm(_Pv[_ed[:, 0]] - _Pv[_ed[:, 1]], axis=1) + 1e-9
    _G = _csr((np.r_[_el, _el], (np.r_[_ed[:, 0], _ed[:, 1]], np.r_[_ed[:, 1], _ed[:, 0]])), shape=(_nv0, _nv0))
    _src = np.unique([v.index for e in _open for v in e.verts])
    _D2 = CFG.get('forro_d2', 0.06)
    _dist = _dij(_G, directed=False, indices=_src, min_only=True, limit=_D2 * 1.2)
    _near = _dist <= _D
    _sel = [f for f in _bm.faces if all(_near[v.index] for v in f.verts)]
    _nborde = len(_sel)
    # además: caras cuyo REVERSO da al exterior en reposo (cintas anchas, el interior de un cuello o de una bota).
    # Desde el reverso de cada cara se lanzan rayos hacia atrás: si escapan o chocan con una superficie por su frente,
    # ese reverso está en el espacio de afuera (se puede ver); si chocan con otra cara por detrás, está dentro del cuerpo.
    from mathutils.bvhtree import BVHTree as _BVHf
    _bm.normal_update()
    _bvh = _BVHf.FromBMesh(_bm)
    _selset = set(f.index for f in _sel); _next = 0
    _pk = np.zeros(_nv0, bool)
    if 'zmask' in me.color_attributes and 'pack' in (ZM.get('channels') or []):
        _zm = np.zeros(_nv0 * 4); me.color_attributes['zmask'].data.foreach_get('color', _zm)
        _pk = _zm.reshape(-1, 4)[:, (ZM.get('channels') or []).index('pack')] > 0.5
    _cone = [(0.0, 0.0)] + [(math.radians(50), k * math.pi / 3) for k in range(6)]
    _extn = {}
    for f in (_bm.faces if CFG.get('forro_cono', False) else []):
        if f.index in _selset: continue
        # lejos de todo borde no se puede ver... salvo en la mochila: es una cáscara abierta del lado del cuerpo y por
        # la rendija de arriba se le ve el fondo por dentro (comprobado con el barrido): ahí no hay límite de distancia
        _inpack = CFG.get('forro_mochila', True) and all(_pk[v.index] for v in f.verts)
        if not _inpack and min(_dist[v.index] for v in f.verts) > _D2: continue
        n_ = f.normal
        if n_.length < 0.5: continue
        c_ = f.calc_center_median() - n_ * 0.0005
        t1 = n_.orthogonal().normalized(); t2 = n_.cross(t1)
        ext = 0
        for (th, ph) in _cone:
            dd = (-n_ * math.cos(th) + (t1 * math.cos(ph) + t2 * math.sin(ph)) * math.sin(th)).normalized()
            hit = _bvh.ray_cast(c_, dd, 3.0)
            if hit[0] is None or hit[1].dot(dd) < 0: ext += 1
        _extn[f.index] = ext
        if ext >= CFG.get('forro_ext', 3):
            _sel.append(f); _selset.add(f.index); _next += 1
    # la mochila entera: es una cáscara abierta y por dentro se ve desde varios ángulos
    _npack = 0
    if CFG.get('forro_mochila', True) and _pk.any():
        for f in _bm.faces:
            if f.index not in _selset and all(_pk[v.index] for v in f.verts):
                _sel.append(f); _selset.add(f.index); _npack += 1
    # y lo que DE HECHO se ve por detrás en reposo: rayos paralelos desde 26 direcciones (8 alrededor por 3 alturas,
    # más arriba y abajo); la cara cuyo reverso recibe el primer choque está expuesta (p. ej. la raíz de las orejas)
    _nray = 0
    if CFG.get('forro_rayos', True):
        _Pb = np.array([v.co[:] for v in _bm.verts]); _cb = (_Pb.min(0) + _Pb.max(0)) / 2
        _dirs = [(a_, e_) for e_ in (-26.0, 0.0, 26.0) for a_ in range(0, 360, 45)] + [(0.0, 80.0), (0.0, -80.0)]
        _stp = CFG.get('forro_rayos_paso', 0.0025); _hitc = {}
        for (a_, e_) in _dirs:
            ar_, er_ = math.radians(a_), math.radians(e_)
            cd_ = np.array([math.sin(ar_) * math.cos(er_), -math.cos(ar_) * math.cos(er_), math.sin(er_)]); dd_ = -cd_
            up_ = np.array([0, 0, 1.0]) if abs(e_) < 70 else np.array([0, 1.0, 0])
            rg_ = np.cross(dd_, up_); rg_ /= np.linalg.norm(rg_); uv_ = np.cross(rg_, dd_)
            uu_ = (_Pb - _cb) @ rg_; ww_ = (_Pb - _cb) @ uv_; dv_ = Vector(dd_)
            for u_ in np.arange(uu_.min(), uu_.max(), _stp):
                o_u = _cb + rg_ * u_ - dd_ * 2.0
                for w_ in np.arange(ww_.min(), ww_.max(), _stp):
                    hit = _bvh.ray_cast(Vector(o_u + uv_ * w_), dv_, 6.0)
                    if hit[0] is not None and hit[1].dot(dv_) > 0: _hitc[hit[2]] = _hitc.get(hit[2], 0) + 1
        for fi in _hitc:
            if fi not in _selset: _sel.append(_bm.faces[fi]); _selset.add(fi); _nray += 1
        # un anillo alrededor de lo que se vio (los rayos van cada 2,5 mm: la cara vecina también asoma)
        add_ = []
        for fi in list(_hitc):
            for e in _bm.faces[fi].edges:
                for g in e.link_faces:
                    if g.index not in _selset: add_.append(g); _selset.add(g.index)
        _sel += add_; _nray += len(add_)
    # y lo que el BARRIDO del visor demostró que se ve por detrás en movimiento: el barrido cuadro por cuadro guarda la
    # posición de reposo de cada píxel de cara trasera visible (rejilla de 1,6 cm, rig4/expuesto/<mascota>.json).
    # Las caras que pasan por esas celdas reciben su cara interior. Es la red de seguridad para lo que solo aparece
    # al deformar (un triángulo que se da vuelta bajo la manga, el interior de un pliegue).
    _nscan = 0
    _ef = f'/home/claude/w/rig4/expuesto/{pet}.json'
    if CFG.get('forro_barrido', True) and os.path.exists(_ef):
        _keys = set(int(k) for k in json.load(open(_ef))['celdas'])
        def _vk(p_):
            r_ = int(np.clip((p_[0] + 0.5) * 255, 0, 255)) >> 2; g_ = int(np.clip(p_[2] * 255, 0, 255)) >> 2; b_ = int(np.clip((-p_[1] + 0.5) * 255, 0, 255)) >> 2
            return (r_ << 12) | (g_ << 6) | b_
        for f in _bm.faces:
            if f.index in _selset: continue
            vs_ = [np.array(v.co[:]) for v in f.verts]
            pts_ = vs_ + [(vs_[0] + vs_[1]) / 2, (vs_[1] + vs_[2]) / 2, (vs_[2] + vs_[0]) / 2, (vs_[0] + vs_[1] + vs_[2]) / 3]
            if any(_vk(p_) in _keys for p_ in pts_):
                _sel.append(f); _selset.add(f.index); _nscan += 1
    # las caras vecinas con el reverso apenas expuesto se suman (dos vueltas): la franja queda sin huecos
    for it in range(2):
        add_ = []
        for fi, ext in _extn.items():
            if fi in _selset or ext < 1: continue
            f = _bm.faces[fi]
            if any(g.index in _selset for e in f.edges for g in e.link_faces if g is not f): add_.append(f)
        for f in add_: _sel.append(f); _selset.add(f.index); _next += 1
    if _sel:
        _ret = bmesh.ops.duplicate(_bm, geom=_sel)
        _nf = [g for g in _ret['geom'] if isinstance(g, bmesh.types.BMFace)]
        _nvs = [g for g in _ret['geom'] if isinstance(g, bmesh.types.BMVert)]
        _newset = set(_nvs); _srcof = {}
        for a_, b_ in _ret['vert_map'].items():
            if b_ in _newset and a_ not in _newset: _srcof[b_] = a_.index
            elif a_ in _newset and b_ not in _newset: _srcof[a_] = b_.index
        bmesh.ops.reverse_faces(_bm, faces=_nf)
        _layers = list(_bm.verts.layers.shape.values())
        _off = CFG.get('forro_off', 0.0003)
        for v in _nvs:
            d_ = Vector(-_NV[_srcof[v]] * _off)
            v.co = v.co + d_
            for ly in _layers: v[ly] = v[ly] + d_
        _bm.verts.index_update()
        _order = sorted(_nvs, key=lambda v: v.index)
        _srcidx = np.array([_srcof[v] for v in _order])
        assert _order[0].index == _nv0 and len(_order) == len(_bm.verts) - _nv0
        _bm.to_mesh(me); _bm.free(); me.update()
        _NVall = np.r_[_NV, -_NV[_srcidx]]
        me.normals_split_custom_set_from_vertices([tuple(n) for n in _NVall])
        nv = len(me.vertices)
        print('FORRO', pet, 'borde abierto', len(_open), 'aristas', round(_olen, 2), 'm | franja mm', round(_D * 1000), '| caras forradas', len(_nf), '(junto a bordes', _nborde, '+ reverso expuesto', _next, '+ mochila', _npack, '+ vistas por rayos', _nray, '+ vistas en el barrido', _nscan, ') de', _nf0, f'({100 * len(_nf) / _nf0:.1f} %)', '| vértices nuevos', len(_nvs))
    else:
        _bm.free(); print('FORRO', pet, 'sin caras junto a los bordes abiertos')
else:
    _bm.free(); print('FORRO', pet, 'sin bordes abiertos')
