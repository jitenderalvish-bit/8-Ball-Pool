package com.billiards.analyzer

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val switchAutoDetect = findViewById<Switch>(R.id.switchAutoDetect)
        val switchGuides = findViewById<Switch>(R.id.switchGuides)
        val seekPower = findViewById<SeekBar>(R.id.seekDefaultPower)
        val tvPowerValue = findViewById<TextView>(R.id.tvDefaultPowerValue)

        switchAutoDetect.isChecked = AppSettings.autoDetectEnabled(this)
        switchGuides.isChecked = AppSettings.showGuides(this)
        seekPower.progress = AppSettings.defaultPower(this)
        tvPowerValue.text = "Value: ${seekPower.progress}"

        switchAutoDetect.setOnCheckedChangeListener { _, checked ->
            AppSettings.setAutoDetectEnabled(this, checked)
        }
        switchGuides.setOnCheckedChangeListener { _, checked ->
            AppSettings.setShowGuides(this, checked)
        }
        seekPower.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvPowerValue.text = "Value: $progress"
                AppSettings.setDefaultPower(this@SettingsActivity, progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
