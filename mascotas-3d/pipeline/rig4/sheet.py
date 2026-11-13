import sys
from PIL import Image
out=sys.argv[1]; files=sys.argv[2:]
ims=[Image.open(f) for f in files]; W,H=ims[0].size; c=4; r=(len(ims)+c-1)//c
s=Image.new('RGB',(W*c,H*r),'white')
for j,im in enumerate(ims): s.paste(im,((j%c)*W,(j//c)*H))
s.save(out)
