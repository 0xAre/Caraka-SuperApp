# CARAKA — Physical Device Test Checklist
> P0 Testing Guide | WRECK-IT 7.0 | Updated: 7 Juni 2026

---

## Setup

### Perangkat yang dibutuhkan
- **Minimal 2 Android device** (minSdk 26 / Android 8.0+)
- **Ideal: 3 device** untuk test multi-hop relay
- WiFi harus ON di semua device (WiFi Direct menggunakan radio WiFi)
- **Lokasi**: dalam ruangan, jarak 1–5 meter untuk mulai test

### Install APK
```bash
cd app
./gradlew assembleDebug
# APK: app/app/build/outputs/apk/debug/app-debug.apk

# Install ke semua device (ulangi per device):
adb -s <device_serial> install app/app/build/outputs/apk/debug/app-debug.apk

# Cek semua device yang terdeteksi:
adb devices
```

---

## ✅ Test Suite A — Onboarding & Identity

### A1. First Launch & Permission
- [ ] Buka app di Device 1 → muncul onboarding tour
- [ ] Grant semua permissions: Location, Nearby Devices, Notifications, Camera
- [ ] Tour bisa di-skip dengan tap "Skip"

### A2. Profile Setup — Civilian
- [ ] Di Device 1: pilih role **CIVILIAN**, masukkan nama
- [ ] Tap "Mulai" → masuk ke HomeScreen
- [ ] Peer ID muncul di Settings screen (16 char hex)

### A3. Profile Setup — Authority
- [ ] Di Device 2: pilih role **BPBD** (atau POLRI/PMI)
- [ ] Masukkan password authority yang sesuai
- [ ] Setelah login → HomeScreen menampilkan badge authority 🛡️

### A4. QR Identity
- [ ] Device 1: Settings → "Lihat / Scan QR Identitas"
- [ ] QR muncul dengan warna navy+amber dalam 2-3 detik
- [ ] Device 2: tap "Buka Kamera & Scan" → scan QR Device 1
- [ ] Muncul sheet "IDENTITAS TERDETEKSI" dengan nama + role + peer ID
- [ ] Tap "Simpan" → muncul konfirmasi "disimpan sebagai verified peer ✓"

---

## ✅ Test Suite B — WiFi Direct Connectivity

### B1. Discovery
- [ ] Kedua device buka tab **Network**
- [ ] Dalam 10-30 detik, device muncul di list peer masing-masing
- [ ] Status bar berubah dari "DISCOVERING" → "PEERS_FOUND"

**Jika tidak ketemu dalam 60 detik:**
- Pastikan Location permission di-grant
- Pastikan WiFi ON (bukan Airplane Mode)
- Coba toggle WiFi OFF→ON di kedua device
- Coba pindah ke ruangan yang lebih terbuka

### B2. Connection
- [ ] Tap node peer di Network screen → terjadi koneksi
- [ ] Atau: tunggu auto-connect (20 detik cooldown antara attempt)
- [ ] Status berubah ke "CONNECTED_GO" atau "CONNECTED_CLIENT"
- [ ] Node count di Settings bertambah jadi "2"
- [ ] GO Intent chip menampilkan battery % yang akurat

### B3. Connection Stability
- [ ] Setelah connected, tunggu 2 menit tanpa aktivitas
- [ ] Koneksi masih aktif? (node count tetap 2)
- [ ] Pindahkan device ke jarak ~15 meter → koneksi putus
- [ ] Auto-reconnect dalam ~5-10 detik setelah kembali dekat

---

## ✅ Test Suite C — Messaging

### C1. Direct Message Terenkripsi
- [ ] Device 1: buka Chat dengan Device 2
- [ ] Ketik pesan → tap Send
- [ ] Device 2 menerima pesan dalam < 2 detik
- [ ] Pesan muncul dengan tanda "✓✓ Mesh" (tanda terkirim via mesh)
- [ ] Matikan internet kedua device (Airplane Mode ON, lalu WiFi ON)
- [ ] Coba kirim pesan lagi → masih berhasil

### C2. Broadcast SOS
- [ ] Device 1: tab SOS → pilih kategori 🚨 Medical
- [ ] Tambah deskripsi singkat (opsional)
- [ ] Hold tombol SOS 2 detik → arc menyapu → SOS terkirim
- [ ] Device 2 menerima notifikasi "EMERGENCY SOS: [nama]"
- [ ] SOS muncul di HomeScreen Device 2 sebagai alert merah

### C3. Multi-hop Relay (butuh 3 device)
```
Device A ←→ Device B ←→ Device C
(A tidak bisa langsung ke C — pisahkan jaraknya)
```
- [ ] A dan C di luar jangkauan satu sama lain (> 20 meter)
- [ ] B di tengah (dalam jangkauan keduanya)
- [ ] Kirim pesan dari A ke C → C menerima via relay B
- [ ] Settings Device B → "Relayed" count bertambah

