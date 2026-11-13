#!/usr/bin/env python3
"""Hoja de contactos con el visor real (misma pose, cámara y luz en cada puerto).
uso: sheet.py <puerto> <tag> <modo> <pets,> <clip:t;clip:t> <ang,ang> <tilt,tilt> [w] [dsf] [rect x,y,w,h]
Salida: /home/claude/w/v23test/<tag>/<tag>_<pet>.png (filas = clip:t x tilt, columnas = ángulo)"""
import sys, json, os, subprocess, math
from PIL import Image, ImageDraw
port, tag, modo, pets, poses, angs, tilts = sys.argv[1:8]
w = int(sys.argv[8]) if len(sys.argv) > 8 else 360
dsf = float(sys.argv[9]) if len(sys.argv) > 9 else 1.5
rect = [int(v) for v in sys.argv[10].split(',')] if len(sys.argv) > 10 and sys.argv[10] else None
pets = pets.split(','); poses = [(p.split(':')[0], float(p.split(':')[1])) for p in poses.split(';')]
angs = [float(a) for a in angs.split(',')]; tilts = [float(t) for t in tilts.split(',')]
od = f'/home/claude/w/v23test/{tag}'; os.makedirs(od, exist_ok=True)
jobs = []
for pet in pets:
    shots = []
    for (clip, t) in poses:
        for tl in tilts:
            for a in angs:
                s = dict(clip=clip, t=t, ang=math.radians(a), tilt=tl, out=f'{od}/_{pet}_{clip}_{t}_{tl}_{a}.png')
                if rect: s['rect'] = rect
                shots.append(s)
    jobs.append(dict(pet=pet, modo=modo, w=w, h=w, dsf=dsf, shots=shots))
jf = f'{od}/_jobs.json'; json.dump(jobs, open(jf, 'w'))
subprocess.run(['node', '/home/claude/w/v23test/shots.js', port, jf], check=True, cwd='/home/claude/w/v23test')
for pet in pets:
    rows = []
    for (clip, t) in poses:
        for tl in tilts:
            ims = [Image.open(f'{od}/_{pet}_{clip}_{t}_{tl}_{a}.png').convert('RGB') for a in angs]
            rows.append((f'{clip} t={t} incl={tl}', ims))
    iw, ih = rows[0][1][0].size
    sheet = Image.new('RGB', (iw * len(angs), (ih + 22) * len(rows)), (30, 30, 30))
    d = ImageDraw.Draw(sheet)
    for r, (lab, ims) in enumerate(rows):
        for c, im in enumerate(ims):
            sheet.paste(im, (c * iw, r * (ih + 22) + 22))
            d.text((c * iw + 6, r * (ih + 22) + 5), f'{pet} {lab} ang={angs[c]:.0f}', fill=(255, 255, 255))
    sheet.save(f'{od}/{tag}_{pet}.png')
    for (clip, t) in poses:
        for tl in tilts:
            for a in angs:
                os.remove(f'{od}/_{pet}_{clip}_{t}_{tl}_{a}.png')
    print('hoja', f'{od}/{tag}_{pet}.png', sheet.size)
