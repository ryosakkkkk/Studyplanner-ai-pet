# v0.23 — cierre de costuras de la malla original.
# Comprobado en las seis mascotas: la malla de origen llega partida en cientos de parches (uno por isla de textura)
# cuyos bordes coinciden pero NO comparten vértices: entre 24 y 38 metros de borde abierto por mascota, con el otro
# lado a décimas de milímetro. En reposo no se nota; al mover los huesos cada parche se deforma por su cuenta y la
# costura se abre: rendijas por las que se ve el fondo, bordes dentados y parches que parecen desprenderse.
# Aquí se cosen: (1) vértices de borde que casi coinciden se unen; (2) uniones en T (un vértice de borde que cae en
# medio de la arista de borde del parche vecino): se parte esa arista en ese punto y se une. La forma no cambia
# (los movimientos son de décimas de milímetro), las UV quedan en las esquinas de las caras y la forma MouthOpen
# se conserva. Con vértices compartidos, ninguna deformación puede volver a abrir la costura.
import bmesh
import numpy as np
from scipy.spatial import cKDTree


def _boundary(bm):
    bm.verts.ensure_lookup_table(); bm.edges.ensure_lookup_table(); bm.faces.ensure_lookup_table()
    return [e for e in bm.edges if len(e.link_faces) == 1]


def seam_stats(bm):
    be = _boundary(bm)
    return len(be), float(sum(e.calc_length() for e in be))


def _components(bm):
    """Etiqueta de pieza conexa por vértice (índice de vértice -> pieza)."""
    bm.verts.ensure_lookup_table()
    lab = np.full(len(bm.verts), -1, int); n = 0
    for v0 in bm.verts:
        if lab[v0.index] >= 0: continue
        st = [v0]; lab[v0.index] = n
        while st:
            v = st.pop()
            for e in v.link_edges:
                w = e.other_vert(v)
                if lab[w.index] < 0: lab[w.index] = n; st.append(w)
        n += 1
    return lab, n


def _reach(v0, K):
    """Índices de los vértices a K saltos o menos de v0 por las aristas de la malla."""
    seen = {v0.index}; fr = [v0]
    for it in range(K):
        nx = []
        for v in fr:
            for e in v.link_edges:
                w = e.other_vert(v)
                if w.index not in seen: seen.add(w.index); nx.append(w)
        if not nx: break
        fr = nx
    return seen


