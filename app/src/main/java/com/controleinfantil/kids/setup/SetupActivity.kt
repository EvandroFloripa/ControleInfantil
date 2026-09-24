package com.controleinfantil.kids.setup

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.controleinfantil.kids.R
import com.controleinfantil.kids.admin.DeviceAdmin
import com.controleinfantil.kids.admin.PolicyManager
import com.controleinfantil.kids.launcher.AppPickerActivity
import com.controleinfantil.kids.launcher.KioskManager
import com.controleinfantil.kids.remote.DeviceIdentity
import com.controleinfantil.kids.remote.SupabaseClient
import com.controleinfantil.kids.schedule.ScheduleActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela do responsável: estado da proteção, registro do aparelho e o código para
 * parear o PRIMEIRO controlador. Os demais entram por convite gerado no painel.
 */
class SetupActivity : GuardianActivity() {

    private lateinit var policy: PolicyManager
    private lateinit var client: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        policy = PolicyManager(this)
        client = SupabaseClient(this)

        findViewById<Button>(R.id.btnEnableAdmin).setOnClickListener { requestDeviceAdmin() }
        findViewById<Button>(R.id.btnPairingCode).setOnClickListener { generatePairingCode() }
        findViewById<Button>(R.id.btnChooseApps).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        findViewById<Button>(R.id.btnReleaseDevice).setOnClickListener { confirmReleaseDevice() }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        ensureRuntimePermissions()

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        // Concede câmera/mic/localização ao app quando somos Device Owner.
        policy.grantMediaPermissions()
        refreshStatus()
    }

    /** Única saída do modo travado sem resetar o aparelho; pede confirmação. */
    private fun confirmReleaseDevice() {
        AlertDialog.Builder(this)
            .setTitle(R.string.release_title)
            .setMessage(R.string.release_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.release_confirm) { _, _ ->
                val ok = KioskManager(this).releaseDevice(this)
                Toast.makeText(
                    this,
                    if (ok) R.string.release_done else R.string.release_failed,
                    Toast.LENGTH_LONG,
                ).show()
                refreshStatus()
            }
            .show()
    }

    private fun refreshStatus() {
        val admin = if (policy.isAdminActive) "✅ ativo" else "❌ inativo"
        val owner = if (policy.isDeviceOwner) "✅ ativo" else "❌ inativo (ver ADB)"
        findViewById<TextView>(R.id.status).text =
            getString(R.string.status_fmt, admin, owner)
        findViewById<View>(R.id.btnReleaseDevice).visibility =
            if (policy.isDeviceOwner) View.VISIBLE else View.GONE
        // Botão de sobreposição só quando falta a permissão (e não é Device Owner).
        val overlayOk = policy.isDeviceOwner ||
            android.provider.Settings.canDrawOverlays(this)
        findViewById<View>(R.id.btnOverlay).visibility =
            if (overlayOk) View.GONE else View.VISIBLE
        // Bloqueio por Acessibilidade: botão some quando já está ligado (ou é DO).
        val blockOk = policy.isDeviceOwner ||
            com.controleinfantil.kids.block.AppBlockerService.isEnabled(this)
        findViewById<View>(R.id.btnAccessibility).visibility =
            if (blockOk) View.GONE else View.VISIBLE

        val id = DeviceIdentity.id(this)
        findViewById<TextView>(R.id.deviceId).text =
            if (id != null) getString(R.string.device_id_fmt, id)
            else getString(R.string.device_not_registered)

        val liberados = KioskManager(this).allowedPackages.size
        findViewById<TextView>(R.id.allowedSummary).text =
            resources.getQuantityString(R.plurals.picker_count, liberados, liberados)
    }

    /**
     * No aparelho provisionado o Device Owner concede tudo sozinho. Fora dele (testes,
     * ou antes de provisionar) pedimos em runtime: sem câmera/microfone o check-in
     * fica preto; sem localização o "Pedir localização" não tem o que enviar.
     */
    private fun ensureRuntimePermissions() {
        if (policy.isDeviceOwner) return
        val needed = listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) runtimePermissions.launch(needed.toTypedArray())
    }

    private val runtimePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    /** Abre a tela do sistema para permitir sobrepor a outros apps (bloqueio real). */
    private fun requestOverlay() {
        startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName"),
            )
        )
    }

    private fun requestDeviceAdmin() {
        if (policy.isAdminActive) return
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                DeviceAdmin.component(this@SetupActivity)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.admin_explanation)
            )
        }
        startActivity(intent)
    }

    private fun generatePairingCode() {
        val codeView = findViewById<TextView>(R.id.pairingCode)
        codeView.textSize = 14f
        codeView.letterSpacing = 0f
        codeView.text = getString(R.string.pairing_generating)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { client.createPairingCode() }
            // Código grande e espaçado; mensagem de erro em texto normal.
            val isCode = result is SupabaseClient.PairingResult.Success
            codeView.textSize = if (isCode) 28f else 14f
            codeView.letterSpacing = if (isCode) 0.15f else 0f
            codeView.text = when (result) {
                is SupabaseClient.PairingResult.Success ->
                    getString(R.string.pairing_code_fmt, spaced(result.code))
                SupabaseClient.PairingResult.NotConfigured ->
                    getString(R.string.pairing_error_not_configured)
                SupabaseClient.PairingResult.NoNetwork ->
                    getString(R.string.pairing_error_network)
                SupabaseClient.PairingResult.AlreadyPaired ->
                    getString(R.string.pairing_error_already_paired)
                SupabaseClient.PairingResult.Failed ->
                    getString(R.string.pairing_error_generic)
            }
            // O registro pode ter acabado de acontecer: reflete o ID novo.
            refreshStatus()
        }
    }

    /** "ABCD1234" -> "ABCD 1234", mais fácil de ler e digitar. */
    private fun spaced(code: String): String =
        if (code.length == 8) "${code.substring(0, 4)} ${code.substring(4)}" else code
}
