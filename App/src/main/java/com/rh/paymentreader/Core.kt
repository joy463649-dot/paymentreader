package com.rh.paymentreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** >>> আপনার নিজের (অ্যাডমিন) Firebase Realtime Database-এর URL এখানে বসান, তারপর অ্যাপ বিল্ড করুন <<< */
const val CENTRAL_URL = "https://turbd-750fc-default-rtdb.asia-southeast1.firebasedatabase.app"

object Fb {
    fun norm(s: String) = s.trim().trimEnd('/').lowercase()

    private fun call(url: String, method: String, body: String?, headers: Map<String, String>): Pair<Int, String> = try {
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = method
        con.connectTimeout = 10000; con.readTimeout = 10000
        headers.forEach { (k, v) -> con.setRequestProperty(k, v) }
        if (body != null) {
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = con.responseCode
        val txt = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.readText() ?: ""
        Pair(code, txt)
    } catch (e: Exception) { Pair(-1, e.message ?: "error") }

    /** The user's own database (the one their website uses). */
    fun req(c: Context, method: String, path: String, body: String? = null, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val p = c.getSharedPreferences("rh", 0)
        val base = p.getString("db", "")!!.trim().trimEnd('/')
        val auth = p.getString("auth", "")!!.trim()
        return call("$base$path.json" + if (auth.isNotEmpty()) "?auth=$auth" else "", method, body, headers)
    }

    /** The owner's central database: usernames + device names for the admin panel. */
    fun central(method: String, path: String, body: String? = null, headers: Map<String, String> = emptyMap()) =
        call(CENTRAL_URL.trim().trimEnd('/') + path + ".json", method, body, headers)

    /** Sends one payment record. The website admin panel matches on trxId + amount. */
    fun sendPayment(c: Context, provider: String, amount: String, trxId: String, test: Boolean): Boolean {
        val p = c.getSharedPreferences("rh", 0)
        val user = p.getString("user", "") ?: ""
        val bn = central("GET", "/users/$user/banned")
        if (bn.first in 200..299 && bn.second.trim() == "true") { p.edit().putBoolean("banned", true).apply(); return false }
        val now = System.currentTimeMillis()
        val j = JSONObject()
            .put("provider", provider).put("amount", amount.toDoubleOrNull() ?: 0.0)
            .put("trxId", trxId.uppercase()).put("reader", user)
            .put("test", test).put("status", "unused").put("time", now)
        val ok = req(c, "POST", "/payments", j.toString()).first in 200..299
        if (ok) {
            // per-user history copy so a returning user sees past messages after login
            val hj = JSONObject().put("p", provider).put("a", amount).put("t", trxId.uppercase()).put("time", now).put("test", test)
            req(c, "POST", "/history/$user", hj.toString())
        }
        synchronized(this) {
            val old = try { JSONArray(p.getString("hist", "[]")) } catch (e: Exception) { JSONArray() }
            val item = JSONObject().put("p", provider).put("a", amount).put("t", trxId.uppercase())
                .put("ok", ok).put("time", now).put("test", test)
            val out = JSONArray().put(item)
            for (i in 0 until minOf(old.length(), 199)) out.put(old.get(i))
            p.edit().putString("hist", out.toString()).apply()
            p.edit().putInt("sent", p.getInt("sent", 0) + 1)
                .putInt(if (ok) "ok" else "fail", p.getInt(if (ok) "ok" else "fail", 0) + 1).apply()
        }
        return ok
    }

    /** On login: load this username's earlier messages from the user's own database. */
    fun restore(c: Context, user: String) {
        val p = c.getSharedPreferences("rh", 0)
        val (code, txt) = req(c, "GET", "/history/$user")
        if (code !in 200..299 || txt.trim() == "null") return
        val items = ArrayList<JSONObject>()
        try { val o = JSONObject(txt); o.keys().forEach { k -> items.add(o.getJSONObject(k)) } } catch (e: Exception) { return }
        items.sortByDescending { it.optLong("time") }
        val arr = JSONArray()
        items.take(200).forEach {
            arr.put(JSONObject().put("p", it.optString("p")).put("a", it.optString("a")).put("t", it.optString("t"))
                .put("ok", true).put("time", it.optLong("time")).put("test", it.optBoolean("test")))
        }
        p.edit().putString("hist", arr.toString()).putInt("sent", items.size).putInt("ok", items.size).putInt("fail", 0).apply()
    }
}

object Parser {
    data class Pay(val provider: String, val amount: String, val trx: String)

    fun parse(sender: String, body: String): Pay? {
        val s = sender.lowercase()
        val provider = when {
            "bkash" in s -> "bKash"
            "nagad" in s -> "Nagad"
            "rocket" in s || s == "16216" -> "Rocket"
            else -> return null
        }
        // incoming money only
        if (!Regex("received|cash in|money received", RegexOption.IGNORE_CASE).containsMatchIn(body)) return null
        val amount = Regex("(?:Tk|BDT)\\.?\\s?([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.replace(",", "") ?: return null
        val trx = Regex("(?:Trx\\s?ID|Txn\\s?ID|TxnId)\\s*[:.]?\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1) ?: return null
        return Pay(provider, amount, trx)
    }
}
