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
import kotlin.test.BeforeTest
import kotlin.test.Test

class StorefrontFlowTest : E2ETestLifecycle {

  @kotlin.test.AfterTest
  fun cleanupState() {
    // Reset test environment state after each execution
    ServiceRegistry.resetForTesting()
  }

  @BeforeTest
  fun setupMocking() {
    val mockEngine = MockEngine { request ->
      when {
        request.url.encodedPath.contains("/products") -> respond(
          content = """[
            {"id":"p1","title":"Wireless Noise-Canceling Headphones","category":"Electronics","price":199.99,"description":"Premium over-ear headphones with active noise cancellation.","tags":["Audio","Wireless","Premium"],"rating":4.8},
            {"id":"p2","title":"Ergonomic Mechanical Keyboard","category":"Electronics","price":129.50,"description":"Custom RGB mechanical keyboard with tactile switches.","tags":["Peripheral","Office"],"rating":4.7},
            {"id":"p3","title":"Smart Fitness Watch","category":"Wearables","price":149.00,"description":"Track workouts, heart rate, and sleep quality.","tags":["Fitness","Health"],"rating":4.5}
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
    assertVisible("Gift Wrap (+$5.00)")

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
}
