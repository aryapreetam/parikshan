package org.example.project

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.example.project.storefront.ServiceRegistry
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class StorefrontFlowTest : E2ETestLifecycle {

  @AfterTest
  fun cleanupState() {
    ServiceRegistry.resetForTesting()
  }

  @BeforeTest
  fun setupMocking() {
    val mockEngine = MockEngine { request ->
      when {
        request.url.encodedPath.contains("/products") -> respond(
          content = """[
            {"id":"p1","title":"Wireless Noise-Canceling Headphones","category":"Electronics","price":199.99,"description":"Premium over-ear headphones with active noise cancellation.","tags":["Audio","Wireless","Premium"],"rating":4.8,"stock":10},
            {"id":"p2","title":"Ergonomic Mechanical Keyboard","category":"Electronics","price":129.50,"description":"Custom RGB mechanical keyboard with tactile switches.","tags":["Peripheral","Office"],"rating":4.7,"stock":10},
            {"id":"p3","title":"Smart Fitness Watch","category":"Wearables","price":149.00,"description":"Track workouts, heart rate, and sleep quality.","tags":["Fitness","Health"],"rating":4.5,"stock":10}
          ]""",
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
        request.url.encodedPath.contains("/login") -> respond(
          content = """{"status":"success","user":{"email":"alex@example.com"}}""",
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
        request.url.encodedPath.contains("/orders") -> respond(
          content = """{"status":"success","orderId":"ORD-8821"}""",
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
        else -> respond(
          content = """{"status":"ok"}""",
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
      }
    }

    val testClient = HttpClient(mockEngine)
    ServiceRegistry.registerHttpClient(testClient)
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

    // 3. Checkout & Delivery Selection
    assertVisible("Checkout & Delivery")
    assertVisible("Delivery Method")

    // Select Express Delivery and verify shipping fee updates Order Summary
    click(Selector.Tag("shipping_option_express"))
    assertVisible(Selector.Tag("checkout_shipping_fee"))
    assertText(Selector.Tag("checkout_shipping_fee"), "$9.99")

    // Scroll to Payment Method & Place Order
    scrollUntilVisible(
      containerSelector = Selector.Tag("storefront_checkout_screen"),
      targetSelector = Selector.Tag("complete_order_button")
    )
    assertVisible("Visa ending in 4242")
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
