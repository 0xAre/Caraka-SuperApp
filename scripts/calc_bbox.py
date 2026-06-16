import re

def compute_bbox(path_str):
    tokens = re.findall(r'[Mcz]|[-+]?\d*\.?\d+', path_str)
    
    current_cmd = None
    i = 0
    
    min_x, max_x = float('inf'), float('-inf')
    min_y, max_y = float('inf'), float('-inf')
    
    curr_x, curr_y = 0.0, 0.0
    
    while i < len(tokens):
        t = tokens[i]
        if t in ('M', 'c', 'z'):
            current_cmd = t
            i += 1
            continue
            
        if current_cmd == 'M':
            curr_x = float(tokens[i])
            curr_y = float(tokens[i+1])
            min_x = min(min_x, curr_x)
            max_x = max(max_x, curr_x)
            min_y = min(min_y, curr_y)
            max_y = max(max_y, curr_y)
            i += 2
        elif current_cmd == 'c':
            # c has 3 pairs of relative coordinates
            for j in range(3):
                dx = float(tokens[i + j*2])
                dy = float(tokens[i + j*2 + 1])
                curr_x += dx
                curr_y += dy
                min_x = min(min_x, curr_x)
                max_x = max(max_x, curr_x)
                min_y = min(min_y, curr_y)
                max_y = max(max_y, curr_y)
            i += 6
            
    return min_x, max_x, min_y, max_y

with open('docs/brand/caraka-logo-black.svg', 'r') as f:
    content = f.read()

paths = re.findall(r'd="([^"]+)"', content)

min_x, max_x = float('inf'), float('-inf')
min_y, max_y = float('inf'), float('-inf')

for p in paths:
    # Remove newlines
    p = p.replace('\n', ' ')
    mnx, mxx, mny, mxy = compute_bbox(p)
    min_x = min(min_x, mnx)
    max_x = max(max_x, mxx)
    min_y = min(min_y, mny)
    max_y = max(max_y, mxy)

print(f"BBox: X=({min_x}, {max_x}), Y=({min_y}, {max_y})")
print(f"Width: {max_x - min_x}, Height: {max_y - min_y}")
