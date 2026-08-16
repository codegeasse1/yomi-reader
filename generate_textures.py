from PIL import Image, ImageFilter, ImageDraw
import random
import os
import base64

os.makedirs('app/src/main/res/drawable-nodpi', exist_ok=True)

def generate_paper():
    size = 256
    out = Image.new('RGBA', (size, size))
    out_pixels = out.load()
    for y in range(size):
        for x in range(size):
            noise = random.random()
            if noise < 0.5:
                # Black pixel, slight alpha
                alpha = int((0.5 - noise) * 2 * 25)
                out_pixels[x, y] = (0, 0, 0, alpha)
            else:
                # White pixel, slight alpha
                alpha = int((noise - 0.5) * 2 * 25)
                out_pixels[x, y] = (255, 255, 255, alpha)
                
    out = out.filter(ImageFilter.GaussianBlur(0.6))
    path = 'app/src/main/res/drawable-nodpi/texture_paper.webp'
    out.save(path, 'WEBP')
    
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode('utf-8')

def generate_linen():
    size = 256
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(out, 'RGBA')
    
    # Horizontal lines
    for y in range(size):
        if random.random() > 0.2:
            is_dark = random.random() > 0.5
            color = (0,0,0, random.randint(8, 18)) if is_dark else (255,255,255, random.randint(8, 18))
            draw.line([(0, y), (size, y)], fill=color, width=random.choice([1, 2]))
            
    # Vertical lines
    for x in range(size):
        if random.random() > 0.2:
            is_dark = random.random() > 0.5
            color = (0,0,0, random.randint(8, 18)) if is_dark else (255,255,255, random.randint(8, 18))
            draw.line([(x, 0), (x, size)], fill=color, width=random.choice([1, 2]))
            
    out = out.filter(ImageFilter.GaussianBlur(0.4))
    path = 'app/src/main/res/drawable-nodpi/texture_linen.webp'
    out.save(path, 'WEBP')
    
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode('utf-8')

b64_paper = generate_paper()
b64_linen = generate_linen()

print("PAPER_B64:")
print(b64_paper)
print("")
print("LINEN_B64:")
print(b64_linen)
