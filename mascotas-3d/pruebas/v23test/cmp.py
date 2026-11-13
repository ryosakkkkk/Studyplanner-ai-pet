#!/usr/bin/env python3
"""Antes y después con la MISMA pose, cámara y luz (visor real, fondo verde para que se note cualquier hueco).
uso: cmp.py <tag> <pets,> <clip:t;clip:t> <ang,ang> <tilt,tilt> [w=360] [dsf=2] [rect x,y,w,h] [modo=fondo] [puertoA=8770] [puertoB=8768]
Salida: /home/claude/w/v23test/<tag>/<tag>_<pet>.png — cada fila una pose; a la izquierda la versión anterior, a la derecha la nueva."""
import sys, json, os, subprocess, math
from PIL import Image, ImageDraw
tag, pets, poses, angs, tilts = sys.argv[1:6]
w = int(sys.argv[6]) if len(sys.argv) > 6 else 360
dsf = float(sys.argv[7]) if len(sys.argv) > 7 else 2.0
rect = [int(v) for v in sys.argv[8].split(',')] if len(sys.argv) > 8 and sys.argv[8] not in ('', '-') else None
modo = sys.argv[9] if len(sys.argv) > 9 else 'fondo'
pa = sys.argv[10] if len(sys.argv) > 10 else '8770'; pb = sys.argv[11] if len(sys.argv) > 11 else '8768'
la = os.environ.get('ETIQ_A', '0.22.2 (antes)'); lb = os.environ.get('ETIQ_B', '0.23 (después)')
pets = pets.split(','); poses = [(p.split(':')[0], float(p.split(':')[1])) for p in poses.split(';')]
angs = [float(a) for a in angs.split(',')]; tilts = [float(t) for t in tilts.split(',')]
od = f'/home/claude/w/v23test/{tag}'; os.makedirs(od, exist_ok=True)
for port, key in ((pa, 'a'), (pb, 'b')):
    jobs = []
    for pet in pets:
        shots = []
        for (clip, t) in poses:
            for tl in tilts:
                for a in angs:
                    s = dict(clip=clip, t=t, ang=math.radians(a), tilt=tl, out=f'{od}/_{key}_{pet}_{clip}_{t}_{tl}_{a}.png')
                    if rect: s['rect'] = rect
                    shots.append(s)
        jobs.append(dict(pet=pet, modo=modo, w=w, h=w, dsf=dsf, shots=shots))
    jf = f'{od}/_jobs_{key}.json'; json.dump(jobs, open(jf, 'w'))
    subprocess.run(['node', '/home/claude/w/v23test/shots.js', port, jf], check=True, cwd='/home/claude/w/v23test', stdout=subprocess.DEVNULL)
for pet in pets:
    rows = []
    for (clip, t) in poses:
        for tl in tilts:
            ims = []
            for a in angs:
                for key in ('a', 'b'):
                    ims.append(Image.open(f'{od}/_{key}_{pet}_{clip}_{t}_{tl}_{a}.png').convert('RGB'))
            rows.append((f'{clip} t={t}' + (f' incl={tl}' if tl else ''), ims))
    iw, ih = rows[0][1][0].size
    H0 = 26
    sheet = Image.new('RGB', (iw * len(angs) * 2 + 6 * (len(angs) - 1), (ih + H0) * len(rows)), (25, 25, 25))
    d = ImageDraw.Draw(sheet)
    for r, (lab, ims) in enumerate(rows):
        for c, im in enumerate(ims):
            x = c * iw + 6 * (c // 2)
            sheet.paste(im, (x, r * (ih + H0) + H0))
            d.text((x + 6, r * (ih + H0) + 7), f"{pet} · {lab} · áng {angs[c // 2]:.0f}° · {la if c % 2 == 0 else lb}", fill=(255, 255, 255) if c % 2 else (255, 200, 120))
    sheet.save(f'{od}/{tag}_{pet}.png')
    for f in os.listdir(od):
        if f.startswith(f'_a_{pet}_') or f.startswith(f'_b_{pet}_'): os.remove(f'{od}/{f}')
    print('hoja', f'{od}/{tag}_{pet}.png', sheet.size)
