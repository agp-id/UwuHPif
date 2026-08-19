import xml.etree.ElementTree as ET
import os
import sys

def find_arrays_xml(base_dir="temp_decoded"):
    # Melakukan pencarian file arrays.xml secara rekursif di dalam folder hasil decompile
    for root, dirs, files in os.walk(base_dir):
        if "arrays.xml" in files:
            return os.path.join(root, "arrays.xml")
    return None

def parse_arrays():
    xml_path = find_arrays_xml("temp_decoded")
    
    if not xml_path or not os.path.exists(xml_path):
        print("Error: File arrays.xml tidak ditemukan di dalam folder hasil decompile!")
        sys.exit(1)

    print(f"File arrays.xml ditemukan di: {xml_path}")

    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()

        keybox_items = []
        props_items = []

        for child in root.findall('string-array'):
            name = child.get('name')
            if name == 'keybox':
                for item in child.findall('item'):
                    val = item.text.strip() if item.text else ""
                    if val.startswith('"') and val.endswith('"'):
                        val = val[1:-1]
                    val = val.replace('\\n', '\n')
                    keybox_items.append(val)
            elif name == 'device_props' or name == 'full_device_props':
                for item in child.findall('item'):
                    val = item.text.strip() if item.text else ""
                    if val.startswith('"') and val.endswith('"'):
                        val = val[1:-1]
                    props_items.append(val)

        output_dir = "."
        success_keybox = False
        success_prop = False

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
            success_keybox = True
            print("Berhasil membuat danda_pif/keybox.xml")

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
            success_prop = True
            print("Berhasil membuat danda_pif/pif.prop")

        if success_keybox and success_prop:
            sys.exit(0)
        else:
            print("Error: Gagal menghasilkan file output dengan lengkap.")
            sys.exit(1)

    except Exception as e:
        print(f"Error saat parsing XML: {e}")
        sys.exit(1)

if __name__ == "__main__":
    parse_arrays()
