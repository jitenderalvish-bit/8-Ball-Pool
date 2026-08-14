package com.billiards.analyzer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

class AnalysisActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }

    private lateinit var tableView: TableView
    private lateinit var tvInstruction: TextView
    private lateinit var seekPower: SeekBar
    private lateinit var tvPowerValue: TextView
    private var power: Power = Power.MEDIUM
    private var decodedBitmap: Bitmap? = null

    private val modeButtons = mutableMapOf<Mode, Button>()
    private val powerButtons = mutableMapOf<Power, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        tableView = findViewById(R.id.tableView)
        tvInstruction = findViewById(R.id.tvInstruction)
        seekPower = findViewById(R.id.seekPower)
        tvPowerValue = findViewById(R.id.tvPowerValue)

        modeButtons[Mode.TABLE] = findViewById(R.id.btnModeTable)
        modeButtons[Mode.POCKETS] = findViewById(R.id.btnModePockets)
        modeButtons[Mode.BALLS] = findViewById(R.id.btnModeBalls)
        modeButtons[Mode.CUE] = findViewById(R.id.btnModeCue)
        modeButtons[Mode.TARGET] = findViewById(R.id.btnModeTarget)
        modeButtons[Mode.POCKET_SELECT] = findViewById(R.id.btnModePocketSelect)

        powerButtons[Power.LOW] = findViewById(R.id.btnPowerLow)
        powerButtons[Power.MEDIUM] = findViewById(R.id.btnPowerMed)
        powerButtons[Power.HIGH] = findViewById(R.id.btnPowerHigh)

        modeButtons.forEach { (m, btn) ->
            btn.setOnClickListener { setMode(m) }
        }
        powerButtons.forEach { (p, btn) ->
            btn.setOnClickListener {
                power = p
                val presetValue = when (p) { Power.LOW -> 25; Power.MEDIUM -> 65; Power.HIGH -> 95 }
                seekPower.progress = presetValue
                highlightPower()
            }
        }

        seekPower.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvPowerValue.text = "Power: $progress"
                if (fromUser) {
                    power = Power.nearestPreset(progress)
                    highlightPower()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val toggleBank = findViewById<ToggleButton>(R.id.toggleBank)

        findViewById<Button>(R.id.btnCalculate).setOnClickListener {
            if (tableView.cueBall() == null) {
                toast("Select a cue ball first (CUE mode)"); return@setOnClickListener
            }
            if (tableView.targetBall() == null) {
                toast("Select a target ball first (TARGET mode)"); return@setOnClickListener
            }
            if (tableView.selectedPocket() == null) {
                toast("Select a destination pocket (POCKET mode)"); return@setOnClickListener
            }
            tableView.calculateShot(power, seekPower.progress, toggleBank.isChecked)
        }

        findViewById<Button>(R.id.btnRemoveBall).setOnClickListener {
            tableView.armRemoveBall()
            toast("Tap the ball you want to remove")
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            tableView.resetAll()
            setMode(Mode.TABLE)
        }

        findViewById<Button>(R.id.btnRedetect).setOnClickListener {
            runDetection(showToast = true)
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setMode(Mode.TABLE)
        power = Power.nearestPreset(AppSettings.defaultPower(this))
        seekPower.progress = AppSettings.defaultPower(this)
        highlightPower()
        loadImage()
    }

    private fun setMode(m: Mode) {
        tableView.mode = m
        modeButtons.forEach { (mode, btn) -> btn.alpha = if (mode == m) 1f else 0.5f }
        tvInstruction.text = when (m) {
            Mode.TABLE -> "Step 1: Drag the yellow corner handles to fit the table boundary."
            Mode.POCKETS -> "Step 2: Drag each of the 6 pockets onto the real pocket positions."
            Mode.BALLS -> "Step 3: Tap empty felt to add a ball, drag to reposition. Use REMOVE BALL to delete."
            Mode.CUE -> "Step 4: Tap the white cue ball to mark it."
            Mode.TARGET -> "Step 5: Tap the ball you want to hit (the target ball)."
            Mode.POCKET_SELECT -> "Step 6: Tap the pocket you're aiming for, then press CALCULATE."
        }
    }

    private fun highlightPower() {
        powerButtons.forEach { (p, btn) -> btn.alpha = if (p == power) 1f else 0.5f }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun loadImage() {
        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI) ?: return
        val uri = Uri.parse(uriString)
        try {
            val bmp = decodeSampledBitmap(uri, 1600, 1600)
            if (bmp != null) {
                decodedBitmap = bmp
                tableView.setImage(bmp) {
                    if (AppSettings.autoDetectEnabled(this)) {
                        runDetection(showToast = false)
                    }
                }
            }
        } catch (e: Exception) {
            toast("Could not load image")
        }
    }

    /** Runs detection on a background thread (image is small/downsampled so this is fast),
     * then applies results on the UI thread. Only ever triggered explicitly (on import, or DETECT tap). */
    private fun runDetection(showToast: Boolean) {
        val bmp = decodedBitmap ?: return
        if (showToast) toast("Detecting table, balls and pockets…")
        Thread {
            val result = try {
                Detector.detect(bmp)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (result != null) {
                    tableView.applyDetection(result)
                    if (showToast) toast("Detection complete — correct anything that looks off")
                } else if (showToast) {
                    toast("Detection failed — place items manually")
                }
            }
        }.start()
    }

    /** Decodes the bitmap downsampled to a max size to keep memory use low and analysis fast. */
    private fun decodeSampledBitmap(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        var input: InputStream? = contentResolver.openInputStream(uri)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, opts)
        input?.close()

        var sampleSize = 1
        var (h, w) = opts.outHeight to opts.outWidth
        while (h / 2 >= reqHeight || w / 2 >= reqWidth) {
            h /= 2; w /= 2; sampleSize *= 2
        }

        val opts2 = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        input = contentResolver.openInputStream(uri)
        val bmp = BitmapFactory.decodeStream(input, null, opts2)
        input?.close()
        return bmp
    }

    override fun onDestroy() {
        super.onDestroy()
        tableView.releaseImage()
    }
}