def zip_seams(bm, tol_v=0.0005, tol_t=0.0005, passes=24, log=print, tag='', same_comp=True, hops=8):
    """Cose los bordes abiertos que casi coinciden, COMO UNA CREMALLERA: solo une dos puntos si ya están conectados
    por la malla a pocos saltos (los dos lados de una rendija se tocan en sus extremos y se va cerrando desde ahí).
    Dos partes distintas que solo se rozan (la punta de la cola contra el costado) no se sueldan: seguirían unidas
    al moverse y la malla se estiraría como un pico. Con same_comp tampoco se une una pieza suelta (mochila) al cuerpo."""
    n0, l0 = seam_stats(bm)
    tot_w = tot_t = 0; skipped = 0
    for it in range(passes):
        # ---- (1) vértices de borde que casi coinciden
        be = _boundary(bm)
        if not be: break
        bm.verts.index_update(); bm.verts.ensure_lookup_table()
        bvl = sorted({v for e in be for v in e.verts}, key=lambda v: v.index)
        Pb = np.array([v.co[:] for v in bvl])
        pairs = sorted(cKDTree(Pb).query_pairs(tol_v))
        par = list(range(len(bvl)))
        def find(a):
            while par[a] != a:
                par[a] = par[par[a]]; a = par[a]
            return a
        cache = {}; nweld = 0; skipped = 0
        for (i, j) in pairs:
            if i not in cache: cache[i] = _reach(bvl[i], hops)
            if bvl[j].index not in cache[i]: skipped += 1; continue
            ri, rj = find(i), find(j)
            if ri != rj: par[max(ri, rj)] = min(ri, rj)
        tm = {}
        for k in range(len(bvl)):
            r = find(k)
            if r != k: tm[bvl[k]] = bvl[r]
        if tm:
            bmesh.ops.weld_verts(bm, targetmap=tm); nweld = len(tm); tot_w += nweld
        # ---- (2) uniones en T
        be = _boundary(bm)
        if not be: break
        bm.verts.index_update(); bm.verts.ensure_lookup_table()
        bvs = sorted({v.index for e in be for v in e.verts})
        P = np.array([bm.verts[i].co[:] for i in bvs]); loc = {v: k for k, v in enumerate(bvs)}
        vref = {i: bm.verts[i] for i in bvs}
        E = np.array([[loc[e.verts[0].index], loc[e.verts[1].index]] for e in be])
        A = P[E[:, 0]]; B = P[E[:, 1]]; AB = B - A
        L2 = np.maximum((AB * AB).sum(1), 1e-18); hl = np.sqrt(L2) / 2
        tree = cKDTree((A + B) / 2)
        inc = [[] for _ in bvs]
        for k, (a, b) in enumerate(E): inc[a].append(k); inc[b].append(k)
        # vértices de la cara de cada arista de borde (el vértice opuesto no puede coserse a su propia arista)
        fverts = [set(v.index for v in e.link_faces[0].verts) for e in be]
        rcache = {}
        R = float(hl.max()) + tol_t
        best = {}                                             # arista -> (distancia, vértice, parámetro)
        for i, p in enumerate(P):
            vi = bvs[i]
            for k in tree.query_ball_point(p, R):
                if k in inc[i] or vi in fverts[k]: continue
                t = float(np.dot(p - A[k], AB[k]) / L2[k])
                if t <= 0.0 or t >= 1.0: continue
                d = float(np.linalg.norm(p - (A[k] + t * AB[k])))
                if d >= tol_t: continue
                # no cerca de los extremos (eso lo resuelve la unión de vértices de la vuelta siguiente)
                if min(t, 1 - t) * 2 * hl[k] < tol_v * 0.5: continue
                if vi not in rcache: rcache[vi] = _reach(vref[vi], hops)
                if bvs[E[k][0]] not in rcache[vi] and bvs[E[k][1]] not in rcache[vi]: continue
                if k not in best or d < best[k][0]: best[k] = (d, vi, t)
        if not best:
            if nweld == 0: break
            continue
        used_v = set(); tmap = {}
        for k, (d, vi, t) in sorted(best.items(), key=lambda kv: kv[1][0]):
            if vi in used_v: continue
            e = be[k]
            if not e.is_valid or len(e.link_faces) != 1: continue
            f = e.link_faces[0]
            if len(f.verts) != 3: continue
            va = e.verts[0]
            opp = [v for v in f.verts if v not in e.verts][0]
            tgt = vref[vi]
            if not tgt.is_valid: continue
            if tgt in f.verts: continue
            ne, nv_ = bmesh.utils.edge_split(e, va, t)
            # la cara quedó de cuatro lados con un vértice en línea: se parte hacia el vértice opuesto
            f2 = [g for g in nv_.link_faces if opp in g.verts]
            if f2 and len(f2[0].verts) == 4:
                try: bmesh.utils.face_split(f2[0], nv_, opp)
                except Exception: pass
            tmap[nv_] = tgt; used_v.add(vi)
        if tmap:
            bmesh.ops.weld_verts(bm, targetmap=tmap); tot_t += len(tmap)
        bm.verts.ensure_lookup_table(); bm.edges.ensure_lookup_table(); bm.faces.ensure_lookup_table()
        ng = [f for f in bm.faces if len(f.verts) > 3]
        if ng: bmesh.ops.triangulate(bm, faces=ng)
        if not tmap and nweld == 0: break
    # caras degeneradas que pudieran quedar
    bm.faces.ensure_lookup_table()
    deg = [f for f in bm.faces if f.calc_area() < 1e-14]
    if deg: bmesh.ops.delete(bm, geom=deg, context='FACES_ONLY')
    loose = [v for v in bm.verts if not v.link_faces]
    if loose: bmesh.ops.delete(bm, geom=loose, context='VERTS')
    n1, l1 = seam_stats(bm)
    log('COSTURAS', tag, 'tolerancia mm', round(tol_v * 1000, 2), round(tol_t * 1000, 2), '| borde abierto', n0, 'aristas', round(l0, 2), 'm ->', n1, 'aristas', round(l1, 2), 'm',
        '| vértices unidos', tot_w, 'uniones en T', tot_t, '| pares que solo se rozan (no se unen)', skipped, '| caras degeneradas quitadas', len(deg))
    return n1, l1


