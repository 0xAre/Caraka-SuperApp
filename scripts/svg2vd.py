import xml.etree.ElementTree as ET
import sys

def convert_svg_to_vd(svg_file, out_xml_file, width_dp, height_dp, is_white=False):
    tree = ET.parse(svg_file)
    root = tree.getroot()
    
    # SVG namespace
    ns = '{http://www.w3.org/2000/svg}'
    
    width = float(root.attrib.get('width', 100))
    height = float(root.attrib.get('height', 100))
    
    vd_root = ET.Element('vector', {
        'xmlns:android': 'http://schemas.android.com/apk/res/android',
        'android:width': f'{width_dp}dp',
        'android:height': f'{height_dp}dp',
        'android:viewportWidth': str(width),
        'android:viewportHeight': str(height)
    })
    
    for path in root.findall(f'.//{ns}path'):
        d = path.attrib.get('d', '')
        fill = path.attrib.get('fill', '#000000')
        if is_white:
            fill = '#FFFFFF'
            
        ET.SubElement(vd_root, 'path', {
            'android:fillColor': fill,
            'android:pathData': d
        })
        
    xml_str = ET.tostring(vd_root, encoding='utf-8', xml_declaration=True).decode('utf-8')
    # add line break after declaration
    xml_str = xml_str.replace('?>', '?>\n')
    
    with open(out_xml_file, 'w', encoding='utf-8') as f:
        f.write(xml_str)
        
if __name__ == '__main__':
    convert_svg_to_vd('docs/brand/caraka-new.svg', 'app/app/src/main/res/drawable/ic_caraka_logo.xml', 108, 108, is_white=False)
    convert_svg_to_vd('docs/brand/caraka-new.svg', 'app/app/src/main/res/drawable/ic_caraka_logo_white.xml', 108, 108, is_white=True)
    print("Done")
