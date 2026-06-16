import xml.etree.ElementTree as ET
import re

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)

def scale_path(path_str):
    # The brand SVG uses potrace-style paths under:
    # transform="translate(0,1000) scale(0.1,-0.1)".
    # Apply that transform directly so Android VectorDrawable renders it faithfully.
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
            new_x = x * 0.1
            new_y = y * -0.1 + 1000.0
            out.append(f"{new_x:.2f}")
            out.append(f"{new_y:.2f}")
            i += 2
        elif current_cmd == 'c':
            x = float(tokens[i])
            y = float(tokens[i+1])
            new_x = x * 0.1
            new_y = y * -0.1
            out.append(f"{new_x:.2f}")
            out.append(f"{new_y:.2f}")
            i += 2
            
    return " ".join(out)

def convert_svg_to_vd(svg_file, out_xml_file, width_dp, height_dp, fill):
    tree = ET.parse(svg_file)
    root = tree.getroot()
    ns = '{http://www.w3.org/2000/svg}'
    
    width = float(root.attrib.get('width', '1000').replace('pt', ''))
    height = float(root.attrib.get('height', '1000').replace('pt', ''))
    
    vd_root = ET.Element('vector', {
        f'{{{ANDROID_NS}}}width': f'{width_dp}dp',
        f'{{{ANDROID_NS}}}height': f'{height_dp}dp',
        f'{{{ANDROID_NS}}}viewportWidth': f'{width:.0f}',
        f'{{{ANDROID_NS}}}viewportHeight': f'{height:.0f}'
    })

    for path in root.findall(f'.//{ns}path'):
        d = path.attrib.get('d', '')
        scaled_d = scale_path(d)
            
        ET.SubElement(vd_root, 'path', {
            f'{{{ANDROID_NS}}}fillColor': fill,
            f'{{{ANDROID_NS}}}pathData': scaled_d
        })
        
    xml_str = ET.tostring(vd_root, encoding='utf-8', xml_declaration=True).decode('utf-8')
    xml_str = xml_str.replace('?>', '?>\n')
    
    with open(out_xml_file, 'w', encoding='utf-8') as f:
        f.write(xml_str)
        
if __name__ == '__main__':
    source = 'docs/brand/caraka-logo-black.svg'
    convert_svg_to_vd(
        source,
        'app/app/src/main/res/drawable/ic_caraka_logo.xml',
        108,
        108,
        '@color/caraka_logo_adaptive'
    )
    convert_svg_to_vd(
        source,
        'app/app/src/main/res/drawable/ic_caraka_logo_white.xml',
        108,
        108,
        '#FFFFFF'
    )
    convert_svg_to_vd(
        source,
        'app/app/src/main/res/drawable/ic_caraka_logo_splash_mark.xml',
        108,
        108,
        '@color/caraka_logo_splash'
    )
    
    print("Vector Drawables generated successfully.")
