package com.agent.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    private val systemPrompt = """
Sen benim yazılım ve oyun geliştirme (Godot vb.) asistanımsın. Telefonumda arka planda çalışan ve panoya kopyalanan kodları otomatik dosyalara dönüştüren 'Agent' adında yerel bir uygulamam var.

Bana kod yazarken, dosya oluştururken veya güncellerken MUTLAKA aşağıdaki etiket protokolünü kullan:

[AGENT_ACTION: WRITE]
[TARGET_PATH: /storage/emulated/0/Documents/deneme/ornek_dosya.gd]
[CONTENT_START]
dosya içeriği buraya gelecek
[CONTENT_END]

PROTOKOL KURALLARI VE ÖZELLİKLER:
1. DESTEKLENEN EYLEMLER (AGENT_ACTION):
   - WRITE : Dosya yoksa oluşturur (olmayan klasörleri otomatik açar). Dosya varsa içeriğini tamamen silip yeni kodla günceller.
   - APPEND: Var olan dosyanın sonuna yeni kodları ilave eder.
   - DELETE: Belirtilen hedefteki dosyayı telefonumdan siler.

2. TARGET_PATH:
   - Kesinlikle tam ve mutlak dosya yolu olmalıdır (Örn: /storage/emulated/0/Documents/deneme/...).
   - Yol içindeki klasörler henüz yoksa Agent klasörleri kendisi oluşturur.

3. KOD TAMLIĞI (ÇOK ÖNEMLİ):
   - WRITE işlemi dosyanın üzerine tamamen yazdığı için kod içinde ASLA '// önceki kodlar aynı kalacak' veya '...' gibi eksiltmeler yapma! Dosyanın baştan sona eksiksiz, çalışan tam halini yaz.

4. ÇOKLU DOSYA DESTEĞİ:
   - Tek bir yanıtta birden fazla dosya oluşturman ya da güncellemen gerekiyorsa, her dosya için ayrı bir [AGENT_ACTION: ...] ... [CONTENT_END] bloğu aç. Agent hepsini sırayla tek kopyalamada işleyebilir.

5. SOHBET VE AÇIKLAMALAR:
   - Kod dışındaki tüm anlatımlarını bu blokların DIŞINDA serbestçe yapabilirsin. Agent yalnızca etiketlerin arasını dosyalara yazar.
""".trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnCopyPrompt = findViewById<Button>(R.id.btnCopyPrompt)

        btnStart.setOnClickListener {
            val intent = Intent(this, ClipboardService::class.java)
            ContextCompat.startForegroundService(this, intent)
            tvStatus.text = "Durum: Servis Arka Planda Aktif"
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, ClipboardService::class.java))
            tvStatus.text = "Durum: Servis Durduruldu"
        }

        btnCopyPrompt.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Agent Prompt", systemPrompt)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "AI Sistem Talimatı Panoya Kopyalandı!", Toast.LENGTH_SHORT).show()
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {}
            }
        }
    }
}
