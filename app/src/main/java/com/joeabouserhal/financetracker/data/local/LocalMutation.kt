package com.joeabouserhal.financetracker.data.local

import androidx.room.withTransaction
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/** A logical action retains its batch identity across nested repository calls. */
internal class LocalMutation : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<LocalMutation>
  val id: String = UUID.randomUUID().toString()
  var index: Int = 0
}

suspend fun <T> AppDatabase.withMutation(block: suspend () -> T): T =
  if (currentCoroutineContext()[LocalMutation] != null) withTransaction(block)
  else withContext(LocalMutation()) { withTransaction(block) }
