package com.controleinfantil.kids.launcher

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.controleinfantil.kids.R
import com.controleinfantil.kids.remote.CommandService
import com.controleinfantil.kids.setup.SetupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela inicial (launcher) da criança. Mostra apenas os apps liberados pelo
 * responsável em [AppPickerActivity]. Segurar o título abre a área do responsável.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var kiosk: KioskManager
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        kiosk = KioskManager(this)
        CommandService.start(this)

        recycler = findViewById(R.id.appsGrid)
        recycler.layoutManager = GridLayoutManager(this, 4)

        findViewById<TextView>(R.id.header).setOnLongClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        kiosk.startLockTask(this)
        // Recarrega a cada volta: o responsável pode ter mudado a lista.
        refreshApps()
    }

    private fun refreshApps() {
        lifecycleScope.launch {
            val allowed = kiosk.allowedPackages
            val apps = withContext(Dispatchers.IO) {
                launchableApps().filter { it.packageName in allowed }
            }
            findViewById<TextView>(R.id.emptyHint).visibility =
                if (apps.isEmpty()) View.VISIBLE else View.GONE
            recycler.adapter = AppsAdapter(apps) { pkg ->
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            }
        }
    }

    private class AppsAdapter(
        private val items: List<AppInfo>,
        private val onClick: (String) -> Unit,
    ) : RecyclerView.Adapter<AppsAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val label: TextView = view.findViewById(R.id.appLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.itemView.setOnClickListener { onClick(item.packageName) }
        }

        override fun getItemCount(): Int = items.size
    }
}
