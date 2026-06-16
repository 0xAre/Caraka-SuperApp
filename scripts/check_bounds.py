import xml.etree.ElementTree as ET
import re

tree = ET.parse('app/app/src/main/res/drawable/ic_caraka_logo.xml')
root = tree.getroot()
ns = '{http://schemas.android.com/apk/res/android}'
paths = root.findall('path')

all_x = []
all_y = []

for path in paths:
    d = path.attrib[ns + 'pathData']
    tokens = re.findall(r'[Mcz]|[-+]?\d*\.\d+', d)
    
    current_x = 0
    current_y = 0
    
    i = 0
    cmd = None
    while i < len(tokens):
        t = tokens[i]
        if t in ('M', 'c', 'z'):
            cmd = t
            i += 1
            continue
            
        if cmd == 'M':
            current_x = float(tokens[i])
            current_y = float(tokens[i+1])
            all_x.append(current_x)
            all_y.append(current_y)
            i += 2
        elif cmd == 'c':
            # Relative cubic bezier: dx1, dy1, dx2, dy2, dx, dy
            dx1, dy1 = float(tokens[i]), float(tokens[i+1])
            dx2, dy2 = float(tokens[i+2]), float(tokens[i+3])
            dx, dy = float(tokens[i+4]), float(tokens[i+5])
            
            all_x.extend([current_x+dx1, current_x+dx2, current_x+dx])
            all_y.extend([current_y+dy1, current_y+dy2, current_y+dy])
            
            current_x += dx
            current_y += dy
            i += 6

print("X bounds:", min(all_x), max(all_x))
print("Y bounds:", min(all_y), max(all_y))
