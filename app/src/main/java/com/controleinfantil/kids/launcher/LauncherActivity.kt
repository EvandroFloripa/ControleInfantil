package com.controleinfantil.kids.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.controleinfantil.kids.R
import com.controleinfantil.kids.remote.CommandService
import com.controleinfantil.kids.schedule.Verdict
import com.controleinfantil.kids.schedule.checkRules
import com.controleinfantil.kids.setup.GuardianArea
import com.controleinfantil.kids.setup.SetupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela inicial (launcher) da criança. Mostra apenas os apps liberados pelo
 * responsável em [AppPickerActivity]. Segurar o título abre a área do responsável.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var kiosk: KioskManager
    private lateinit var recycler: RecyclerView
    private var showingBlocked = false
    private var askedForHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Desenha por trás das barras do sistema (transparentes no tema), como um
        // launcher; o conteúdo recebe o espaço delas como margem logo abaixo.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_launcher)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.launcherRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // Voltar na tela inicial não faz nada (e não fecha o launcher).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        kiosk = KioskManager(this)
        CommandService.start(this)

        recycler = findViewById(R.id.appsGrid)
        recycler.layoutManager = GridLayoutManager(this, 4)

        findViewById<View>(R.id.btnSetHome).setOnClickListener { requestHome() }

        findViewById<View>(R.id.btnGuardianBlocked).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        findViewById<View>(R.id.header).setOnLongClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
            true
        }

        // Enquanto a criança está no launcher, reavalia as regras de tempos em
        // tempos: a janela de uso pode abrir ou o responsável pode liberar tempo
        // pelo painel, e o aviso de descanso não pode ficar preso na tela.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(RULES_RECHECK_MS)
                    if (checkRules(this@LauncherActivity).blocked != showingBlocked) refreshApps()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // De volta à tela da criança: a área do responsável exige o PIN outra vez.
        GuardianArea.lock()
        kiosk.enforceLauncherLockdown()
        kiosk.startLockTask(this)
        updateHomePrompt()
        // Recarrega a cada volta: o responsável pode ter mudado a lista.
        refreshApps()
    }

    /**
     * Sem Device Owner não dá para impor a tela inicial: pedimos uma vez ao abrir e,
     * se recusado, deixamos um aviso com botão (sem insistir a cada volta).
     */
    private fun updateHomePrompt() {
        val prompt = findViewById<View>(R.id.homePrompt)
        if (kiosk.canForceHome || kiosk.isDefaultHome()) {
            prompt.visibility = View.GONE
            return
        }
        prompt.visibility = View.VISIBLE
        if (!askedForHome) {
            askedForHome = true
            requestHome()
        }
    }

    private fun requestHome() {
        try {
            homeRequest.launch(kiosk.homeRequestIntent())
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Sem tela para escolher o launcher", e)
        }
    }

    private val homeRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateHomePrompt()
        }

    private fun refreshApps() {
        // Fora do horário, nem carrega a lista: a criança vê o aviso de descanso.
        val verdict = checkRules(this)
        showingBlocked = verdict.blocked
        val blockedView = findViewById<TextView>(R.id.blockedHint)
        val emptyView = findViewById<TextView>(R.id.emptyHint)
        val guardianBtn = findViewById<View>(R.id.btnGuardianBlocked)
        if (verdict.blocked) {
            blockedView.text = blockedMessage(verdict)
            blockedView.visibility = View.VISIBLE
            guardianBtn.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            recycler.visibility = View.GONE
            return
        }
        blockedView.visibility = View.GONE
        guardianBtn.visibility = View.GONE
        recycler.visibility = View.VISIBLE

        lifecycleScope.launch {
            val allowed = kiosk.allowedPackages
            val apps = withContext(Dispatchers.IO) {
                launchableApps().filter { it.packageName in allowed }
            }
            emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            recycler.adapter = AppsAdapter(apps) { pkg ->
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            }
        }
    }

    private fun blockedMessage(verdict: Verdict): String = when (verdict) {
        is Verdict.OutsideWindow ->
            getString(R.string.blocked_outside_window, verdict.rules.startText, verdict.rules.endText)
        is Verdict.BudgetSpent ->
            getString(R.string.blocked_budget_spent, verdict.limitMinutes)
        Verdict.Allowed -> ""
    }

    private companion object {
        const val TAG = "LauncherActivity"
        const val RULES_RECHECK_MS = 30_000L
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
