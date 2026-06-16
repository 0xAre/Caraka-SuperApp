import xml.etree.ElementTree as ET
import re

def scale_path(path_str):
    # Tokens are M, c, z, and numbers (which may be negative or decimal)
    tokens = re.findall(r'[Mcz]|[-+]?\d*\.?\d+', path_str)
    out = []
    
    current_cmd = None
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t in ('M', 'c', 'z'):
            current_cmd = t
            out.append(t)
            i += 1
            continue
            
        if current_cmd == 'M':
            x = float(tokens[i])
            y = float(tokens[i+1])
            new_x = x * 0.043 + 335.59
            new_y = y * -0.043 + 551.88
            out.append(f"{new_x:.2f}")
            out.append(f"{new_y:.2f}")
            i += 2
        elif current_cmd == 'c':
            x = float(tokens[i])
            y = float(tokens[i+1])
            new_x = x * 0.043
            new_y = y * -0.043
            out.append(f"{new_x:.2f}")
            out.append(f"{new_y:.2f}")
            i += 2
            
    return " ".join(out)

def convert_svg_to_vd(svg_file, out_xml_file, width_dp, height_dp, is_white=False):
    tree = ET.parse(svg_file)
    root = tree.getroot()
    ns = '{http://www.w3.org/2000/svg}'
    
    width = float(root.attrib.get('width', '1000').replace('pt', ''))
    height = float(root.attrib.get('height', '1000').replace('pt', ''))
    
    vd_root = ET.Element('vector', {
        'xmlns:android': 'http://schemas.android.com/apk/res/android',
        'android:width': f'{width_dp}dp',
        'android:height': f'{height_dp}dp',
        'android:viewportWidth': str(width),
        'android:viewportHeight': str(height)
    })

    # Find the group to see if we should extract fill
    group = root.find(f'.//{ns}g')

    for path in root.findall(f'.//{ns}path'):
        d = path.attrib.get('d', '')
        scaled_d = scale_path(d)
        
        # Adaptive color logic
        fill = "@color/caraka_logo_adaptive"
                
        if is_white:
            # But the user asked for white specifically for the app icon,
            # wait, if is_white is true, it is for ic_caraka_logo_white.xml
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
    # ic_caraka_logo is the main one, we use adaptive color
    convert_svg_to_vd('docs/brand/caraka-logo-black.svg', 'app/app/src/main/res/drawable/ic_caraka_logo.xml', 108, 108, is_white=False)
    # ic_caraka_logo_white is explicitly white (for launcher inset)
    convert_svg_to_vd('docs/brand/caraka-logo-white.svg', 'app/app/src/main/res/drawable/ic_caraka_logo_white.xml', 108, 108, is_white=True)
    
    print("Vector Drawables generated successfully.")
