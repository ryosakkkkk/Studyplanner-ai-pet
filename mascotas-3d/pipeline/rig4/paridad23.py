# v0.23 — el cuerpo se mueve EXACTAMENTE como en la 0.22.2 (se ejecuta en rig4.py antes de unir los brazos).
# Al coser las costuras la malla quedó conectada y el cálculo automático de pesos empezó a repartir el cuerpo entre
# huesos que en la 0.22.2 no movían nada (piernas, pies, orejas, la cadena de la cola): las mismas animaciones se
# verían distintas en todo el cuerpo, con deformaciones que nadie revisó. Esta corrección es de las uniones de brazos
# y alas: el resto del cuerpo conserva los pesos de la 0.22.2 (se copian del vértice más cercano de aquella malla,
# sin sus huesos de brazo). Los brazos, la bisagra, el borde y las paredes se calculan después con la lógica nueva.
import numpy as _np
from scipy.spatial import cKDTree as _KD
_ref = _np.load(f'/home/claude/w/rig4/ref0222/{pet}.npz', allow_pickle=True)
_Pr, _Wr, _nm = _ref['P'].astype(float), _ref['W'].astype(float), [str(n) for n in _ref['names']]
_arm_cols = [k for k, n in enumerate(_nm) if n.startswith('Arm') or n.startswith('Hand')]
_keep_cols = [k for k, n in enumerate(_nm) if not (n.startswith('Arm') or n.startswith('Hand'))]
_armshare = _Wr[:, _arm_cols].sum(1) / _np.maximum(_Wr.sum(1), 1e-9)
_rimshare = _Wr[:, [k for k, n in enumerate(_nm) if n.startswith('ArmRim')]].sum(1) / _np.maximum(_Wr.sum(1), 1e-9)
_ok = (_Wr[:, _keep_cols].sum(1) > 0.3) & (_rimshare < 0.5)          # vértices del cuerpo de la 0.22.2 con pesos propios
_tree = _KD(_Pr[_ok]); _Wk = _Wr[_ok][:, _keep_cols]; _Wk = _Wk / _np.maximum(_Wk.sum(1), 1e-9)[:, None]
_body = ~cap_mask & ~plug_mask
_d, _j = _tree.query(P[_body])
_bi = _np.nonzero(_body)[0]
_armb = [b for b in bones if (b.startswith('Arm') or b.startswith('Hand')) and not b.startswith('ArmRim')]
_TUBO = bool(CFG.get('tube'))
if _TUBO:
    # brazo en tubo (gato, coneja): el brazo NO se corta, así que sus vértices están dentro de «cuerpo». Su parte de
    # brazo (cálculo automático de esta malla) se conserva; solo se reemplaza el reparto entre los huesos del cuerpo.
    # Sin esto la mano quedaba con peso de cabeza (el vértice de referencia más cercano era la punta de una oreja) y
    # se estiraba 13 cm al celebrar.
    _tot0 = _np.maximum(sum(Wm[b] for b in bones), 1e-9)
    _Wa = _np.stack([Wm[b] / _tot0 for b in _armb], 1)
    _a = _np.clip(_Wa.sum(1), 0, 1)
    # lejos de la referencia (sobre el brazo) el reparto del cuerpo se toma de la referencia bajo el cuello
    _hd = _Wk[:, [k for k, c in enumerate(_keep_cols) if _nm[c] == 'Head' or _nm[c].startswith('Ear')]].sum(1)
    _ok2 = _np.nonzero(_hd < 0.5)[0]
    _d2, _j2 = _KD(_Pr[_ok][_ok2]).query(P[_body])
    _lejos = _d > CFG.get('paridad_lejos', 0.02)
    _j = _np.where(_lejos, _ok2[_j2], _j)
else:
    _a = _np.zeros(nv)
for b in bones:
    if b.startswith('ArmRim'): Wm[b][_bi] = 0.0
for b in bones:
    if b.startswith('Arm') or b.startswith('Hand'): continue
    if b in _nm and _nm.index(b) in _keep_cols:
        Wm[b][_bi] = _Wk[_j, _keep_cols.index(_nm.index(b))] * (1 - _a[_bi])
    else:
        Wm[b][_bi] = 0.0
for k, b in enumerate(_armb):
    Wm[b][_bi] = _Wa[_bi, k] if _TUBO else 0.0
PARIDAD = True
print('PARIDAD 0.22.2', pet, 'vértices del cuerpo', int(_body.sum()), '| distancia al vértice de referencia: mediana mm', round(float(_np.median(_d)) * 1000, 3), 'p99', round(float(_np.percentile(_d, 99)) * 1000, 2), 'máx', round(float(_d.max()) * 1000, 1),
      '| pesos', {b: int((Wm[b][_bi] > 0.5).sum()) for b in bones if (Wm[b][_bi] > 0.5).any()})
