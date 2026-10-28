# Draws the launcher icon, TV banner and phone app icons in Wonder Box's colours.
#     python3 tools/make_icons.py .
# Needs Pillow; the banner uses the Fredoka font from the app.
from PIL import Image, ImageDraw, ImageFont
import math, sys
ROOT = sys.argv[1]
LAGOON=(14,94,102); INK=(7,59,64); TANG=(255,138,61); CORAL=(255,92,122); LIME=(195,232,75); WHITE=(255,255,255)
FONT = ROOT + "/app/src/main/res/font/fredoka_bold.ttf"

def star(d, cx, cy, r, fill, rot=0):
    pts=[]
    for i in range(10):
        rr = r if i%2==0 else r*0.46
        a = math.radians(rot + i*36 - 90)
        pts.append((cx+rr*math.cos(a), cy+rr*math.sin(a)))
    d.polygon(pts, fill=fill)

def box_art(d, x, y, s):
    """The box and star in a square of size s at (x, y)."""
    u = s/100
    # sparkles
    star(d, x+18*u, y+30*u, 6*u, LIME, 10)
    star(d, x+84*u, y+22*u, 5*u, LIME, -8)
    d.ellipse([x+10*u, y+12*u, x+15*u, y+17*u], fill=TANG)
    # star popping out
    star(d, x+50*u, y+33*u, 24*u, WHITE, -8)
    # body
    d.rounded_rectangle([x+20*u, y+52*u, x+80*u, y+88*u], radius=6*u, fill=CORAL)
    # ribbon
    d.rectangle([x+45*u, y+52*u, x+55*u, y+88*u], fill=LIME)
    # lid
    d.rounded_rectangle([x+15*u, y+44*u, x+85*u, y+57*u], radius=4*u, fill=TANG)
    d.rectangle([x+45*u, y+44*u, x+55*u, y+57*u], fill=LIME)

def icon(size, rounded=True, pad=0.0, alpha=True):
    S = 1024
    im = Image.new("RGBA", (S,S), (0,0,0,0))
    d = ImageDraw.Draw(im)
    if rounded: d.rounded_rectangle([0,0,S-1,S-1], radius=S*0.22, fill=LAGOON)
    else: d.rectangle([0,0,S,S], fill=LAGOON)
    inset = S*(0.04+pad)
    box_art(d, inset, inset, S-2*inset)
    im = im.resize((size,size), Image.LANCZOS)
    return im if alpha else im.convert("RGB")

def banner(w, h):
    k = 4; W, H = w*k, h*k
    im = Image.new("RGB", (W,H), LAGOON)
    d = ImageDraw.Draw(im)
    box_art(d, W*0.02, H*0.1, H*0.8)
    f = ImageFont.truetype(FONT, int(H*0.22))
    tx = W*0.02 + H*0.84
    d.text((tx, H*0.25), "Wonder", font=f, fill=TANG)
    d.text((tx, H*0.50), "Box", font=f, fill=WHITE)
    return im.resize((w,h), Image.LANCZOS)

res = ROOT + "/app/src/main/res/"
for name, px in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
    icon(px).save(f"{res}drawable-{name}/ic_launcher.png")
banner(320,180).save(res+"drawable/banner.png")
banner(320,180).save(res+"drawable-xhdpi/banner.png")
banner(480,270).save(res+"drawable-xxhdpi/banner.png")
pub = ROOT + "/server/pb_public/"
icon(192).save(pub+"icon-192.png")
icon(512).save(pub+"icon-512.png")
icon(512, rounded=False, pad=0.10).save(pub+"icon-maskable-512.png")
icon(180, rounded=False, alpha=False).save(pub+"apple-touch-icon.png")
