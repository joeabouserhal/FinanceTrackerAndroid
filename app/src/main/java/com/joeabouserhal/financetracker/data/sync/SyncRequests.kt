package com.joeabouserhal.financetracker.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide "a mutation was queued" signal. [OutboxWriter.enqueue] fires
 * this on every add/edit/delete op (inside the same Room transaction as the
 * mutation), and the Application layer collects it to request an immediate
 * sync. Deterministic on purpose: it does not depend on Room flow
 * invalidation timing.
 */
object SyncRequests {
  private val _mutations = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
  val mutations: SharedFlow<Unit> = _mutations.asSharedFlow()

  fun notifyMutation() {
    _mutations.tryEmit(Unit)
  }
}
