package com.controleinfantil.kids.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.controleinfantil.kids.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela onde o responsável marca quais apps a criança pode abrir.
 *
 * A escolha só é gravada ao tocar em "Salvar", para que sair sem querer não mude
 * nada. Ao salvar, a lista do Lock Task é reaplicada (ver [KioskManager]).
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var kiosk: KioskManager
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        kiosk = KioskManager(this)
        selected.addAll(kiosk.allowedPackages)

        val recycler = findViewById<RecyclerView>(R.id.appsList)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            kiosk.allowedPackages = selected.toSet()
            finish()
        }

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { launchableApps() }
            findViewById<View>(R.id.loading).visibility = View.GONE
            if (apps.isEmpty()) {
                findViewById<TextView>(R.id.empty).visibility = View.VISIBLE
            }
            recycler.adapter = PickerAdapter(apps, selected) { updateCount() }
            updateCount()
        }
    }

    private fun updateCount() {
        findViewById<TextView>(R.id.count).text =
            resources.getQuantityString(R.plurals.picker_count, selected.size, selected.size)
    }

    private class PickerAdapter(
        private val items: List<AppInfo>,
        private val selected: MutableSet<String>,
        private val onChanged: () -> Unit,
    ) : RecyclerView.Adapter<PickerAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val label: TextView = view.findViewById(R.id.appLabel)
            val pkg: TextView = view.findViewById(R.id.appPackage)
            val check: CheckBox = view.findViewById(R.id.appCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app_picker, parent, false)
            )

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.pkg.text = item.packageName

            // Sem listener durante o bind, senão reciclar a linha dispara o toggle.
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = item.packageName in selected

            val toggle = {
                val nowChecked = item.packageName !in selected
                if (nowChecked) selected.add(item.packageName)
                else selected.remove(item.packageName)
                holder.check.isChecked = nowChecked
                onChanged()
            }
            holder.itemView.setOnClickListener { toggle() }
            holder.check.setOnClickListener { toggle() }
        }

        override fun getItemCount(): Int = items.size
    }
}
