package com.controleinfantil.kids.setup

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.controleinfantil.kids.R
import com.controleinfantil.kids.admin.DeviceAdmin
import com.controleinfantil.kids.admin.PolicyManager
import com.controleinfantil.kids.launcher.AppPickerActivity
import com.controleinfantil.kids.launcher.KioskManager
import com.controleinfantil.kids.remote.DeviceIdentity
import com.controleinfantil.kids.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela do responsável: estado da proteção, registro do aparelho e o código para
 * parear o PRIMEIRO controlador. Os demais entram por convite gerado no painel.
 */
class SetupActivity : AppCompatActivity() {

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

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val admin = if (policy.isAdminActive) "✅ ativo" else "❌ inativo"
        val owner = if (policy.isDeviceOwner) "✅ ativo" else "❌ inativo (ver ADB)"
        findViewById<TextView>(R.id.status).text =
            getString(R.string.status_fmt, admin, owner)

        val id = DeviceIdentity.id(this)
        findViewById<TextView>(R.id.deviceId).text =
            if (id != null) getString(R.string.device_id_fmt, id)
            else getString(R.string.device_not_registered)

        val liberados = KioskManager(this).allowedPackages.size
        findViewById<TextView>(R.id.allowedSummary).text =
            resources.getQuantityString(R.plurals.picker_count, liberados, liberados)
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
