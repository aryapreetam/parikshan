package org.example.project.storefront

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StorefrontViewModel(
  private val repository: StorefrontRepository = StorefrontRepository(),
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
  private val _uiState = MutableStateFlow(StorefrontUiState())
  val uiState: StateFlow<StorefrontUiState> = _uiState.asStateFlow()

  fun onAction(action: StorefrontUserAction) {
    when (action) {
      is StorefrontUserAction.UpdateEmail -> {
        _uiState.update { it.copy(email = action.email, emailError = null) }
      }
      is StorefrontUserAction.UpdatePassword -> {
        _uiState.update { it.copy(password = action.password, passwordError = null) }
      }
      is StorefrontUserAction.SubmitLogin -> submitLogin()
      is StorefrontUserAction.SelectProduct -> {
        _uiState.update {
          it.copy(
            selectedProduct = action.product,
            isBottomSheetOpen = true,
            isGiftWrapped = false
          )
        }
      }
      is StorefrontUserAction.CloseBottomSheet -> {
        _uiState.update { it.copy(isBottomSheetOpen = false, selectedProduct = null) }
      }
      is StorefrontUserAction.ToggleGiftWrap -> {
        _uiState.update { it.copy(isGiftWrapped = action.giftWrap) }
      }
      is StorefrontUserAction.AddToCart -> addToCart(action.product)
      is StorefrontUserAction.ProceedToCheckout -> {
        _uiState.update { it.copy(step = StorefrontStep.CHECKOUT) }
      }
      is StorefrontUserAction.RefreshCatalog -> refreshCatalog()
      is StorefrontUserAction.SelectShippingMethod -> {
        _uiState.update { it.copy(shippingMethod = action.method) }
      }
      is StorefrontUserAction.ConfirmCheckout -> confirmCheckout()
      is StorefrontUserAction.ResetFlow -> resetFlow()
      is StorefrontUserAction.SignOut -> {
        _uiState.value = StorefrontUiState()
      }
    }
  }

  fun refreshCatalog() {
    scope.launch {
      val productsResult = repository.fetchProducts().getOrDefault(emptyList())
      _uiState.update { state ->
        val reservedByProduct = state.cart
          .groupBy { it.product.id }
          .mapValues { (_, items) -> items.sumOf { it.quantity } }
        val adjustedProducts = productsResult.map { product ->
          val reserved = reservedByProduct[product.id] ?: 0
          product.copy(stock = (product.stock - reserved).coerceAtLeast(0))
        }
        state.copy(products = adjustedProducts)
      }
    }
  }

  private fun submitLogin() {
    val currentEmail = uiState.value.email.trim()
    val currentPass = uiState.value.password

    var emailErr: String? = null
    var passErr: String? = null

    if (currentEmail.isEmpty() || !currentEmail.contains("@") || !currentEmail.contains(".")) {
      emailErr = "Please enter a valid email address"
    }
    if (currentPass.length < 6) {
      passErr = "Password must be at least 6 characters"
    }

    if (emailErr != null || passErr != null) {
      _uiState.update { it.copy(emailError = emailErr, passwordError = passErr) }
      return
    }

    _uiState.update { it.copy(isLoading = true) }

    scope.launch {
      repository.login(currentEmail, currentPass)
      val productsResult = repository.fetchProducts().getOrDefault(emptyList())
      _uiState.update {
        it.copy(
          isLoading = false,
          userEmail = currentEmail,
          products = productsResult,
          step = StorefrontStep.FEED
        )
      }
    }
  }

  private fun addToCart(product: Product) {
    _uiState.update { state ->
      val currentProduct = state.products.find { it.id == product.id } ?: product
      if (currentProduct.stock <= 0) {
        return@update state
      }
      val currentCart = state.cart.toMutableList()
      val giftWrap = state.isGiftWrapped
      val existingIndex = currentCart.indexOfFirst { it.product.id == product.id && it.giftWrap == giftWrap }
      if (existingIndex >= 0) {
        val item = currentCart[existingIndex]
        currentCart[existingIndex] = item.copy(quantity = item.quantity + 1)
      } else {
        currentCart.add(CartItem(product = currentProduct, quantity = 1, giftWrap = giftWrap))
      }
      val updatedProducts = state.products.map { item ->
        if (item.id == product.id) item.copy(stock = (item.stock - 1).coerceAtLeast(0)) else item
      }
      state.copy(
        products = updatedProducts,
        cart = currentCart,
        isBottomSheetOpen = false,
        selectedProduct = null
      )
    }
  }

  private fun confirmCheckout() {
    val shippingMethod = _uiState.value.shippingMethod
    val cart = _uiState.value.cart

    scope.launch {
      val orderIdResult = repository.submitOrder(shippingMethod.label, cart).getOrDefault("ORD-9999")
      _uiState.update {
        it.copy(
          step = StorefrontStep.SUCCESS,
          orderId = orderIdResult,
          cart = emptyList(),
          shippingMethod = ShippingMethod.STANDARD
        )
      }
    }
  }

  private fun resetFlow() {
    _uiState.update {
      it.copy(
        step = StorefrontStep.FEED,
        orderId = null,
        shippingMethod = ShippingMethod.STANDARD
      )
    }
  }
}
