# CARAKA — Sesi Uji Gawai (2 device)
> Dibuat otomatis: 14 Juni 2026 · Infinix X682B + Redmi M2010J19CG

## Perangkat

| Label | Serial | OEM | Android | Peran uji |
|-------|--------|-----|---------|-----------|
| **Device A** | `06116250B4003101` | Infinix X682B | — | Fresh install (DB v3 langsung) |
| **Device B** | `8fc5b2a40221` | Xiaomi Redmi | 12 | Update dari install lama (7 Jun) — uji migrasi v2→v3 |

## Setup selesai ✓

- [x] `assembleDebug` hijau
- [x] APK terpasang di kedua gawai
- [x] Infinix: uninstall + install baru (signature mismatch diperbaiki)
- [x] Redmi: `install -r` sukses (pertahankan data lama jika ada)

## Langkah berikutnya (Anda di gawai)

### 1. Mode pesawat + WiFi ON (keduanya)
Settings → Mode pesawat ON → WiFi ON (matikan data seluler).

### 2. Onboarding (jika fresh)
- **Infinix (A):** buka CARAKA → izinkan Location, Nearby, Notifications, Camera → buat profil (mis. nama "Alpha", role CIVILIAN).
- **Redmi (B):** jika sudah punya identitas, lewati; jika crash saat buka → catat log (A1 gagal).

### 3. Verifikasi peer (QR)
- A: Settings → QR identitas
- B: scan QR A → Simpan sebagai verified peer
- Ulangi sebaliknya (B→A) agar keduanya verified

### 4. Discovery
- Tab **Network** di kedua device, jarak 1–3 m
- Tunggu 10–30 detik sampai peer muncul

---

## Checklist uji (prioritas)

### A1 Migrasi DB — Redmi (B)
- [ ] App tidak crash saat buka setelah update
- [ ] Chat/peer lama masih ada (jika pernah dipakai)
- [ ] Logcat: tidak ada `Migration didn't properly handle` / `IllegalStateException`

### A2 Unicast (A ↔ B)
- [ ] A kirim TEXT ke B → B terima plaintext
- [ ] Status A: SENT → DELIVERED (ikon DoneAll) **setelah** B terima
- [ ] Matikan layar B, A kirim → A tetap SENT (bukan DELIVERED)

### A3 DTN carry (verified peer offline)
- [ ] B matikan app / force-stop
- [ ] A kirim ke B (verified) → status SENT
- [ ] Nyalakan B → pesan terkirim & DELIVERED otomatis

### A5 Regression cepat
- [ ] SOS broadcast: A kirim SOS → B terima (label disiarkan, bukan delivered)
- [ ] Restart app → pesan duplikat tidak muncul

---

## Perintah di laptop

```powershell
# Logcat live (Redmi)
.\scripts\device-test-logcat.ps1 -DeviceSerial 8fc5b2a40221 -LogFile logs\redmi-test.log

# Logcat live (Infinix)
.\scripts\device-test-logcat.ps1 -DeviceSerial 06116250B4003101 -LogFile logs\infinix-test.log

# Rebuild + install semua
cd app; .\gradlew.bat assembleDebug
..\scripts\device-test-install.ps1

# Snapshot log DB/mesh
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s 8fc5b2a40221 logcat -d Room:* CarakaDB:* MeshRouter:* MeshFGS:* *:S
```

## Catatan keterbatasan sesi ini

- Rencana asli minta **3+ gawai** untuk multi-hop (A2.6, A5 SOS ≥3 hop) — dengan 2 device, uji multi-hop **ditunda** atau butuh gawai ketiga.
- Uji migrasi v2→v3 paling valid di **Redmi** (install lama 7 Jun). Infinix = fresh install saja.
- Infinix kadang `offline` di ADB — cabut/colok USB atau aktifkan ulang USB debugging jika install gagal.

## Gagal-kriteria (STOP)

1. Crash saat buka app (A1)
2. DELIVERED tanpa ACK nyata (A2)
3. Pesan verified tidak ter-deliver setelah peer online (A3)
