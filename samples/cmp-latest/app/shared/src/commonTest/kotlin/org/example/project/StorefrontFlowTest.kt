package org.example.project

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex
import org.example.project.storefront.ServiceRegistry
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.Test

class StorefrontFlowTest : E2ETestLifecycle {

  @AfterTest
  fun cleanupState() {
    ServiceRegistry.resetForTesting()
  }

  @Test
  fun testCompleteStorefrontE2EFlow() = e2eTest {
    if (hasVisibleNode("storefront_sign_out_button")) {
      click("storefront_sign_out_button")
    }

    // 1. Auth Screen Validation & Login
    assertVisible("Customer Sign In")
    assertVisible("Sign In")

    input("storefront_email_input", "alex@example.com")
    input("storefront_password_input", "password123")
    click("storefront_login_button")

    // 2. Product Feed & Details Bottom Sheet
    assertVisible("Storefront Catalog")
    assertVisible("Wireless Noise-Canceling Headphones")

    // Open details sheet for the first product using atIndex(0) selector
    click(Selector.Text("View Details").atIndex(0))
    assertVisible("Product Details")
    // Drag bottom sheet upward to fully reveal the Add to Cart button if partially expanded
    expandBottomSheetUntilVisible(
      sheetSelector = Selector.Text("Product Details"),
      targetSelector = Selector.Tag("details_add_to_cart_button")
    )

    // Add to Cart from Details Sheet
    click(Selector.Tag("details_add_to_cart_button"))

    // Verify cart summary bar & proceed to checkout
    assertVisible(Selector.Tag("checkout_button"))
    click(Selector.Tag("checkout_button"))

    // 3. Delivery Signature Checkout Screen
    assertVisible("Checkout & Delivery")
    assertVisible("Draw Delivery Signature Below")

    // Draw creative cursive 'hi' signature gesture on canvas
    drawCursiveHiSignature("storefront_signature_canvas")

    // Complete Order
    scrollUntilVisible(
      containerSelector = Selector.Tag("storefront_checkout_screen"),
      targetSelector = Selector.Tag("complete_order_button")
    )
    click(Selector.Tag("complete_order_button"))

    // 4. Order Success Screen
    assertVisible("Order Confirmed!")
    assertVisible("Thank you for your purchase")

    // Reset flow back to products catalog
    click(Selector.Tag("back_to_products_button"))
    assertVisible("Storefront Catalog")
    if (hasVisibleNode("storefront_sign_out_button")) {
      click("storefront_sign_out_button")
    }
  }

  private suspend fun E2ETestScope.drawCursiveHiSignature(canvasTag: String) {
    val canvasNode = resolveNode(Selector.Tag(canvasTag))
    val bounds = canvasNode.bounds
    val startX = bounds.left + 30.0
    val centerY = bounds.centerY

    // Letter 'h' - loop up, straight down, arch over
    drag(fromX = startX, fromY = centerY + 15.0, toX = startX + 10.0, toY = centerY - 30.0, durationMs = 150)
    drag(fromX = startX + 10.0, fromY = centerY - 30.0, toX = startX + 15.0, toY = centerY + 20.0, durationMs = 150)
    drag(fromX = startX + 15.0, fromY = centerY + 20.0, toX = startX + 30.0, toY = centerY - 10.0, durationMs = 150)
    drag(fromX = startX + 30.0, fromY = centerY - 10.0, toX = startX + 40.0, toY = centerY + 20.0, durationMs = 150)

    // Connecting tail to 'i'
    drag(fromX = startX + 40.0, fromY = centerY + 20.0, toX = startX + 55.0, toY = centerY - 10.0, durationMs = 150)
    drag(fromX = startX + 55.0, fromY = centerY - 10.0, toX = startX + 62.0, toY = centerY + 20.0, durationMs = 150)

    // Dot over 'i'
    drag(fromX = startX + 58.0, fromY = centerY - 25.0, toX = startX + 60.0, toY = centerY - 23.0, durationMs = 100)
  }

  private suspend fun E2ETestScope.expandBottomSheetUntilVisible(
    sheetSelector: Selector = Selector.Auto("Product Details"),
    targetSelector: Selector,
    dragDistancePx: Double = 250.0,
    maxAttempts: Int = 3
  ) {
    for (attempt in 0 until maxAttempts) {
      if (hasVisibleNode(targetSelector)) return

      val anchorNode = resolveNode(sheetSelector)
      val startX = anchorNode.bounds.centerX
      val startY = anchorNode.bounds.top
      val endY = (startY - dragDistancePx).coerceAtLeast(30.0)

      drag(
        fromX = startX,
        fromY = startY,
        toX = startX,
        toY = endY,
        durationMs = 400L
      )
      delay(500L)
    }
  }
}
