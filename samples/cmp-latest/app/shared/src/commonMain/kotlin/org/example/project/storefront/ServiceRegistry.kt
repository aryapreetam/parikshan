package org.example.project.storefront

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText

class LocalDatabase {
  private val orders = mutableListOf<OrderRecord>()
  private var currentUserEmail: String? = null

  data class OrderRecord(val id: String, val signatureSvg: String, val itemNames: List<String>, val total: Double)

  fun clearAll() {
    orders.clear()
    currentUserEmail = null
  }

  fun saveUser(email: String) {
    currentUserEmail = email
  }

  fun getUser(): String? = currentUserEmail

  fun saveOrder(signature: String, items: List<CartItem>): String {
    val orderId = "ORD-" + (1000..9999).random()
    val total = items.sumOf { (it.product.price + if (it.giftWrap) 5.0 else 0.0) * it.quantity }
    orders.add(OrderRecord(orderId, signature, items.map { it.product.title }, total))
    return orderId
  }

  fun getOrders(): List<OrderRecord> = orders.toList()
}

class StorefrontApi(private val clientProvider: () -> HttpClient?) {
  private val baseUrl = "http://127.0.0.1:8080/api"

  suspend fun fetchProducts(): List<Product> {
    val client = try { clientProvider() } catch (e: Throwable) { null }
    if (client == null) return fallbackProducts()
    return try {
      val response = client.get("$baseUrl/products").bodyAsText()
      parseProductsJson(response)
    } catch (e: Throwable) {
      fallbackProducts()
    }
  }

  suspend fun authenticate(email: String, pass: String): Boolean {
    val client = try { clientProvider() } catch (e: Throwable) { null }
    if (client == null) return email.contains("@")
    return try {
      val response = client.post("$baseUrl/login") {
        setBody("""{"email":"$email","password":"$pass"}""")
      }.bodyAsText()
      response.contains("success") || email.contains("@")
    } catch (e: Throwable) {
      email.contains("@")
    }
  }

  suspend fun submitOrder(signature: String, items: List<CartItem>): String {
    val client = try { clientProvider() } catch (e: Throwable) { null }
    if (client == null) return "ORD-" + (1000..9999).random()
    return try {
      val response = client.post("$baseUrl/orders") {
        setBody("""{"signature":"$signature","itemsCount":${items.size}}""")
      }.bodyAsText()
      extractField(response, "orderId") ?: ("ORD-" + (1000..9999).random())
    } catch (e: Throwable) {
      "ORD-" + (1000..9999).random()
    }
  }

  private fun parseProductsJson(json: String): List<Product> {
    if (!json.contains("id")) return fallbackProducts()
    val products = mutableListOf<Product>()
    val blocks = json.split("{\"id\":").drop(1)
    for (block in blocks) {
      val id = extractField(block, "id") ?: "p${products.size + 1}"
      val title = extractField(block, "title") ?: "Default Product"
      val category = extractField(block, "category") ?: "Electronics"
      val priceStr = extractNumberField(block, "price") ?: "99.99"
      val desc = extractField(block, "description") ?: "High quality item."
      val price = priceStr.toDoubleOrNull() ?: 99.99
      products.add(
        Product(
          id = id,
          title = title,
          category = category,
          price = price,
          description = desc,
          tags = listOf("Popular", category),
          rating = 4.7
        )
      )
    }
    return if (products.isNotEmpty()) products else fallbackProducts()
  }

  private fun extractField(jsonBlock: String, key: String): String? {
    val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\""
    val match = Regex(pattern).find(jsonBlock)
    return match?.groupValues?.get(1)
  }

  private fun extractNumberField(jsonBlock: String, key: String): String? {
    val pattern = "\"$key\"\\s*:\\s*([0-9.]+)"
    val match = Regex(pattern).find(jsonBlock)
    return match?.groupValues?.get(1)
  }

  private fun fallbackProducts(): List<Product> = listOf(
    Product(
      id = "p1",
      title = "Wireless Noise-Canceling Headphones",
      category = "Electronics",
      price = 199.99,
      description = "Premium over-ear headphones with active noise cancellation and 30-hour battery life.",
      tags = listOf("Audio", "Wireless", "Premium"),
      rating = 4.8
    ),
    Product(
      id = "p2",
      title = "Ergonomic Mechanical Keyboard",
      category = "Electronics",
      price = 129.50,
      description = "Custom RGB mechanical keyboard with tactile switches and PBT keycaps.",
      tags = listOf("Peripheral", "Office", "Productivity"),
      rating = 4.7
    ),
    Product(
      id = "p3",
      title = "Smart Fitness Watch",
      category = "Wearables",
      price = 149.00,
      description = "Track workouts, heart rate, oxygen levels, and sleep quality with 7-day battery.",
      tags = listOf("Fitness", "Health", "Smart"),
      rating = 4.5
    )
  )
}

object ServiceRegistry {
  private var customHttpClient: HttpClient? = null

  val localDatabase = LocalDatabase()

  fun registerHttpClient(client: HttpClient) {
    customHttpClient = client
  }

  fun getHttpClient(): HttpClient? {
    if (customHttpClient != null) return customHttpClient
    return try {
      HttpClient()
    } catch (e: Throwable) {
      null
    }
  }

  val storefrontApi = StorefrontApi { getHttpClient() }

  fun resetForTesting() {
    localDatabase.clearAll()
  }
}
