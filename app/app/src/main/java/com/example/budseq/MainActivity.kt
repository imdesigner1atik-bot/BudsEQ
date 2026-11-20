package com.example.budseq

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null

    private val bandSeekBars = mutableListOf<SeekBar>()
    private var minLevel: Int = 0
    private var maxLevel: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            eq = Equalizer(0, 0).apply { enabled = true }
        } catch (e: Exception) { }
        try {
            bass = BassBoost(0, 0).apply { enabled = true }
        } catch (e: Exception) { }
        try {
            virt = Virtualizer(0, 0).apply { enabled = true }
        } catch (e: Exception) { }
        try {
            loud = LoudnessEnhancer(0).apply { enabled = true }
        } catch (e: Exception) { }

        if (eq == null) {
            Toast.makeText(this, "Equalizer not supported on this phone", Toast.LENGTH_LONG).show()
        }

        buildBands()
        setupMaster()
        setupExtras()

        findViewById<SwitchCompat>(R.id.switchEq).setOnCheckedChangeListener { _, on ->
            eq?.enabled = on
            bass?.enabled = on
            virt?.enabled = on
            loud?.enabled = on
        }
    }

    private fun buildBands() {
        val e = eq ?: return
        val container = findViewById<LinearLayout>(R.id.bandContainer)
        minLevel = e.bandLevelRange[0].toInt()
        maxLevel = e.bandLevelRange[1].toInt()
        val range = maxLevel - minLevel

        for (i in 0 until e.numberOfBands.toInt()) {
            val band = i.toShort()
            val freq = e.getCenterFreq(band) / 1000
            val label = TextView(this)
            label.text = "$freq Hz"
            val seek = SeekBar(this)
            seek.max = range
            seek.progress = e.getBandLevel(band).toInt() - minLevel
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) e.setBandLevel(band, (p + minLevel).toShort())
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            bandSeekBars.add(seek)
            container.addView(label)
            container.addView(seek)
        }
    }

    private fun setupMaster() {
        val e = eq ?: return
        val master = findViewById<SeekBar>(R.id.seekMaster)
        var last = master.progress

        master.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val delta = ((p - last) / 100f * (maxLevel - minLevel)).toInt()
                last = p
                bandSeekBars.forEachIndexed { i, sb ->
                    val newProg = (sb.progress + delta).coerceIn(0, sb.max)
                    sb.progress = newProg
                    e.setBandLevel(i.toShort(), (newProg + minLevel).toShort())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnMax).setOnClickListener {
            bandSeekBars.forEachIndexed { i, sb ->
                sb.progress = sb.max
                e.setBandLevel(i.toShort(), maxLevel.toShort())
            }
            master.progress = 100
            last = 100
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            val mid = (-minLevel).coerceIn(0, maxLevel - minLevel)
            bandSeekBars.forEachIndexed { i, sb ->
                sb.progress = mid
                e.setBandLevel(i.toShort(), 0)
            }
            master.progress = 50
            last = 50
        }
    }

    private fun setupExtras() {
        findViewById<SeekBar>(R.id.seekBass).setOnSeekBarChangeListener(simple { p ->
            try { bass?.setStrength(p.toShort()) } catch (_: Exception) {}
        })
        findViewById<SeekBar>(R.id.seekSurround).setOnSeekBarChangeListener(simple { p ->
            try { virt?.setStrength(p.toShort()) } catch (_: Exception) {}
        })
        // Volume boost in millibels: 1500 = +15 dB. Increase slowly, loud sound can hurt your ears.
        findViewById<SeekBar>(R.id.seekLoud).setOnSeekBarChangeListener(simple { p ->
            try { loud?.setTargetGain(p) } catch (_: Exception) {}
        })
    }

    private fun simple(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
            if (fromUser) onChange(p)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        eq?.release()
        bass?.release()
        virt?.release()
        loud?.release()
    }
}
