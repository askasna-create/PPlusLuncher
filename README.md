# P+ Launcher

Launcher Android minimalist dan ringan. Java tulen, tiada library luar (APK release ~30–60 KB).

## Ciri

- **Skrin utama**: jam besar, tarikh Masihi + Hijri, atas wallpaper anda.
- **Widget waktu solat**: Subuh, Syuruk, Zohor, Asar, Maghrib, Isyak. Waktu seterusnya diserlahkan, dengan kiraan detik ("Asar dalam 1j 20m").
  - Dikira **luar talian** (tiada internet, tiada kebenaran lokasi). Parameter ala JAKIM: Subuh 18°, Isyak 18°, Asar Syafie, ihtiyati 2 minit.
  - Lalai: Temerloh. **Tekan lama** kad solat untuk tukar nama tempat / latitud / longitud.
- **Widget bateri**: peratus, suhu, status mengecas. Bar bertukar merah bila ≤15%. Tekan → tetapan penggunaan bateri.
- **Widget storan**: peratus digunakan, ruang kosong / jumlah. Merah bila ≥90%. Tekan → tetapan storan.
- **App drawer + carian**: leret ke atas (atau tekan teks di bawah). Papan kekunci terus terbuka.
  - Taip untuk tapis; tekan **Enter** untuk buka hasil pertama. Tiada hasil → carian web.
  - Tekan lama aplikasi → App info (untuk nyahpasang dsb).
  - Leret ke bawah / Back / Home untuk tutup.
- Tekan jam → Penggera. Tekan tarikh → Kalendar.

## Bina APK

### Cara 1 — GitHub Actions (tanpa pasang apa-apa)
1. Cipta repo baharu di GitHub dan muat naik semua fail dalam folder ini (termasuk `.github/`).
2. Pergi ke tab **Actions** → *Build APK* akan berjalan sendiri (atau tekan *Run workflow*).
3. Selepas ~3 minit, muat turun **PPlusLauncher-apk** dari bahagian *Artifacts*, unzip, pasang `app-release.apk`.

### Cara 2 — Android Studio
Buka folder ini → tunggu Gradle sync → *Build ▸ Build APK(s)*, atau:
```
./gradlew assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`

## Jadikan launcher utama
Selepas pasang, tekan butang Home → pilih **P+ Launcher** → *Sentiasa*.
Atau: Tetapan ▸ Aplikasi ▸ Aplikasi lalai ▸ Aplikasi skrin utama.

## Nota
- APK release ditandatangan dengan kunci debug supaya terus boleh dipasang untuk kegunaan sendiri. Untuk Play Store, tukar `signingConfig` kepada kunci anda sendiri.
- Waktu solat biasanya dalam ±1 minit berbanding e-Solat JAKIM untuk lokasi yang sama.
- minSdk 26 (Android 8.0) hingga Android 16.
