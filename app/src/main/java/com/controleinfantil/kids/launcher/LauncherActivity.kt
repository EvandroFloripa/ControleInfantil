package com.controleinfantil.kids.launcher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.controleinfantil.kids.R
import com.controleinfantil.kids.remote.CommandService
import com.controleinfantil.kids.setup.SetupActivity

/**
 * Tela inicial (launcher) da criança. Mostra apenas os apps liberados pelo
 * responsável. Segurar o título por alguns segundos abre a área do responsável
 * (protegida por PIN em versão futura).
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var kiosk: KioskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        kiosk = KioskManager(this)
        CommandService.start(this)

        val recycler = findViewById<RecyclerView>(R.id.appsGrid)
        recycler.layoutManager = GridLayoutManager(this, 4)
        recycler.adapter = AppsAdapter(loadAllowedApps()) { pkg ->
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        }

        // Acesso do responsável (toque longo no cabeçalho).
        findViewById<TextView>(R.id.header).setOnLongClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // Como Device Owner, mantém a criança presa a esta tela.
        kiosk.startLockTask(this)
    }

    private fun loadAllowedApps(): List<AppItem> {
        val allowed = kiosk.allowedPackages
        val intent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        return resolved
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it in allowed && it != packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    AppItem(
                        packageName = pkg,
                        label = packageManager.getApplicationLabel(info).toString(),
                        icon = packageManager.getApplicationIcon(info)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Item de app exibido no grid. */
    data class AppItem(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable
    )

    private class AppsAdapter(
        private val items: List<AppItem>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val label: TextView = view.findViewById(R.id.appLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.itemView.setOnClickListener { onClick(item.packageName) }
        }

        override fun getItemCount(): Int = items.size
    }
}
