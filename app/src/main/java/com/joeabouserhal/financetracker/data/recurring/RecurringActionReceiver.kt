package com.joeabouserhal.financetracker.data.recurring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.joeabouserhal.financetracker.FinanceTrackerApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecurringActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != RecurringNotifications.ACTION_VERIFY) return
    val id = intent.getStringExtra(RecurringNotifications.EXTRA_OCCURRENCE_ID) ?: return
    val pending = goAsync(); val app = context.applicationContext as? FinanceTrackerApplication ?: run { pending.finish(); return }
    app.container.applicationScope.launch {
      try { val owner = app.container.sessionManager.session.first().ownerId; app.container.recurringRepository.verify(owner, id); app.container.recurringNotifications.cancel(id) } finally { pending.finish() }
    }
  }
}
