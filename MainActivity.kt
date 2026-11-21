package com.rh.paymentreader

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var p: SharedPreferences
    private val h = Handler(Looper.getMainLooper())
    private val BG = 0xFF0F172A.toInt(); private val CARD = 0xFF1E293B.toInt()
    private val GREEN = 0xFF16A34A.toInt(); private val BLUE = 0xFF2563EB.toInt(); private val RED = 0xFFEF4444.toInt()

    override fun onCreate(b: Bundle?) { super.onCreate(b); p = getSharedPreferences("rh", 0); splash() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); setPadding(dp(24), dp(56), dp(24), dp(24))
    }
    private fun tv(t: String, size: Float = 16f, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun bg(c: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(14).toFloat() }
    private fun btn(t: String, c: Int, on: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); background = bg(c); setOnClickListener { on() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) }
    }
    private fun setEnabledBtn(b: Button, en: Boolean, c: Int) { b.isEnabled = en; b.background = bg(if (en) c else 0xFF475569.toInt()) }
    private fun et(hint: String, num: Boolean = false) = EditText(this).apply {
        this.hint = hint; setHintTextColor(0xFF94A3B8.toInt()); setTextColor(Color.WHITE); background = bg(CARD)
        setPadding(dp(16), dp(14), dp(16), dp(14)); isSingleLine = true
        if (num) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    // 1) Splash
    private fun splash() {
        val r = root().apply { gravity = Gravity.CENTER }
        r.addView(tv("RH PAY READER", 30f, Color.WHITE, true))
        r.addView(tv("পেমেন্ট এসএমএস রিডার", 14f, 0xFF94A3B8.toInt()))
        r.addView(ProgressBar(this).apply { setPadding(0, dp(32), 0, 0) })
        setContentView(r)
        h.postDelayed({
            when {
                p.getString("db", "").isNullOrEmpty() -> config()
                p.getString("user", "").isNullOrEmpty() -> username()
                else -> askPermissions()
            }
        }, 1800)
    }

    // 2) Firebase config
    private fun config() {
        val r = root()
        r.addView(tv("Firebase সেটআপ", 24f, Color.WHITE, true))
        r.addView(tv("ওয়েবসাইটে যে Realtime Database ব্যবহার করছেন তার তথ্য দিন", 14f, 0xFF94A3B8.toInt()))
        val db = et("Database URL (https://xxxx.firebaseio.com)").apply { setText(p.getString("db", "")) }
        val au = et("Database Secret / Auth (ঐচ্ছিক)")
        val next = btn("পরবর্তী", BLUE) {}
        next.setOnClickListener {
            val u = db.text.toString().trim()
            if (!u.startsWith("https://")) { Toast.makeText(this, "সঠিক Database URL দিন", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            p.edit().putString("db", u).putString("auth", au.text.toString().trim()).apply(); username()
        }
        r.addView(db); r.addView(au); r.addView(next); setContentView(r)
    }

    // 3) Username (one username = one account)
    private fun username() {
        val r = root()
        r.addView(tv("ইউজার নেম", 24f, Color.WHITE, true))
        r.addView(tv("একটি ইউজার নেমে শুধু একটি অ্যাকাউন্ট খোলা যাবে (a-z, 0-9, _ ; ৩-২০ অক্ষর)", 14f, 0xFF94A3B8.toInt()))
        val e = et("ইউজার নেম লিখুন")
        val st = tv("", 22f, Color.WHITE, true).apply { setPadding(0, dp(16), 0, 0) }
        val ok = btn("OK", BLUE) {}
        setEnabledBtn(ok, false, GREEN)
        var task: Runnable? = null
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                task?.let { h.removeCallbacks(it) }
                setEnabledBtn(ok, false, GREEN); st.text = ""
                val u = s.toString().trim().lowercase()
                if (!Regex("^[a-z0-9_]{3,20}$").matches(u)) return
                st.text = "যাচাই হচ্ছে..."; st.setTextColor(0xFF94A3B8.toInt())
                task = Runnable {
                    Thread {
                        val (code, body) = Fb.req(this@MainActivity, "GET", "/readers/$u")
                        runOnUiThread {
                            if (e.text.toString().trim().lowercase() != u) return@runOnUiThread
                            when {
                                code in 200..299 && body.trim() == "null" -> { st.text = "ভেরিফাই ✓"; st.setTextColor(0xFF22C55E.toInt()); setEnabledBtn(ok, true, GREEN) }
                                code in 200..299 -> { st.text = "USERNAME IS USED"; st.setTextColor(RED) }
                                else -> { st.text = "ডাটাবেসে সংযোগ হয়নি ($code)"; st.setTextColor(RED) }
                            }
                        }
                    }.start()
                }
                h.postDelayed(task!!, 500)
            }
        })
        ok.setOnClickListener {
            val u = e.text.toString().trim().lowercase()
            setEnabledBtn(ok, false, GREEN)
            Thread {
                // conditional write: fails with 412 if someone already holds this username
                val (code, _) = Fb.req(this, "PUT", "/readers/$u", """{"created":${System.currentTimeMillis()}}""", mapOf("if-match" to "null_etag"))
                runOnUiThread {
                    if (code in 200..299) { p.edit().putString("user", u).apply(); askPermissions() }
                    else { st.text = "USERNAME IS USED"; st.setTextColor(RED) }
                }
            }.start()
        }
        r.addView(e); r.addView(st); r.addView(ok); setContentView(r)
    }

    // 4) Permissions → 5) Dashboard
    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 1)
    }
    override fun onRequestPermissionsResult(c: Int, ps: Array<out String>, gr: IntArray) { dashboard() }

    private fun dashboard() {
        val r = root()
        r.addView(tv("RH PAY READER", 22f, Color.WHITE, true))
        r.addView(tv("ইউজার: ${p.getString("user", "")}  •  চালু আছে ✓", 14f, 0xFF22C55E.toInt()))
        val vals = HashMap<String, TextView>()
        fun card(title: String, key: String, color: Int) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = bg(CARD); setPadding(dp(20), dp(16), dp(20), dp(16))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            }
            box.addView(tv(title, 14f, 0xFF94A3B8.toInt()))
            val v = tv("0", 34f, color, true); vals[key] = v; box.addView(v); r.addView(box)
        }
        card("মোট ফায়ারবেসে পাঠানো মেসেজ", "sent", Color.WHITE)
        card("সফলভাবে পৌঁছেছে", "ok", 0xFF22C55E.toInt())
        card("ব্যর্থ", "fail", RED)
        r.addView(btn("টেস্ট মেসেজ", BLUE) { testDialog() })
        setContentView(r)
        val t = object : Runnable { override fun run() { vals.forEach { (k, v) -> v.text = p.getInt(k, 0).toString() }; h.postDelayed(this, 1000) } }
        h.post(t)
    }

    private fun testDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val a = et("Amount (যেমন 100)", true); val t = et("Transaction ID")
        a.setTextColor(Color.BLACK); t.setTextColor(Color.BLACK); a.background = null; t.background = null
        box.addView(a); box.addView(t)
        AlertDialog.Builder(this).setTitle("টেস্ট মেসেজ").setView(box)
            .setPositiveButton("Send") { _, _ ->
                val am = a.text.toString().trim(); val tx = t.text.toString().trim()
                if (am.isEmpty() || tx.isEmpty()) { Toast.makeText(this, "Amount ও Transaction ID দিন", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                Thread {
                    val ok = Fb.sendPayment(this, "TEST", am, tx, true)
                    runOnUiThread { Toast.makeText(this, if (ok) "পাঠানো হয়েছে ✓" else "পাঠানো যায়নি", Toast.LENGTH_SHORT).show() }
                }.start()
            }.setNegativeButton("Cancel", null).show()
    }
}
