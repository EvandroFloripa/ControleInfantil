package com.controleinfantil.kids.schedule

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.TextView
import com.controleinfantil.kids.setup.GuardianActivity
import androidx.appcompat.widget.SwitchCompat
import com.controleinfantil.kids.R

/**
 * Tela do responsável para definir os limites de uso: a janela de horário e o teto
 * de tempo por dia. Mostra também quanto já foi usado hoje, com atalhos para
 * devolver tempo à criança ou liberar o aparelho por alguns minutos.
 */
class ScheduleActivity : GuardianActivity() {

    private var rules = TimeRules()

    /** Opções do teto diário, em minutos (0 = sem limite). */
    private val limitOptions = listOf(0, 15, 30, 45, 60, 90, 120, 180)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)
        rules = TimeRules.load(this)

        findViewById<Spinner>(R.id.limitSpinner).adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            limitOptions.map { if (it == 0) getString(R.string.limit_none) else formatDuration(it) },
        )

        findViewById<SwitchCompat>(R.id.switchEnabled)
            .setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                rules = rules.copy(enabled = checked)
                updateEnabledState()
            }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            pickTime(rules.startMinute) { rules = rules.copy(startMinute = it); render() }
        }
        findViewById<Button>(R.id.btnEnd).setOnClickListener {
            pickTime(rules.endMinute) { rules = rules.copy(endMinute = it); render() }
        }

        findViewById<Button>(R.id.btnResetToday).setOnClickListener {
            UsageTracker.resetToday(this)
            render()
        }
        findViewById<Button>(R.id.btnOverride).setOnClickListener {
            TimeRules.grantOverride(this, OVERRIDE_MINUTES)
            render()
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val pos = findViewById<Spinner>(R.id.limitSpinner).selectedItemPosition
            TimeRules.save(this, rules.copy(dailyLimitMinutes = limitOptions[pos]))
            finish()
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        findViewById<SwitchCompat>(R.id.switchEnabled).isChecked = rules.enabled
        findViewById<Button>(R.id.btnStart).text =
            getString(R.string.schedule_from, rules.startText)
        findViewById<Button>(R.id.btnEnd).text =
            getString(R.string.schedule_to, rules.endText)
        findViewById<Spinner>(R.id.limitSpinner)
            .setSelection(limitOptions.indexOf(rules.dailyLimitMinutes).coerceAtLeast(0))

        val usado = UsageTracker.usedMinutesToday(this)
        findViewById<TextView>(R.id.usedToday).text =
            getString(R.string.used_today, formatDuration(usado))

        val until = TimeRules.overrideUntil(this)
        val faltam = ((until - System.currentTimeMillis()) / 60_000L).toInt()
        findViewById<TextView>(R.id.overrideInfo).apply {
            visibility = if (faltam > 0) View.VISIBLE else View.GONE
            if (faltam > 0) text = getString(R.string.override_active, faltam)
        }

        updateEnabledState()
    }

    /** Os controles de horário só fazem sentido com os limites ligados. */
    private fun updateEnabledState() {
        val on = rules.enabled
        listOf(R.id.btnStart, R.id.btnEnd, R.id.limitSpinner).forEach {
            findViewById<View>(it).isEnabled = on
        }
    }

    private fun pickTime(currentMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            currentMinute / 60,
            currentMinute % 60,
            true,
        ).show()
    }

    private fun formatDuration(minutes: Int): String = when {
        minutes < 60 -> getString(R.string.duration_minutes, minutes)
        minutes % 60 == 0 -> getString(R.string.duration_hours, minutes / 60)
        else -> getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }

    private companion object {
        const val OVERRIDE_MINUTES = 15
    }
}
