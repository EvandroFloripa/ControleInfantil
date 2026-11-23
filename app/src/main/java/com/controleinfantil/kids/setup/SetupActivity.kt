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
import android.widget.TextView
import com.controleinfantil.kids.schedule.TimeRules
import com.controleinfantil.kids.schedule.UsageTracker
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

        findViewById<View>(R.id.btnEnableAdmin).setOnClickListener { requestDeviceAdmin() }
        findViewById<View>(R.id.btnPairingCode).setOnClickListener { generatePairingCode() }
        findViewById<View>(R.id.btnChooseApps).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
        findViewById<View>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        findViewById<View>(R.id.btnReleaseDevice).setOnClickListener { confirmReleaseDevice() }
        findViewById<View>(R.id.btnUnlockDevice).setOnClickListener { unlockForMaintenance() }
        findViewById<View>(R.id.btnDisableControl).setOnClickListener { confirmDisableControl() }
        findViewById<View>(R.id.btnRelock).setOnClickListener {
            GuardianArea.resumeBlocking(this)
            Toast.makeText(this, R.string.relock_toast, Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
        findViewById<View>(R.id.btnOverlay).setOnClickListener { requestOverlay() }
        findViewById<View>(R.id.btnAccessibility).setOnClickListener {
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

    /**
     * Desativa o controle até o responsável reativar: sai do quiosque e libera todos
     * os apps, as Configurações e a instalação, sem limite de horário. Diferente de
     * "Remover o controle", continua Device Owner e protegido contra desinstalação.
     */
    private fun confirmDisableControl() {
        AlertDialog.Builder(this)
            .setTitle(R.string.disable_control_title)
            .setMessage(R.string.disable_control_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.disable_control_confirm) { _, _ ->
                GuardianArea.setControlDisabled(this, true)
                runCatching { stopLockTask() }
                Toast.makeText(this, R.string.disable_control_toast, Toast.LENGTH_LONG).show()
                refreshStatus()
            }
            .show()
    }

    /**
     * Libera o aparelho por 15 min: sai do quiosque, pausa o bloqueio e abre as
     * Configurações — para o responsável adicionar a conta Google, instalar, etc.
     */
    private fun unlockForMaintenance() {
        GuardianArea.pauseBlocking(15 * 60_000L)
        // Também suspende o limite de horário pelo mesmo tempo.
        com.controleinfantil.kids.schedule.TimeRules.grantOverride(this, 15)
        runCatching { stopLockTask() }
        Toast.makeText(this, R.string.unlock_device_toast, Toast.LENGTH_LONG).show()
        refreshStatus()
        runCatching { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
    }

    private fun refreshStatus() {
        val disabled = GuardianArea.isControlDisabled()
        val rules = TimeRules.load(this)
        val usado = UsageTracker.usedMinutesToday(this)
        val liberados = KioskManager(this).allowedPackages.size

        // Resumo do topo: o que o app sabe sobre o aparelho agora.
        findViewById<TextView>(R.id.sumControl).setText(
            if (disabled) R.string.summary_control_off else R.string.summary_control_on
        )
        findViewById<TextView>(R.id.sumUsage).text =
            if (rules.dailyLimitMinutes > 0) "$usado de ${rules.dailyLimitMinutes} min" else "$usado min"
        findViewById<TextView>(R.id.sumSchedule).text =
            if (rules.enabled) "${rules.startText} às ${rules.endText}"
            else getString(R.string.schedule_off)
        findViewById<TextView>(R.id.sumApps).text = liberados.toString()

        // Estado de controle desativado aparece como aviso no topo da lista.
        findViewById<TextView>(R.id.status).apply {
            text = getString(R.string.control_disabled_status)
            visibility = if (disabled) View.VISIBLE else View.GONE
        }

        // Permissões: linha sempre visível, com selo e subtítulo de estado.
        val overlayOk = policy.isDeviceOwner ||
            android.provider.Settings.canDrawOverlays(this)
        setPill(R.id.overlayPill, overlayOk, R.string.pill_check)
        findViewById<TextView>(R.id.overlaySub).setText(
            if (overlayOk) R.string.sub_overlay_on else R.string.sub_overlay_off
        )
        val blockOk = policy.isDeviceOwner ||
            com.controleinfantil.kids.block.AppBlockerService.isEnabled(this)
        setPill(R.id.accessPill, blockOk, R.string.pill_check)
        findViewById<TextView>(R.id.accessSub).setText(
            if (blockOk) R.string.sub_access_on else R.string.sub_access_off
        )
        setPill(R.id.adminPill, policy.isAdminActive, R.string.pill_missing)
        findViewById<TextView>(R.id.adminSub).setText(
            if (policy.isAdminActive) R.string.sub_admin_on else R.string.sub_admin_off
        )

        // Manutenção: liberar e desligar somem quando o controle já está desativado.
        findViewById<View>(R.id.btnUnlockDevice).visibility =
            if (disabled) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnDisableControl).visibility =
            if (disabled) View.GONE else View.VISIBLE
        val relock = GuardianArea.isPaused()
        findViewById<View>(R.id.btnRelock).visibility = if (relock) View.VISIBLE else View.GONE
        findViewById<View>(R.id.divRelock).visibility = if (relock) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.relockTitle).setText(
            if (disabled) R.string.enable_control_button else R.string.relock_button
        )

        // Uso: apps liberados e horário resumido.
        findViewById<TextView>(R.id.allowedSummary).text =
            getString(R.string.apps_count_short, liberados)
        findViewById<TextView>(R.id.scheduleSub).text = if (rules.enabled) {
            val limite = if (rules.dailyLimitMinutes > 0)
                getString(R.string.schedule_limit_per_day, rules.dailyLimitMinutes)
            else getString(R.string.schedule_limit_none)
            getString(R.string.schedule_summary, rules.startText, rules.endText, limite)
        } else getString(R.string.schedule_off)

        // Zona de risco: só aparece como Device Owner (o botão de liberar é o único item).
        val dono = policy.isDeviceOwner
        findViewById<View>(R.id.dangerSection).visibility = if (dono) View.VISIBLE else View.GONE
        findViewById<View>(R.id.dangerZone).visibility = if (dono) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnReleaseDevice).visibility = if (dono) View.VISIBLE else View.GONE

        // Detalhes técnicos: aparelho registrado, administrador e versão do app.
        val admin = if (policy.isAdminActive) "ativo" else "inativo"
        val owner = if (dono) "ativo" else "inativo (ver ADB)"
        val status = getString(
            R.string.status_fmt, admin, owner,
            com.controleinfantil.kids.BuildConfig.VERSION_NAME,
        )
        val id = DeviceIdentity.id(this)
        val linhaAparelho = if (id != null) getString(R.string.device_id_fmt, id)
            else getString(R.string.device_not_registered)
        findViewById<TextView>(R.id.deviceId).text = linhaAparelho + "\n" + status
    }

    /** Selo de status: verde se OK, âmbar se precisa ativar, vermelho se falta. */
    private fun setPill(id: Int, ok: Boolean, textoSeNao: Int) {
        val pill = findViewById<TextView>(id)
        pill.setText(if (ok) R.string.pill_ok else textoSeNao)
        pill.setBackgroundResource(
            if (ok) R.drawable.bg_pill else if (textoSeNao == R.string.pill_missing) R.drawable.bg_pill_off
            else R.drawable.bg_pill_warn
        )
        pill.setTextColor(
            android.graphics.Color.parseColor(
                when {
                    ok -> "#075A58"
                    textoSeNao == R.string.pill_missing -> "#B00020"
                    else -> "#8A520A"
                }
            )
        )
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
