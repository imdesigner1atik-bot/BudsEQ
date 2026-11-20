package com.example.budseq

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private val status = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            setup()
        } catch (t: Throwable) {
            showError(t)
        }
    }

    private fun showError(t: Throwable) {
        val tv = TextView(this)
        tv.setPadding(32, 32, 32, 32)
        tv.textSize = 13f
        tv.setTextIsSelectable(true)
        tv.text = "ERROR:\n" + Log.getStackTraceString(t)
        val sv = ScrollView(this)
        sv.addView(tv)
        setContentView(sv)
    }

    private fun <T> make(name: String, block: () -> T): T? {
        return try {
            block()
        } catch (t: Throwable) {
            status.append("$name: ${t.javaClass.simpleName} ${t.message}\n")
            null
        }
    }

    private fun setup() {
        eq = make("Equalizer") { Equalizer(0, 0).apply { enabled = true } }
        bass = make("BassBoost") { BassBoost(0, 0).apply { enabled = true } }
        virt = make("Virtualizer") { Virtualizer(0, 0).apply { enabled = true } }
        loud = make("Loudness") { LoudnessEnhancer(0).apply { enabled = true } }

        if (status.isNotEmpty()) {
            Toast.makeText(this, status.toString(), Toast.LENGTH_LONG).show()
        }

        buildBands()
        setupMaster()
        setupExtras()

        findViewById<SwitchCompat>(R.id.switchEq).setOnCheckedChangeListener { _, on ->
            try { eq?.enabled = on } catch (_: Throwable) {}
            try { bass?.enabled = on } catch (_: Throwable) {}
            try { virt?.enabled = on } catch (_: Throwable) {}
            try { loud?.enabled = on } catch (_: Throwable) {}
        }
    }

    private fun setBand(i: Int, level: Int) {
        try {
            eq?.setBandLevel(i.toShort(), level.toShort())
        } catch (_: Throwable) {
        }
    }

    private fun buildBands() {
        val e = eq ?: return
        val container = findViewById<LinearLayout>(R.id.bandContainer)
        minLevel = e.bandLevelRange[0].toInt()
        maxLevel = e.bandLevelRange[1].toInt()
        val range = maxLevel - minLevel

        for (i in 0 until e.numberOfBands.toInt()) {
            val freq = e.getCenterFreq(i.toShort()) / 1000
            val label = TextView(this)
            label.text = "$freq Hz"
            val seek = SeekBar(this)
            seek.max = range
            seek.progress = e.getBandLevel(i.toShort()).toInt() - minLevel
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) setBand(i, p + minLevel)
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
        if (eq == null) return
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
                    setBand(i, newProg + minLevel)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnMax).setOnClickListener {
            bandSeekBars.forEachIndexed { i, sb ->
                sb.progress = sb.max
                setBand(i, maxLevel)
            }
            master.progress = 100
            last = 100
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            val mid = (-minLevel).coerceIn(0, maxLevel - minLevel)
            bandSeekBars.forEachIndexed { i, sb ->
                sb.progress = mid
                setBand(i, 0)
            }
            master.progress = 50
            last = 50
        }
    }

    private fun setupExtras() {
        findViewById<SeekBar>(R.id.seekBass).setOnSeekBarChangeListener(simple { p ->
            try { bass?.setStrength(p.toShort()) } catch (_: Throwable) {}
        })
        findViewById<SeekBar>(R.id.seekSurround).setOnSeekBarChangeListener(simple { p ->
            try { virt?.setStrength(p.toShort()) } catch (_: Throwable) {}
        })
        // Volume boost in millibels: 1500 = +15 dB. Increase slowly, loud sound can hurt your ears.
        findViewById<SeekBar>(R.id.seekLoud).setOnSeekBarChangeListener(simple { p ->
            try { loud?.setTargetGain(p) } catch (_: Throwable) {}
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
        try { eq?.release() } catch (_: Throwable) {}
        try { bass?.release() } catch (_: Throwable) {}
        try { virt?.release() } catch (_: Throwable) {}
        try { loud?.release() } catch (_: Throwable) {}
    }
}
