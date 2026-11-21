package com.rh.paymentreader

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Fb {
    fun req(c: Context, method: String, path: String, body: String? = null, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val p = c.getSharedPreferences("rh", 0)
        val base = p.getString("db", "")!!.trim().trimEnd('/')
        val auth = p.getString("auth", "")!!.trim()
        val url = "$base$path.json" + if (auth.isNotEmpty()) "?auth=$auth" else ""
        return try {
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
    }

    /** Sends one payment record. The website admin panel matches on trxId + amount. */
    fun sendPayment(c: Context, provider: String, amount: String, trxId: String, test: Boolean): Boolean {
        val p = c.getSharedPreferences("rh", 0)
        val j = JSONObject()
            .put("provider", provider).put("amount", amount.toDoubleOrNull() ?: 0.0)
            .put("trxId", trxId.uppercase()).put("reader", p.getString("user", ""))
            .put("test", test).put("status", "unused").put("time", System.currentTimeMillis())
        val ok = req(c, "POST", "/payments", j.toString()).first in 200..299
        synchronized(this) {
            p.edit().putInt("sent", p.getInt("sent", 0) + 1)
                .putInt(if (ok) "ok" else "fail", p.getInt(if (ok) "ok" else "fail", 0) + 1).apply()
        }
        return ok
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
