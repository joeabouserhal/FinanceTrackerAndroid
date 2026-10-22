package com.joeabouserhal.financetracker.ui.transactions

import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import org.junit.Assert.*
import org.junit.Test

class TransferCurrencyOptionsTest {
  private fun account(id: String, currency: String = "usd", archived: Boolean = false, deletedAt: String? = null) =
    AccountEntity(id, "guest", currency, id, archived, createdAt = "", updatedAt = "", deletedAt = deletedAt)

  @Test fun `one account currencies are unavailable but two account currencies are available`() {
    assertEquals(setOf("usd"), transferCurrencyIds(listOf(account("cash"), account("bank"), account("lbp-cash", "lbp"))))
  }

  @Test fun `archived and deleted accounts do not enable a currency`() {
    assertTrue(transferCurrencyIds(listOf(account("cash"), account("bank", archived = true), account("old", deletedAt = "deleted"))).isEmpty())
  }

  @Test fun `accounts from different currencies cannot form a transfer pair`() {
    assertTrue(transferCurrencyIds(listOf(account("cash"), account("bank", "eur"))).isEmpty())
  }

  @Test fun `empty and duplicate accounts cannot enable a currency`() {
    assertTrue(transferCurrencyIds(emptyList()).isEmpty())
    val cash = account("cash")
    assertTrue(transferCurrencyIds(listOf(cash, cash)).isEmpty())
  }
}
