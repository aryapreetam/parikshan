package org.example.project.storefront

class StorefrontRepository(
  private val api: StorefrontApi = ServiceRegistry.storefrontApi,
  private val database: LocalDatabase = ServiceRegistry.localDatabase
) {

  suspend fun login(email: String, pass: String): Result<Boolean> {
    return try {
      val success = api.authenticate(email, pass)
      if (success) {
        database.saveUser(email)
      }
      Result.success(success)
    } catch (e: Throwable) {
      Result.failure(e)
    }
  }

  suspend fun fetchProducts(): Result<List<Product>> {
    return try {
      val products = api.fetchProducts()
      Result.success(products)
    } catch (e: Throwable) {
      Result.failure(e)
    }
  }

  suspend fun submitOrder(signature: String, cart: List<CartItem>): Result<String> {
    return try {
      val apiOrderId = api.submitOrder(signature, cart)
      val orderId = database.saveOrder(signature, cart)
      Result.success(if (apiOrderId.isNotBlank()) apiOrderId else orderId)
    } catch (e: Throwable) {
      val fallbackId = database.saveOrder(signature, cart)
      Result.success(fallbackId)
    }
  }

  fun getSavedUser(): String? = database.getUser()
}
