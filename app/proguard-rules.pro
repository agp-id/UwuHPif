# ==================== PENGACAKAN KODE (OBFUSCATION) ====================
-repackageclasses 'com.uwuh'
-allowaccessmodification
-optimizationpasses 5
-dontusemixedcaseclassnames

# NOTE: Jangan tambahkan aturan menghapus android.util.Log
# agar fitur Log Viewer di tvLog tetap berjalan normal!

# ==================== PERTAHANKAN KOMPONEN SISTEM ====================
# Wajib di-keep agar Android OS dapat memanggil Activity dan Receiver
-keep public class com.uwuh.utils.MainActivity
-keep public class com.uwuh.utils.BootReceiver
-keep public class com.uwuh.utils.SystemPropertiesHelper { *; }

# Keep Class Model untuk JSON Parsing
-keep class com.uwuh.utils.AppModel { *; }
