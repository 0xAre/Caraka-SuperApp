import xml.etree.ElementTree as ET
import re

def scale_path(path_str):
    # Tokens are M, c, z, and numbers
    tokens = re.findall(r'[Mcz]|[-+]?\d*\.?\d+', path_str)
    
    # 1. Transform raw SVG paths (y was inverted in original)
    # The original group had transform="translate(0,1000) scale(0.1,-0.1)"
    # We apply this FIRST to get the upright correct coordinates.
    transformed_coords = []
    
    i = 0
    cmd = None
    while i < len(tokens):
        t = tokens[i]
        if t in ('M', 'c', 'z'):
            cmd = t
            transformed_coords.append(t)
            i += 1
            continue
            
        if cmd == 'M':
            x = float(tokens[i])
            y = float(tokens[i+1])
            new_x = x * 0.1
            new_y = y * -0.1 + 1000.0
            transformed_coords.append(new_x)
            transformed_coords.append(new_y)
            i += 2
        elif cmd == 'c':
            dx1, dy1 = float(tokens[i]), float(tokens[i+1])
            dx2, dy2 = float(tokens[i+2]), float(tokens[i+3])
            dx, dy = float(tokens[i+4]), float(tokens[i+5])
            
            transformed_coords.extend([dx1*0.1, dy1*-0.1])
            transformed_coords.extend([dx2*0.1, dy2*-0.1])
            transformed_coords.extend([dx*0.1, dy*-0.1])
            i += 6

    # 2. Find bounds
    current_x = 0
    current_y = 0
    all_x = []
    all_y = []
    
    i = 0
    cmd = None
    while i < len(transformed_coords):
        t = transformed_coords[i]
        if t in ('M', 'c', 'z'):
            cmd = t
            i += 1
            continue
            
        if cmd == 'M':
            current_x = float(transformed_coords[i])
            current_y = float(transformed_coords[i+1])
            all_x.append(current_x)
            all_y.append(current_y)
            i += 2
        elif cmd == 'c':
            dx1, dy1 = transformed_coords[i], transformed_coords[i+1]
            dx2, dy2 = transformed_coords[i+2], transformed_coords[i+3]
            dx, dy = transformed_coords[i+4], transformed_coords[i+5]
            
            all_x.extend([current_x+dx1, current_x+dx2, current_x+dx])
            all_y.extend([current_y+dy1, current_y+dy2, current_y+dy])
            current_x += dx
            current_y += dy
            i += 6

    min_x, max_x = min(all_x), max(all_x)
    min_y, max_y = min(all_y), max(all_y)
    
    # 3. Center and Scale
    # Viewport is 100x100
    # Let's scale the logo so its max dimension is 70 (15% padding on each side)
    w = max_x - min_x
    h = max_y - min_y
    scale = 70.0 / max(w, h)
    
    cx = (min_x + max_x) / 2.0
    cy = (min_y + max_y) / 2.0
    
    out = []
    i = 0
    cmd = None
    while i < len(transformed_coords):
        t = transformed_coords[i]
        if t in ('M', 'c', 'z'):
            cmd = t
            out.append(t)
            i += 1
            continue
            
        if cmd == 'M':
            x = transformed_coords[i]
            y = transformed_coords[i+1]
            new_x = (x - cx) * scale + 50.0
            new_y = (y - cy) * scale + 50.0
            out.append(f"{new_x:.2f}")
            out.append(f"{new_y:.2f}")
            i += 2
        elif cmd == 'c':
            out.append(f"{transformed_coords[i]*scale:.2f}")
            out.append(f"{transformed_coords[i+1]*scale:.2f}")
            out.append(f"{transformed_coords[i+2]*scale:.2f}")
            out.append(f"{transformed_coords[i+3]*scale:.2f}")
            out.append(f"{transformed_coords[i+4]*scale:.2f}")
            out.append(f"{transformed_coords[i+5]*scale:.2f}")
            i += 6
            
    return " ".join(out)

def convert_svg_to_vd(svg_file, out_xml_file, is_white=False):
    tree = ET.parse(svg_file)
    root = tree.getroot()
    ns = '{http://www.w3.org/2000/svg}'
    
    vd_root = ET.Element('vector', {
        'xmlns:android': 'http://schemas.android.com/apk/res/android',
        'android:width': '108dp',
        'android:height': '108dp',
        'android:viewportWidth': '100',
        'android:viewportHeight': '100'
    })

    # Find the group to see if we should extract fill
    for path in root.findall(f'.//{ns}path'):
        d = path.attrib.get('d', '')
        scaled_d = scale_path(d)
        
        fill = "@color/caraka_logo_adaptive"
        if is_white:
            fill = "#FFFFFF"
            
        ET.SubElement(vd_root, 'path', {
            'android:fillColor': fill,
            'android:pathData': scaled_d
        })
        
    xml_str = ET.tostring(vd_root, encoding='utf-8', xml_declaration=True).decode('utf-8')
    xml_str = xml_str.replace('?>', '?>\n')
    
    with open(out_xml_file, 'w', encoding='utf-8') as f:
        f.write(xml_str)
        
if __name__ == '__main__':
    # ALWAYS use caraka-logo-black.svg because the white one is empty!
    convert_svg_to_vd('docs/brand/caraka-logo-black.svg', 'app/app/src/main/res/drawable/ic_caraka_logo.xml', is_white=False)
    convert_svg_to_vd('docs/brand/caraka-logo-black.svg', 'app/app/src/main/res/drawable/ic_caraka_logo_white.xml', is_white=True)
    print("Vector Drawables generated successfully.")
