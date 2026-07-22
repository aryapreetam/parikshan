package org.example.project.storefront

import androidx.compose.ui.geometry.Offset

data class Product(
  val id: String,
  val title: String,
  val category: String,
  val price: Double,
  val description: String,
  val tags: List<String>,
  val rating: Double
)

data class CartItem(
  val product: Product,
  val quantity: Int = 1,
  val giftWrap: Boolean = false
)

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
  val signaturePoints: List<Offset> = emptyList(),
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

  data class AddSignaturePoint(val point: Offset) : StorefrontUserAction
  data object ClearSignature : StorefrontUserAction
  data object ConfirmCheckout : StorefrontUserAction
  data object ResetFlow : StorefrontUserAction
}
