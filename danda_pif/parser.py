import xml.etree.ElementTree as ET
import os
import sys
import shutil

def clean_pem_text(text):
    lines = text.splitlines()
    cleaned_lines = [line.strip() for line in lines if line.strip()]
    return "\n".join(cleaned_lines)

def detect_key_type(pem_content):
    if "EC PRIVATE KEY" in pem_content:
        return "ecdsa"
    elif "RSA PRIVATE KEY" in pem_content:
        return "rsa"
    return None

def parse_arrays():
    base_dir = "temp_decoded"
    
    if not os.path.exists(base_dir):
        print(f"Error: Direktori {base_dir} tidak ditemukan!")
        sys.exit(1)

    values_dir = os.path.join(base_dir, "res", "values")
    if not os.path.exists(values_dir):
        print(f"Error: Direktori {values_dir} tidak ditemukan!")
        sys.exit(1)

    xml_files = [os.path.join(values_dir, f) for f in os.listdir(values_dir) if f.endswith('.xml')]

    keybox_items = []
    props_items = []

    for xml_path in xml_files:
        # Cari file asli arrays.xml dari hasil decompile untuk disalin nanti
        if os.path.basename(xml_path) == "arrays.xml":
            target_arrays_path = xml_path
            
        try:
            tree = ET.parse(xml_path)
            root = tree.getroot()

            for child in root.findall('string-array'):
                name = child.get('name')
                if name == 'keybox':
                    for item in child.findall('item'):
                        val = item.text if item.text else ""
                        if val.startswith('"') and val.endswith('"'):
                            val = val[1:-1]
                        val = val.replace('\\n', '\n')
                        cleaned_val = clean_pem_text(val)
                        if cleaned_val:
                            keybox_items.append(cleaned_val)
                elif name == 'device_props':
                    for item in child.findall('item'):
                        val = item.text.strip() if item.text else ""
                        if val.startswith('"') and val.endswith('"'):
                            val = val[1:-1]
                        props_items.append(val)
        except Exception as e:
            print(f"Peringatan: Gagal memparsing {xml_path}: {e}")

    output_dir = "danda_pif"
    os.makedirs(output_dir, exist_ok=True)

    # Salin file arrays.xml asli dari hasil decompile ke folder danda_pif/ jika ada
    if target_arrays_path and os.path.exists(target_arrays_path):
        shutil.copy(target_arrays_path, os.path.join(output_dir, "arrays.xml"))
        print("Berhasil menyalin danda_pif/arrays.xml")
    
    success_keybox = False
    success_prop = False

    # ==================== GENERATE KEYBOX.XML ====================
    if keybox_items:
        keybox_content = '<?xml version="1.0" encoding="utf-8"?>\n<Keybox>\n'
        
        # Group items by key type
        ec_private = None
        ec_certs = []
        rsa_private = None
        rsa_certs = []
        
        for item in keybox_items:
            key_type = detect_key_type(item)
            if key_type == "ecdsa":
                if ec_private is None:
                    ec_private = item
                else:
                    ec_certs.append(item)
            elif key_type == "rsa":
                if rsa_private is None:
                    rsa_private = item
                else:
                    rsa_certs.append(item)
            else:
                # Jika tidak terdeteksi, anggap sebagai certificate
                if ec_private is not None:
                    ec_certs.append(item)
                elif rsa_private is not None:
                    rsa_certs.append(item)
        
        # Write EC Key
        if ec_private is not None:
            keybox_content += '    <Key algorithm="ecdsa">\n'
            keybox_content += '        <PrivateKey format="pem">\n'
            for line in ec_private.split('\n'):
                keybox_content += f'            {line}\n'
            keybox_content += '        </PrivateKey>\n'
            for cert in ec_certs:
                keybox_content += '        <Certificate format="pem">\n'
                for line in cert.split('\n'):
                    keybox_content += f'            {line}\n'
                keybox_content += '        </Certificate>\n'
            keybox_content += '    </Key>\n'
        
        # Write RSA Key
        if rsa_private is not None:
            keybox_content += '    <Key algorithm="rsa">\n'
            keybox_content += '        <PrivateKey format="pem">\n'
            for line in rsa_private.split('\n'):
                keybox_content += f'            {line}\n'
            keybox_content += '        </PrivateKey>\n'
            for cert in rsa_certs:
                keybox_content += '        <Certificate format="pem">\n'
                for line in cert.split('\n'):
                    keybox_content += f'            {line}\n'
                keybox_content += '        </Certificate>\n'
            keybox_content += '    </Key>\n'
        
        keybox_content += '</Keybox>'
        
        with open(os.path.join(output_dir, "keybox.xml"), "w") as f:
            f.write(keybox_content)
        success_keybox = True
        print(f"Berhasil membuat danda_pif/keybox.xml")
        print(f"  - EC Private Key: {'Ada' if ec_private else 'Tidak'}, Certs: {len(ec_certs)}")
        print(f"  - RSA Private Key: {'Ada' if rsa_private else 'Tidak'}, Certs: {len(rsa_certs)}")

    # ==================== GENERATE PIF.PROP ====================
    if props_items and len(props_items) >= 8:
        prop_content = ""
        prop_keys = [
            "MANUFACTURER",
            "MODEL", 
            "FINGERPRINT",
            "BRAND",
            "PRODUCT",
            "DEVICE",
            "SECURITY_PATCH",
            "DEVICE_INITIAL_SDK_INT"
        ]
        
        for i, val in enumerate(props_items):
            if i < len(prop_keys):
                prop_content += f"{prop_keys[i]}={val}\n"
        
        # Tambahkan spoofVendingSdk default
        prop_content += "spoofVendingSdk=false\n"
                
        with open(os.path.join(output_dir, "pif.prop"), "w") as f:
            f.write(prop_content)
        success_prop = True
        print("Berhasil membuat danda_pif/pif.prop")

    if success_keybox and success_prop:
        print("\n=== SELESAI ===")
        print(f"Output folder: {output_dir}/")
        print("  - keybox.xml (untuk attestation)")
        print("  - pif.prop (untuk PIF properties)")
        sys.exit(0)
    else:
        if not success_keybox:
            print("Error: Data 'keybox' tidak ditemukan atau tidak valid.")
        if not success_prop:
            print("Error: Data 'device_props' tidak ditemukan atau kurang dari 8 item.")
        sys.exit(1)

if __name__ == "__main__":
    parse_arrays()
