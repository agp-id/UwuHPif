import xml.etree.ElementTree as ET
import os

def parse_arrays():
    xml_path = "temp_decoded/res/values/arrays.xml"
    
    if not os.path.exists(xml_path):
        print(f"Error: {xml_path} tidak ditemukan!")
        exit(1)

    tree = ET.parse(xml_path)
    root = tree.getroot()

    keybox_items = []
    props_items = []

    for child in root.findall('string-array'):
        name = child.get('name')
        if name == 'keybox':
            for item in child.findall('item'):
                val = item.text.strip()
                if val.startswith('"') and val.endswith('"'):
                    val = val[1:-1]
                val = val.replace('\\n', '\n')
                keybox_items.append(val)
        elif name == 'device_props' or name == 'full_device_props':
            for item in child.findall('item'):
                val = item.text.strip()
                if val.startswith('"') and val.endswith('"'):
                    val = val[1:-1]
                props_items.append(val)

    # Karena parser.py di dalam folder dandapif, output langsung disimpan ke direktori saat ini
    output_dir = "."

    # 1. Generate keybox.xml
    if keybox_items:
        keybox_content = '<?xml lang="en"?>\n<Keybox>\n'
        for i, cert in enumerate(keybox_items):
            if i == 0:
                keybox_content += f"    <Key>\n{cert}\n    </Key>\n"
            else:
                keybox_content += f"    <Certificate>\n{cert}\n    </Certificate>\n"
        keybox_content += '</Keybox>'
        
        with open(os.path.join(output_dir, "keybox.xml"), "w") as f:
            f.write(keybox_content)
        print("Berhasil membuat dandapif/keybox.xml")

    # 2. Generate pif.prop
    if props_items:
        prop_content = ""
        prop_keys = [
            "ro.product.brand", "ro.product.device", "ro.build.fingerprint", 
            "ro.product.manufacturer", "ro.product.model", "ro.product.name", 
            "ro.build.date.utc", "ro.build.version.sdk"
        ]
        
        for i, val in enumerate(props_items):
            if i < len(prop_keys):
                prop_content += f"{prop_keys[i]}={val}\n"
            else:
                prop_content += f"prop_{i}={val}\n"
                
        with open(os.path.join(output_dir, "pif.prop"), "w") as f:
            f.write(prop_content)
        print("Berhasil membuat dandapif/pif.prop")

if __name__ == "__main__":
    parse_arrays()
