package com.rh.paymentreader

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var p: SharedPreferences
    private val h = Handler(Looper.getMainLooper())
    private var poll: Runnable? = null
    private val INDIGO = 0xFF4F46E5.toInt(); private val GREEN = 0xFF16A34A.toInt(); private val RED = 0xFFEF4444.toInt()
    private val MUTED = 0xFF94A3B8.toInt(); private val LGREEN = 0xFF22C55E.toInt()
    private val devId by lazy { Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown" }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); p = getSharedPreferences("rh", 0)
        window.statusBarColor = 0xFF0B1220.toInt(); splash()
    }

    // ---------- helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(c: Int, r: Int = 16, stroke: Int = 0) = GradientDrawable().apply {
        setColor(c); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun lp(w: Int = -1, h: Int = -2, top: Int = 0) = LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }
    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(48), dp(22), dp(22))
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0B1220.toInt(), 0xFF1E1B4B.toInt()))
    }
    private fun tv(t: String, size: Float = 16f, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun logo(size: Int) = ImageView(this).apply {
        setImageResource(R.drawable.ic_launcher); layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }
    private fun btn(t: String, c: Int, on: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD); isAllCaps = false
        background = shape(c, 16); layoutParams = lp(-1, dp(54), 20); setOnClickListener { on() }
    }
    private fun setEnabledBtn(b: Button, en: Boolean, c: Int) { b.isEnabled = en; b.background = shape(if (en) c else 0xFF334155.toInt(), 16) }
    private fun et(hint: String, num: Boolean = false) = EditText(this).apply {
        this.hint = hint; setHintTextColor(MUTED); setTextColor(Color.WHITE)
        background = shape(0x1AFFFFFF, 14, 0x33FFFFFF); setPadding(dp(16), dp(14), dp(16), dp(14)); isSingleLine = true
        if (num) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        layoutParams = lp(-1, -2, 14)
    }
    private fun header(title: String, sub: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(logo(64)); addView(tv(title, 26f, Color.WHITE, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        addView(tv(sub, 14f, MUTED))
    }

    // ---------- 1) Splash ----------
    private fun splash() {
        val r = root().apply { gravity = Gravity.CENTER }
        val lg = logo(120).apply { alpha = 0f; scaleX = .6f; scaleY = .6f }
        val title = tv("RH PAY READER", 28f, Color.WHITE, true).apply { alpha = 0f; setPadding(0, dp(24), 0, dp(6)) }
        val sub = tv("বিকাশ • নগদ • রকেট পেমেন্ট রিডার", 14f, MUTED).apply { alpha = 0f }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(0xFF818CF8.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(4)).apply { topMargin = dp(48) }
        }
        r.addView(lg); r.addView(title); r.addView(sub); r.addView(bar); setContentView(r)
        lg.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        title.animate().alpha(1f).setStartDelay(400).setDuration(600).start()
        sub.animate().alpha(1f).setStartDelay(700).setDuration(600).start()
        h.postDelayed({
            when {
                p.getBoolean("banned", false) -> bannedScreen()
                p.getString("db", "").isNullOrEmpty() -> config()
                p.getString("user", "").isNullOrEmpty() -> username()
                else -> askPermissions()
            }
        }, 2300)
    }

    // ---------- 2) Firebase config ----------
    private fun config() {
        val r = root()
        r.addView(header("Firebase সেটআপ", "আপনার Realtime Database-এর তথ্য দিন"))
        val db = et("Database URL (https://xxxx.firebaseio.com)").apply { setText(p.getString("db", "")) }
        val au = et("Database Secret (ঐচ্ছিক)")
        val next = btn("পরবর্তী", INDIGO) {}
        next.setOnClickListener {
            val u = db.text.toString().trim()
            if (!u.startsWith("https://")) { Toast.makeText(this, "সঠিক Database URL দিন", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            p.edit().putString("db", u).putString("auth", au.text.toString().trim()).apply(); username()
        }
        r.addView(db); r.addView(au); r.addView(next); setContentView(r)
    }

    // ---------- 3) Username: one username = one account ----------
    private fun username() {
        val r = root()
        r.addView(header("ইউজার নেম", "একটি ইউজার নেমে শুধু একটি অ্যাকাউন্ট খোলা যাবে\n(a-z, 0-9, _ ; ৩-২০ অক্ষর)"))
        val e = et("ইউজার নেম লিখুন")
        val st = tv("", 22f, Color.WHITE, true).apply { setPadding(0, dp(16), 0, 0) }
        val ok = btn("OK", GREEN) {}
        setEnabledBtn(ok, false, GREEN)
        var mine = false
        var task: Runnable? = null
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                task?.let { h.removeCallbacks(it) }
                setEnabledBtn(ok, false, GREEN); st.text = ""; mine = false
                val u = s.toString().trim().lowercase()
                if (!Regex("^[a-z0-9_]{3,20}$").matches(u)) return
                st.text = "যাচাই হচ্ছে..."; st.setTextColor(MUTED)
                task = Runnable {
                    Thread {
                        val (code, body) = Fb.central("GET", "/users/$u")
                        runOnUiThread {
                            if (e.text.toString().trim().lowercase() != u) return@runOnUiThread
                            when {
                                code in 200..299 && body.trim() == "null" -> {
                                    st.text = "ভেরিফাই ✓"; st.setTextColor(LGREEN); setEnabledBtn(ok, true, GREEN)
                                }
                                code in 200..299 -> {
                                    val same = try { Fb.norm(JSONObject(body).optString("dbUrl")) == Fb.norm(p.getString("db", "")!!) } catch (x: Exception) { false }
                                    val banned = try { JSONObject(body).optBoolean("banned", false) } catch (x: Exception) { false }
                                    if (same && banned) { st.text = "🚫 এই অ্যাকাউন্ট ব্যান করা হয়েছে"; st.setTextColor(RED) }
                                    else if (same) { mine = true; st.text = "স্বাগতম! লগ ইন হবে ✓"; st.setTextColor(LGREEN); setEnabledBtn(ok, true, GREEN) }
                                    else { st.text = "USERNAME IS USED"; st.setTextColor(RED) }
                                }
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
            if (mine) {
                setEnabledBtn(ok, false, GREEN); st.text = "ডেটা লোড হচ্ছে..."; st.setTextColor(MUTED)
                Thread { Fb.restore(this, u); runOnUiThread { p.edit().putString("user", u).apply(); askPermissions() } }.start()
                return@setOnClickListener
            }
            setEnabledBtn(ok, false, GREEN)
            Thread {
                // conditional write: refused (412) if the username already exists
                val body = JSONObject().put("username", u).put("dbUrl", Fb.norm(p.getString("db", "")!!)).put("deviceName", Build.MANUFACTURER + " " + Build.MODEL).put("device", devId).put("created", System.currentTimeMillis()).toString()
                val (code, _) = Fb.central("PUT", "/users/$u", body, mapOf("if-match" to "null_etag"))
                runOnUiThread {
                    if (code in 200..299) { p.edit().putString("user", u).apply(); askPermissions() }
                    else { st.text = "USERNAME IS USED"; st.setTextColor(RED) }
                }
            }.start()
        }
        r.addView(e); r.addView(st); r.addView(ok); setContentView(r)
    }

    // ---------- 4) Permissions ----------
    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 1)
    }
    override fun onRequestPermissionsResult(c: Int, ps: Array<out String>, gr: IntArray) { dashboard() }

    // ---------- 5) Dashboard ----------
    private fun statCard(title: String, key: String, color: Int, vals: HashMap<String, TextView>, w: Int, weight: Float, top: Int, left: Int = 0): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = shape(0x1AFFFFFF, 20, 0x33FFFFFF); setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(w, -2, weight).apply { topMargin = dp(top); leftMargin = dp(left) }
            addView(tv(title, 13f, MUTED)); val v = tv("0", 34f, color, true); vals[key] = v; addView(v)
        }

    private fun dashboard() {
        val r = root().apply { setPadding(dp(22), dp(40), dp(22), dp(22)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(logo(44))
        top.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
            addView(tv("RH Pay Reader", 18f, Color.WHITE, true)); addView(tv("@${p.getString("user", "")}", 13f, MUTED))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        top.addView(tv("লগ আউট", 13f, 0xFFFCA5A5.toInt(), true).apply {
            background = shape(0x22EF4444, 12, 0x66EF4444); setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { logout() }
        })
        r.addView(top)
        r.addView(tv("● চালু আছে — SMS পর্যবেক্ষণ হচ্ছে", 13f, LGREEN, true).apply {
            background = shape(0x2216A34A, 20); setPadding(dp(14), dp(8), dp(14), dp(8)); layoutParams = lp(-2, -2, 22)
        })
        val vals = HashMap<String, TextView>()
        r.addView(statCard("মোট ফায়ারবেসে পাঠানো মেসেজ", "sent", Color.WHITE, vals, -1, 0f, 18))
        val row = LinearLayout(this).apply { layoutParams = lp(-1, -2, 0) }
        row.addView(statCard("সফলভাবে পৌঁছেছে", "ok", LGREEN, vals, 0, 1f, 14))
        row.addView(statCard("ব্যর্থ", "fail", RED, vals, 0, 1f, 14, 14))
        r.addView(row)
        r.addView(btn("টেস্ট মেসেজ পাঠান", INDIGO) { testDialog() })
        r.addView(btn("মেসেজ হিস্ট্রি", 0xFF334155.toInt()) { history() })
        setContentView(r)
        poll?.let { h.removeCallbacks(it) }
        var tick = 0
        poll = object : Runnable { override fun run() { vals.forEach { (k, v) -> v.text = p.getInt(k, 0).toString() }; if (++tick % 30 == 0) checkBan { bannedScreen() }; h.postDelayed(this, 1000) } }
        h.post(poll!!); checkBan { bannedScreen() }
    }

    private fun history() {
        poll?.let { h.removeCallbacks(it) }
        val r = root().apply { setPadding(dp(22), dp(40), dp(22), dp(22)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(tv("←", 26f, Color.WHITE, true).apply { setPadding(0, 0, dp(16), 0); setOnClickListener { dashboard() } })
        top.addView(tv("মেসেজ হিস্ট্রি", 20f, Color.WHITE, true))
        r.addView(top)
        val names = listOf("bKash", "Nagad", "Rocket")
        val tabs = LinearLayout(this).apply { layoutParams = lp(-1, -2, 18) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chips = ArrayList<TextView>()
        val fmt = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
        fun show(sel: String) {
            chips.forEach { c -> c.background = shape(if (c.text == sel) INDIGO else 0x1AFFFFFF, 20) }
            list.removeAllViews()
            val arr = try { JSONArray(p.getString("hist", "[]")) } catch (e: Exception) { JSONArray() }
            var n = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i); if (o.getString("p") != sel) continue
                n++
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; background = shape(0x1AFFFFFF, 16, 0x33FFFFFF)
                    setPadding(dp(16), dp(12), dp(16), dp(12)); layoutParams = lp(-1, -2, 10)
                }
                val head = LinearLayout(this)
                head.addView(tv("৳ " + o.getString("a"), 18f, Color.WHITE, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                head.addView(tv(if (o.getBoolean("ok")) "পৌঁছেছে ✓" else "ব্যর্থ ✗", 13f, if (o.getBoolean("ok")) LGREEN else RED, true))
                row.addView(head)
                row.addView(tv("TrxID: " + o.getString("t") + (if (o.optBoolean("test")) "  • TEST" else ""), 13f, MUTED))
                row.addView(tv(fmt.format(java.util.Date(o.getLong("time"))), 12f, MUTED))
                list.addView(row)
            }
            if (n == 0) list.addView(tv("এই অপশনে কোনো মেসেজ নেই", 14f, MUTED).apply { setPadding(0, dp(32), 0, 0); gravity = Gravity.CENTER })
        }
        names.forEach { n ->
            val c = tv(n, 14f, Color.WHITE, true).apply {
                setPadding(dp(18), dp(10), dp(18), dp(10)); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) }
                setOnClickListener { show(n) }
            }
            chips.add(c); tabs.addView(c)
        }
        r.addView(tabs)
        r.addView(ScrollView(this).apply { addView(list); layoutParams = lp(-1, 0, 6).apply { weight = 1f } })
        setContentView(r)
        show("bKash")
    }

    private fun checkBan(onBanned: () -> Unit) {
        val u = p.getString("user", "") ?: return
        Thread {
            val (code, body) = Fb.central("GET", "/users/$u")
            if (code in 200..299) {
                if (body.trim() == "null") {
                    // account deleted by admin: sign out, start over
                    p.edit().clear().apply()
                    runOnUiThread { poll?.let { h.removeCallbacks(it) }; Toast.makeText(this, "আপনার অ্যাকাউন্ট মুছে ফেলা হয়েছে", Toast.LENGTH_LONG).show(); config() }
                } else {
                    val b = try { JSONObject(body).optBoolean("banned", false) } catch (e: Exception) { false }
                    p.edit().putBoolean("banned", b).apply()
                    if (b) runOnUiThread { onBanned() }
                }
            }
        }.start()
    }

    private fun bannedScreen() {
        poll?.let { h.removeCallbacks(it) }
        val r = root().apply { gravity = Gravity.CENTER }
        r.addView(tv("🚫", 64f))
        r.addView(tv("অ্যাকাউন্ট ব্যান", 24f, RED, true).apply { setPadding(0, dp(16), 0, dp(6)) })
        r.addView(tv("আপনার অ্যাকাউন্ট অ্যাডমিন ব্যান করেছেন।", 14f, MUTED).apply { gravity = Gravity.CENTER })
        r.addView(btn("আবার যাচাই করুন", INDIGO) {
            val u = p.getString("user", "") ?: ""
            Thread {
                val (code, body) = Fb.central("GET", "/users/$u/banned")
                runOnUiThread {
                    if (code in 200..299 && body.trim() != "true") { p.edit().putBoolean("banned", false).apply(); dashboard() }
                    else Toast.makeText(this, "এখনো ব্যান আছে", Toast.LENGTH_SHORT).show()
                }
            }.start()
        })
        r.addView(btn("লগ আউট", 0xFF334155.toInt()) { p.edit().clear().apply(); config() })
        setContentView(r)
    }

    private fun logout() {
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT).setTitle("লগ আউট").setMessage("লগ আউট করলে আবার Firebase তথ্য ও ইউজার নেম দিতে হবে।")
            .setPositiveButton("লগ আউট") { _, _ ->
                poll?.let { h.removeCallbacks(it) }; p.edit().clear().apply(); config()
            }.setNegativeButton("বাতিল", null).show()
    }

    private fun testDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun field(hint: String, num: Boolean) = EditText(this).apply {
            this.hint = hint; setTextColor(Color.BLACK); isSingleLine = true
            if (num) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val a = field("Amount (যেমন 100)", true); val t = field("Transaction ID", false)
        val names = listOf("bKash", "Nagad", "Rocket")
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        names.forEachIndexed { i, n -> rg.addView(RadioButton(this).apply { id = 100 + i; text = n; isChecked = i == 0 }) }
        box.addView(rg); box.addView(a); box.addView(t)
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT).setTitle("টেস্ট মেসেজ").setView(box)
            .setPositiveButton("Send") { _, _ ->
                val am = a.text.toString().trim(); val tx = t.text.toString().trim()
                val pv = names[(rg.checkedRadioButtonId - 100).coerceIn(0, 2)]
                if (am.isEmpty() || tx.isEmpty()) { Toast.makeText(this, "Amount ও Transaction ID দিন", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                Thread {
                    val ok = Fb.sendPayment(this, pv, am, tx, true)
                    runOnUiThread { Toast.makeText(this, if (ok) "পাঠানো হয়েছে ✓" else "পাঠানো যায়নি", Toast.LENGTH_SHORT).show() }
                }.start()
            }.setNegativeButton("Cancel", null).show()
    }
}