# ---- la cola pegada al cuerpo (zorro): antes ese contacto estaba sin coser y al mover la cola se abría una rendija;
# ahora está cosido, así que el paso de «cola» a «cuerpo» se reparte en unos centímetros alrededor del contacto para
# que ninguna arista cargue sola con todo el movimiento. La raíz de la cola no se toca.
_tb = [b for b in bones if b.startswith('Tail')]
if _tb:
    _t = sum(Wm[b] for b in _tb)
    if (_t > 0.05).any():
        _ed = _np.array([e.vertices[:] for e in me.edges])
        _dt = _np.abs(_t[_ed[:, 0]] - _t[_ed[:, 1]])
        _root = _np.array(tail_root[:])
        _far = (_np.linalg.norm(P[_ed[:, 0]] - _root, axis=1) > CFG.get('cola_r', 0.10)) & (_np.linalg.norm(P[_ed[:, 1]] - _root, axis=1) > CFG.get('cola_r', 0.10))
        _ce = _ed[(_dt > CFG.get('cola_dt', 0.3)) & _far & _body[_ed[:, 0]] & _body[_ed[:, 1]]]
        if len(_ce):
            from scipy.sparse import csr_matrix as _csr2
            _A = _csr2((_np.ones(len(_ed)), (_ed[:, 0], _ed[:, 1])), shape=(nv, nv)); _A = ((_A + _A.T) > 0).astype(float)
            _zone = _np.zeros(nv, bool); _zone[_np.unique(_ce)] = True
            for it in range(CFG.get('cola_saltos', 5)): _zone = _zone | ((_A @ _zone.astype(float)) > 0)
            _zone &= _body & (_np.linalg.norm(P - _root, axis=1) > CFG.get('cola_r', 0.10) * 0.8)
            _deg = _np.maximum(_np.asarray(_A.sum(1)).ravel(), 1)
            _Wall = _np.stack([Wm[b] for b in bones], 1)
            for it in range(CFG.get('cola_vueltas', 30)):
                _Wn = (_A @ _Wall) / _deg[:, None]
                _Wall[_zone] = 0.5 * _Wall[_zone] + 0.5 * _Wn[_zone]
            for k, b in enumerate(bones): Wm[b] = _Wall[:, k]
            print('COLA contacto', pet, 'aristas con salto de peso', len(_ce), '| vértices suavizados', int(_zone.sum()))

# ---- antiguas costuras: en la 0.22.2 los pesos ya estaban suavizados DENTRO de cada parche, pero no a través de las
# costuras sin unir (por ahí se abrían rendijas al moverse). Ahora están cosidas: donde todavía hay un salto de peso
# entre dos puntos unidos se aplica la MISMA regla de suavizado de la 0.22.2 (franja de unos centímetros), para que el
# estiramiento se reparta en vez de caer en una sola fila de aristas. Donde no hay salto no cambia nada.
if CFG.get('paridad_franja', True):
    from scipy.sparse import csr_matrix as _csr3
    _ed = _np.array([e.vertices[:] for e in me.edges])
    _A = _csr3((_np.ones(len(_ed)), (_ed[:, 0], _ed[:, 1])), shape=(nv, nv)); _A = ((_A + _A.T) > 0).astype(float)
    _deg = _np.maximum(_np.asarray(_A.sum(1)).ravel(), 1)
    _mov = _body & ~pack_mask
    _Wall = _np.stack([Wm[b] for b in bones], 1)
    if _TUBO:
        _az = (_a > 0.01).astype(float)
        for it in range(3): _az = ((_A @ _az) > 0).astype(float) + _az
        _azm = (_az > 0) & _mov
        for it in range(CFG.get('w_smooth', 4)):
            _Wn = (_A @ _Wall) / _deg[:, None]
            _Wall[_azm] = 0.5 * _Wall[_azm] + 0.5 * _Wn[_azm]
    _dW = _np.abs(_Wall[_ed[:, 0]] - _Wall[_ed[:, 1]]).sum(1)
    _he = (_dW > CFG.get('w_hard', 0.5)) & _mov[_ed[:, 0]] & _mov[_ed[:, 1]]
    _hard = _np.zeros(nv, bool); _hard[_ed[_he, 0]] = True; _hard[_ed[_he, 1]] = True
    _band = _hard.copy()
    for it in range(CFG.get('w_band_rings', 7)): _band = _band | ((_A @ _band.astype(float)) > 0)
    _band &= _mov
    for it in range(CFG.get('w_band_iters', 14)):
        _Wn = (_A @ _Wall) / _deg[:, None]
        _Wall[_band] = 0.5 * _Wall[_band] + 0.5 * _Wn[_band]
    for k, b in enumerate(bones): Wm[b] = _Wall[:, k]
    print('FRANJA costuras', pet, 'aristas con salto', int(_he.sum()), '| vértices en la franja', int(_band.sum()), 'de', int(_mov.sum()))
