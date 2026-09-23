package com.controleinfantil.kids.setup

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.controleinfantil.kids.R
import com.controleinfantil.kids.admin.DeviceAdmin
import com.controleinfantil.kids.admin.PolicyManager
import com.controleinfantil.kids.remote.DeviceIdentity

/**
 * Tela do responsável: mostra o estado da proteção (Device Admin / Device Owner),
 * o ID do aparelho (para cadastrar no painel) e permite ativar o Device Admin.
 *
 * Esta é a base; a lista de apps liberados e o PIN de acesso entram aqui em seguida.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var policy: PolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        policy = PolicyManager(this)

        findViewById<TextView>(R.id.deviceId).text =
            getString(R.string.device_id_fmt, DeviceIdentity.id(this))

        findViewById<android.widget.Button>(R.id.btnEnableAdmin).setOnClickListener {
            requestDeviceAdmin()
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
    }

    private fun requestDeviceAdmin() {
        if (policy.isAdminActive) return
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceAdmin.component(this@SetupActivity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.admin_explanation)
            )
        }
        startActivity(intent)
    }
}