---

## ✅ Test Suite D — Security Features

### D1. Database Encryption (SQLCipher)
- [ ] Kirim beberapa pesan
- [ ] Sambungkan device ke PC → buka File Manager atau `adb pull`
- [ ] Cari file database di `/data/data/com.example.caraka/databases/`
- [ ] Buka dengan hex editor → isi harus **tidak terbaca** (bukan SQLite plaintext)
- [ ] Baris pertama file TIDAK boleh berisi "SQLite format 3" (ini tandanya tidak terenkripsi)

### D2. Anti-replay
- [ ] Kirim pesan dari Device 1
- [ ] Check logcat: tidak ada pesan "Anti-replay: dropped" yang tidak wajar
- [ ] Jika device 1 disconnect lalu reconnect → pesan lama tidak di-relay ulang

### D3. Message Flagging (Anti-disinformasi)
- [ ] Device 2 long-press pesan dari Device 1
- [ ] Muncul dialog konfirmasi flagging
- [ ] Setelah di-flag → pesan menampilkan icon ⚠️
- [ ] Flag count bertambah di pesan tersebut

---

## ✅ Test Suite E — Accessibility

### E1. Big Text Mode
- [ ] Settings → Aksesibilitas → Big Text ON
- [ ] Semua teks di seluruh app membesar

### E2. High Contrast Mode
- [ ] Settings → High Contrast ON
- [ ] UI berubah ke mode kontras tinggi

### E3. Haptic Feedback
- [ ] Tap berbagai tombol → device bergetar halus
- [ ] Hold SOS button → ada vibration feedback
- [ ] Settings → Haptics OFF → tidak ada vibration lagi

### E4. Language Toggle
- [ ] Settings → Language → EN
- [ ] UI berubah ke Bahasa Inggris
- [ ] Kembali ke ID → Bahasa Indonesia

---

## ✅ Test Suite F — Edge Cases & Stress

### F1. Identity Reset
- [ ] Settings → Danger Zone → "Reset Identitas"
- [ ] Konfirmasi dialog → app kembali ke onboarding
- [ ] Semua pesan dan peer data terhapus (Secure Wipe)

### F2. Low Battery Behavior (GO Intent)
- [ ] Kurangi baterai Device 1 ke < 20% (atau pakai battery saver mode)
- [ ] Hubungkan ke Device 2 → cek logcat
- [ ] Log harus menunjukkan: `GO intent=X [battery=XX%, authority=false]`
- [ ] Jika battery ≤ 10%: intent harus = 0

### F3. App Background
- [ ] Saat connected, pindahkan app ke background (home button)
- [ ] Kirim pesan dari Device 2 → notifikasi muncul di Device 1
- [ ] Buka app kembali → pesan sudah ada di chat

### F4. WiFi Toggle
- [ ] Saat connected, toggle WiFi OFF di Device 1
- [ ] Status berubah ke "WIFI_P2P_DISABLED"
- [ ] Toggle WiFi ON kembali → discovery otomatis restart

---

## 🐛 Known Issues & Mitigations

| Issue | Gejala | Mitigation |
|-------|--------|------------|
| NO_COMMON_CHANNEL | Connect gagal terus | Restart WiFi di kedua device |
| Auto-connect tidak jalan | Peer terlihat tapi tidak connect | Tap manual dari Network screen |
| Discovery BUSY | Log: "Discovery start failed: 2" | Tunggu 3 detik, auto-retry |
| Notifikasi tidak muncul | SOS tidak ada notif | Grant POST_NOTIFICATIONS permission |
| QR scan kamera freeze | Camera Activity crash | Grant CAMERA permission di Settings device |

---

## 📊 Bug Report Template

Jika menemukan bug, catat dengan format ini:

```
[BUG] Judul singkat

Device: [model, Android version]
Steps to reproduce:
  1. ...
  2. ...
Expected: ...
Actual: ...
Logcat snippet: (adb logcat -s WifiDirect MeshSocket CarakaDB)
```

---

## 📱 Logcat Filter Commands

```bash
# Monitor semua CARAKA logs
adb logcat -s WifiDirect MeshSocket CarakaDB GoIntent

# Monitor hanya koneksi WiFi Direct
adb logcat -s WifiDirect | grep -E "GO intent|battery|connect|BUSY|ERROR"

# Monitor pesan mesh
adb logcat -s WifiDirect | grep -E "HANDSHAKE|TEXT|SOS|relay"

# Monitor database
adb logcat -s CarakaDB
```