def fill_small_holes(bm, max_n=10, max_per=0.05, log=print, tag=''):
    """Cierra los ojales que quedan (lazos de borde chicos y simples) con caras nuevas. La UV de cada cara nueva
    continúa la de la cara vecina del lado más largo (mapa afín 3D -> UV de esa cara): el color sigue al del parche."""
    uvl = bm.loops.layers.uv.active
    be = _boundary(bm)
    nbr = {}
    for e in be:
        for v in e.verts: nbr.setdefault(v, []).append(e)
    used = set(); nfill = 0; nskip = 0
    for e0 in be:
        if e0 in used: continue
        # recorre el lazo
        loop_v = []; loop_e = []; e = e0; v = e0.verts[0]; ok = True
        for it in range(max_n + 2):
            if len(nbr.get(v, [])) != 2: ok = False; break
            loop_v.append(v); loop_e.append(e); used.add(e)
            v2 = e.other_vert(v)
            e2 = [g for g in nbr[v2] if g is not e]
            if len(nbr.get(v2, [])) != 2 or not e2: ok = False; break
            v, e = v2, e2[0]
            if e is e0: break
        else:
            ok = False
        if not ok or e is not e0 or len(loop_v) < 3 or len(loop_v) > max_n: 
            nskip += 1; continue
        per = sum(g.calc_length() for g in loop_e)
        if per > max_per: nskip += 1; continue
        # orientación: la cara nueva recorre cada arista al revés que su cara vecina
        f_adj = loop_e[0].link_faces[0]
        a, b = loop_v[0], loop_v[1]
        fa = [l.vert for l in f_adj.loops]
        ia = fa.index(a)
        same_dir = fa[(ia + 1) % len(fa)] is b
        vs = loop_v[::-1] if same_dir else loop_v
        try:
            nf = bm.faces.new(vs, f_adj)
        except Exception:
            nskip += 1; continue
        nf.smooth = True
        # UV: mapa afín de la cara vecina de la arista más larga
        el = max(loop_e, key=lambda g: g.calc_length()); fr = el.link_faces[0] if el.link_faces[0] is not nf else el.link_faces[-1]
        if uvl is not None and fr is not nf and len(fr.loops) == 3:
            p0, p1, p2 = [np.array(l.vert.co[:]) for l in fr.loops]; u0, u1, u2 = [np.array(l[uvl].uv[:]) for l in fr.loops]
            e1 = p1 - p0; e2 = p2 - p0
            G = np.array([[e1 @ e1, e1 @ e2], [e1 @ e2, e2 @ e2]])
            if abs(np.linalg.det(G)) > 1e-20:
                Gi = np.linalg.inv(G)
                for l in nf.loops:
                    q = np.array(l.vert.co[:]) - p0
                    s_, t_ = Gi @ np.array([q @ e1, q @ e2])
                    uv = u0 + s_ * (u1 - u0) + t_ * (u2 - u0)
                    l[uvl].uv = (float(uv[0]), float(uv[1]))
        nfill += 1
    bm.faces.ensure_lookup_table()
    ng = [f for f in bm.faces if len(f.verts) > 3]
    if ng: bmesh.ops.triangulate(bm, faces=ng)
    n1, l1 = seam_stats(bm)
    log('OJALES', tag, 'cerrados', nfill, 'sin tocar', nskip, '| borde abierto que queda', n1, 'aristas', round(l1, 2), 'm')
    return nfill


def remove_fins(bm, tol=0.0006, log=print, tag=''):
    """Quita las «aletas»: triángulos sobrantes que cuelgan de una arista que ya tiene sus dos caras y que quedan
    encima de una de ellas (restos de parches que se solapaban). No tapan nada: debajo está la superficie."""
    bm.faces.ensure_lookup_table(); bm.edges.ensure_lookup_table()
    kill = []
    for f in bm.faces:
        if len(f.verts) != 3: continue
        es = list(f.edges)
        nb = [e for e in es if len(e.link_faces) == 1]
        nm = [e for e in es if len(e.link_faces) >= 3]
        if len(nb) != 2 or len(nm) != 1: continue
        e = nm[0]; a, b = e.verts
        apex = [v for v in f.verts if v not in e.verts][0]
        ed = (b.co - a.co); L = ed.length
        if L < 1e-9: kill.append(f); continue
        ed = ed / L
        pa = apex.co - a.co; pa_perp = pa - ed * pa.dot(ed)
        over = False
        for g in e.link_faces:
            if g is f or len(g.verts) != 3: continue
            c = [v for v in g.verts if v not in e.verts][0]
            pc = c.co - a.co; pc_perp = pc - ed * pc.dot(ed)
            if pc_perp.length < 1e-9: continue
            n_g = g.normal if g.normal.length > 0 else ed.cross(pc_perp).normalized()
            if abs(pa.dot(n_g)) < tol and pa_perp.dot(pc_perp) > 0: over = True; break
        if over: kill.append(f)
    if kill:
        bmesh.ops.delete(bm, geom=kill, context='FACES_ONLY')
        loose_e = [e for e in bm.edges if not e.link_faces]
        if loose_e: bmesh.ops.delete(bm, geom=loose_e, context='EDGES')
        loose = [v for v in bm.verts if not v.link_faces]
        if loose: bmesh.ops.delete(bm, geom=loose, context='VERTS')
    n1, l1 = seam_stats(bm)
    log('ALETAS', tag, 'quitadas', len(kill), '| borde abierto que queda', n1, 'aristas', round(l1, 2), 'm')
    return len(kill)
