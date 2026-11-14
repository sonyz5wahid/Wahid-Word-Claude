# Wahid Word

Editor dokumen **.docx native untuk Android 4.2 (Jelly Bean, API 17) ke atas**, dengan tampilan bergaya Word modern
(bilah judul biru, tab Berkas/Beranda/Sisipkan, ribbon, kertas putih). Ditulis ulang dari nol dalam **Java murni,
tanpa library pihak ketiga** (terinspirasi konsep WordCraft, tetapi tidak memakai kodenya).

## Fitur
- Buka, edit, simpan `.docx`; **Simpan sebagai**; **Ekspor PDF**
- Tebal / miring / garis bawah / coret, ukuran font, warna font, sorot (highlight)
- Rata kiri/tengah/kanan, daftar poin, sisipkan tanggal/waktu/garis/tab
- Hitung kata & karakter

## Cara membuat APK (otomatis lewat GitHub)
1. Buat repositori baru di GitHub, lalu unggah seluruh isi folder ini (termasuk `.github`).
2. Buka tab **Actions → Build APK** (jalan otomatis saat push, atau klik *Run workflow*).
3. Setelah hijau, unduh artefak **WahidWord-debug-apk** → ekstrak → pasang `app-debug.apk`
   (aktifkan "Sumber tidak dikenal" di HP).

Build lokal: `gradle assembleDebug` (JDK 17 + Android SDK 34).

## Keterbatasan (jujur)
- Format DOCX yang dipertahankan: teks, tebal/miring/garis bawah/coret, ukuran, warna, sorot, perataan, bullet.
  **Tabel, gambar, header/footer, nomor halaman, dll. tidak dibawa** — pakai *Simpan sebagai* agar file asli aman.
- Tidak ada Undo/Redo, rata kanan-kiri (justify), pilihan jenis font, atau pratinjau paginasi.
- Android < 4.4: berkas disimpan ke folder `WahidWord/` dan PDF hanya aksara Latin (font Helvetica).
  Android 4.4+: memakai pemilih berkas sistem dan PDF Unicode penuh.
- Ukuran APK sekitar 100–300 KB (bukan 50 MB) karena tanpa dependensi.
