package com.rh.paymentreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (c.getSharedPreferences("rh", 0).getString("user", "").isNullOrEmpty()) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
        val sender = msgs.firstOrNull()?.originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val pay = Parser.parse(sender, body) ?: return
        val pr = goAsync()
        Thread {
            try { Fb.sendPayment(c, pay.provider, pay.amount, pay.trx, false) } finally { pr.finish() }
        }.start()
    }
}
