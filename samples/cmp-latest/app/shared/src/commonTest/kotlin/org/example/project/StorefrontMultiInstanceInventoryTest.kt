package org.example.project

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Selector
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class StorefrontMultiInstanceInventoryTest : E2ETestLifecycle {

  private suspend fun resetBackendInventory(stock: Int = 2) {
    val client = HttpClient()
    try {
      val response = client.get("http://127.0.0.1:8080/api/inventory/reset?stock=$stock")
      println("Reset backend inventory ($stock) response: ${response.status}")
    } finally {
      client.close()
    }
  }

  @Test
  fun testStorefrontMultiInstanceInventorySync() = e2eTest(listOf("alice", "bob")) { (alice, bob) ->
    resetBackendInventory(stock = 2)

    if (alice.hasVisibleNode("storefront_sign_out_button")) {
      alice.click("storefront_sign_out_button")
    }
    if (bob.hasVisibleNode("storefront_sign_out_button")) {
      bob.click("storefront_sign_out_button")
    }

    // 1. Initial Authentication on both instances
    alice.assertVisible("Customer Sign In")
    alice.input("storefront_email_input", "alice@example.com")
    alice.input("storefront_password_input", "password123")
    alice.click("storefront_login_button")
    alice.assertVisible("Storefront Catalog")

    bob.assertVisible("Customer Sign In")
    bob.input("storefront_email_input", "bob@example.com")
    bob.input("storefront_password_input", "password123")
    bob.click("storefront_login_button")
    bob.assertVisible("Storefront Catalog")

    // Both observe initial stock = 2
    alice.assertVisible("Stock: 2")
    bob.assertVisible("Stock: 2")

    // 2. Alice purchases 1 unit of product A (p1)
    alice.click(Selector.Tag("add_to_cart_button_p1"))
    alice.assertVisible(Selector.Tag("checkout_button"))
    alice.click(Selector.Tag("checkout_button"))

    alice.assertVisible("Checkout & Delivery")
    alice.drawSignature("storefront_signature_canvas")
    alice.click(Selector.Tag("complete_order_button"))

    alice.assertVisible("Order Confirmed!")
    alice.click(Selector.Tag("back_to_products_button"))
    alice.assertVisible("Storefront Catalog")

    // 3. Bob refreshes catalog and verifies remaining stock is 1
    bob.click(Selector.Tag("refresh_catalog_button"))
    bob.assertVisible(Selector.Tag("stock_text_p1"))
    bob.assertVisible("Stock: 1")

    // 4. Alice purchases the second (remaining) unit
    alice.click(Selector.Tag("add_to_cart_button_p1"))
    alice.assertVisible(Selector.Tag("checkout_button"))
    alice.click(Selector.Tag("checkout_button"))

    alice.assertVisible("Checkout & Delivery")
    alice.drawSignature("storefront_signature_canvas")
    alice.click(Selector.Tag("complete_order_button"))

    alice.assertVisible("Order Confirmed!")
    alice.click(Selector.Tag("back_to_products_button"))

    // 5. Bob refreshes catalog and verifies product is "Out of Stock" (purchase disabled)
    bob.click(Selector.Tag("refresh_catalog_button"))
    bob.assertVisible(Selector.Tag("out_of_stock_badge_p1"))
    bob.assertVisible("Out of Stock")

    alice.click("storefront_sign_out_button")
    bob.click("storefront_sign_out_button")

    resetBackendInventory(stock = 2)
  }

  private suspend fun E2ETestScope.drawSignature(canvasTag: String) {
    val canvasNode = resolveNode(Selector.Tag(canvasTag))
    val bounds = canvasNode.bounds
    val startX = bounds.left + 30.0
    val centerY = bounds.centerY

    drag(fromX = startX, fromY = centerY, toX = startX + 40.0, toY = centerY - 15.0, durationMs = 100)
    drag(fromX = startX + 40.0, fromY = centerY - 15.0, toX = startX + 70.0, toY = centerY + 15.0, durationMs = 100)
  }
}
