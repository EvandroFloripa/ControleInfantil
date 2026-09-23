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
        codeView.text = getString(R.string.pairing_generating)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!client.ensureRegistered()) null else client.createPairingCode()
            }
            codeView.text = when {
                result == null -> getString(R.string.pairing_error)
                else -> getString(R.string.pairing_code_fmt, spaced(result.code))
            }
        }
    }

    /** "ABCD1234" -> "ABCD 1234", mais fácil de ler e digitar. */
    private fun spaced(code: String): String =
        if (code.length == 8) "${code.substring(0, 4)} ${code.substring(4)}" else code
}
