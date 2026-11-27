package org.example.project.storefront

data class Product(
  val id: String,
  val title: String,
  val category: String,
  val price: Double,
  val description: String,
  val tags: List<String>,
  val rating: Double,
  val stock: Int = 10
)

data class CartItem(
  val product: Product,
  val quantity: Int = 1,
  val giftWrap: Boolean = false
)

enum class ShippingMethod(
  val label: String,
  val eta: String,
  val fee: Double
) {
  STANDARD("Standard Shipping", "3-5 business days", 0.0),
  EXPRESS("Express Delivery", "Tomorrow by 6 PM", 9.99)
}

enum class StorefrontStep {
  AUTH,
  FEED,
  CHECKOUT,
  SUCCESS
}

data class StorefrontUiState(
  val step: StorefrontStep = StorefrontStep.AUTH,
  val email: String = "",
  val password: String = "",
  val emailError: String? = null,
  val passwordError: String? = null,
  val isLoading: Boolean = false,
  val userEmail: String? = null,
  val products: List<Product> = emptyList(),
  val selectedProduct: Product? = null,
  val isBottomSheetOpen: Boolean = false,
  val isGiftWrapped: Boolean = false,
  val cart: List<CartItem> = emptyList(),
  val shippingMethod: ShippingMethod = ShippingMethod.STANDARD,
  val orderId: String? = null
)

sealed interface StorefrontUserAction {
  data class UpdateEmail(val email: String) : StorefrontUserAction
  data class UpdatePassword(val password: String) : StorefrontUserAction
  data object SubmitLogin : StorefrontUserAction

  data class SelectProduct(val product: Product) : StorefrontUserAction
  data object CloseBottomSheet : StorefrontUserAction
  data class ToggleGiftWrap(val giftWrap: Boolean) : StorefrontUserAction
  data class AddToCart(val product: Product) : StorefrontUserAction
  data object ProceedToCheckout : StorefrontUserAction
  data object RefreshCatalog : StorefrontUserAction

  data class SelectShippingMethod(val method: ShippingMethod) : StorefrontUserAction
  data object ConfirmCheckout : StorefrontUserAction
  data object ResetFlow : StorefrontUserAction
  data object SignOut : StorefrontUserAction
}
