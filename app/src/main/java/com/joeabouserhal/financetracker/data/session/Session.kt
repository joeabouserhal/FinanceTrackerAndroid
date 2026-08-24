package com.joeabouserhal.financetracker.data.session

import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID

/** Active data partition. [isGuest] means offline-only, never synced. */
data class Session(val ownerId: String = GUEST_OWNER_ID) {
  val isGuest: Boolean get() = ownerId == GUEST_OWNER_ID
}
