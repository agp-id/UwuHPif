import xml.etree.ElementTree as ET
import os
import sys

def parse_arrays():
    base_dir = "temp_decoded"
    
    if not os.path.exists(base_dir):
        print(f"Error: Direktori {base_dir} tidak ditemukan!")
        sys.exit(1)

    values_dir = os.path.join(base_dir, "res", "values")
    if not os.path.exists(values_dir):
        print(f"Error: Direktori {values_dir} tidak ditemukan!")
        sys.exit(1)

    # Ambil semua file XML di dalam folder res/values
    xml_files = [os.path.join(values_dir, f) for f in os.listdir(values_dir) if f.endswith('.xml')]

    keybox_items = []
    props_items = []

    for xml_path in xml_files:
        try:
            tree = ET.parse(xml_path)
            root = tree.getroot()

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
        except Exception as e:
            print(f"Peringatan: Gagal memparsing {xml_path}: {e}")

    # Simpan hasil output ke dalam folder danda_pif/
    output_dir = "danda_pif"
    os.makedirs(output_dir, exist_ok=True)
    
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

    # Sinyal keluar (Exit code) untuk GitHub Actions
    if success_keybox and success_prop:
        sys.exit(0)
    else:
        print("Error: Data string-array 'keybox' atau 'device_props' tidak ditemukan.")
        sys.exit(1)

if __name__ == "__main__":
    parse_arrays()
