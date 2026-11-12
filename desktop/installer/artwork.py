from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
import math

root = Path(__file__).resolve().parent.parent
out = root / 'installer' / 'assets'
out.mkdir(exist_ok=True)
scale = 4
mark_source = Image.open(root/'ui'/'logo-mark.png').convert('RGBA')
mark_source.save(root/'ui'/'rakoto.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
image = Image.new('RGB', (164*scale, 314*scale), '#FFFAF0')
draw = ImageDraw.Draw(image)
for y in range(image.height):
    t=y/image.height
    draw.line((0,y,image.width,y), fill=(int(255-9*t), int(250-12*t), int(240-20*t)))
for layer, color in enumerate(['#E6D8BC','#C2B383','#80905E','#47674B','#2F5D43']):
    points=[(x, int((175+layer*24 + math.sin(x/scale/40+layer*.8)*15)*scale)) for x in range(image.width+1)]
    draw.polygon(points+[(image.width,image.height),(0,image.height)], fill=color)
    draw.line(points,fill='#E3B04B',width=scale)
for x in range(0,image.width,28*scale):
    for offset,width,color in [(0,10,'#B5442E'),(10,4,'#E3B04B'),(14,10,'#2F5D43'),(24,4,'#E3B04B')]:
        draw.rectangle((x+offset*scale,0,x+(offset+width)*scale,4*scale),fill=color)
logo = mark_source.copy()
logo = ImageOps.contain(logo,(136*scale,90*scale),Image.Resampling.LANCZOS)
image.paste(logo,((image.width-logo.width)//2,20*scale),logo)
font_path=Path('C:/Windows/Fonts/segoeuib.ttf')
font=ImageFont.truetype(str(font_path),15*scale)
small=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',9*scale)
draw.text((17*scale,123*scale),'RAKOTO',font=font,fill='#3B2418')
draw.text((18*scale,145*scale),'PLAYER',font=small,fill='#8A6F5E',stroke_width=0)
draw.text((17*scale,277*scale),'VOTRE MUSIQUE.',font=small,fill='white')
draw.text((17*scale,290*scale),'VOTRE LIBERTÉ.',font=small,fill='#F6EEDC')
image.resize((164,314),Image.Resampling.LANCZOS).save(out/'welcome.bmp')
header=Image.new('RGB',(150,57),'#FFFAF0')
mark=ImageOps.contain(mark_source,(82,54),Image.Resampling.LANCZOS)
header.paste(mark,(0,(57-mark.height)//2),mark)
ImageDraw.Draw(header).text((85,19),'RAKOTO',font=ImageFont.truetype(str(font_path),11),fill='#3B2418')
header.save(out/'header.bmp')
