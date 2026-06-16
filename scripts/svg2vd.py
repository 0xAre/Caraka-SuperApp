import xml.etree.ElementTree as ET
import sys
import os

def convert_svg_to_vd(svg_file, out_xml_file, width_dp, height_dp, is_white=False):
    tree = ET.parse(svg_file)
    root = tree.getroot()
    
    # SVG namespace
    ns = '{http://www.w3.org/2000/svg}'
    
    # Extract viewport/width from SVG
    width = float(root.attrib.get('width', '1000').replace('pt', ''))
    height = float(root.attrib.get('height', '1000').replace('pt', ''))
    
    vd_root = ET.Element('vector', {
        'xmlns:android': 'http://schemas.android.com/apk/res/android',
        'android:width': f'{width_dp}dp',
        'android:height': f'{height_dp}dp',
        'android:viewportWidth': str(width),
        'android:viewportHeight': str(height)
    })
    
    # Check if there's a main group with transform
    group = root.find(f'.//{ns}g')
    if group is not None and 'transform' in group.attrib:
        transform = group.attrib['transform']
        # Very specific parsing for "translate(0.000000,1000.000000) scale(0.100000,-0.100000)"
        vd_group = ET.SubElement(vd_root, 'group', {
            'android:translateX': '0',
            'android:translateY': '1000',
            'android:scaleX': '0.1',
            'android:scaleY': '-0.1'
        })
        parent = vd_group
    else:
        parent = vd_root

    for path in root.findall(f'.//{ns}path'):
        d = path.attrib.get('d', '')
        # Determine fill
        fill = path.attrib.get('fill')
        if not fill:
            if group is not None and 'fill' in group.attrib:
                fill = group.attrib['fill']
            else:
                fill = '#000000'
                
        if is_white:
            fill = '#FFFFFF'
            
        ET.SubElement(parent, 'path', {
            'android:fillColor': fill,
            'android:pathData': d
        })
        
    xml_str = ET.tostring(vd_root, encoding='utf-8', xml_declaration=True).decode('utf-8')
    xml_str = xml_str.replace('?>', '?>\n')
    
    with open(out_xml_file, 'w', encoding='utf-8') as f:
        f.write(xml_str)
        
if __name__ == '__main__':
    # _16_ is black, _17_ is white
    convert_svg_to_vd('docs/brand/Untitled-design-_16_.svg', 'app/app/src/main/res/drawable/ic_caraka_logo.xml', 108, 108, is_white=False)
    convert_svg_to_vd('docs/brand/Untitled-design-_17_.svg', 'app/app/src/main/res/drawable/ic_caraka_logo_white.xml', 108, 108, is_white=True)
    
    print("Vector Drawables generated successfully.")
