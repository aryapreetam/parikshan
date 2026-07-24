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
      is StorefrontUserAction.AddSignaturePoint -> {
        _uiState.update { it.copy(signaturePoints = it.signaturePoints + action.point) }
      }
      is StorefrontUserAction.ClearSignature -> {
        _uiState.update { it.copy(signaturePoints = emptyList()) }
      }
      is StorefrontUserAction.ConfirmCheckout -> confirmCheckout()
      is StorefrontUserAction.ResetFlow -> resetFlow()
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
    val currentCart = _uiState.value.cart.toMutableList()
    val giftWrap = _uiState.value.isGiftWrapped
    val existingIndex = currentCart.indexOfFirst { it.product.id == product.id && it.giftWrap == giftWrap }
    if (existingIndex >= 0) {
      val item = currentCart[existingIndex]
      currentCart[existingIndex] = item.copy(quantity = item.quantity + 1)
    } else {
      currentCart.add(CartItem(product = product, quantity = 1, giftWrap = giftWrap))
    }
    _uiState.update {
      it.copy(
        cart = currentCart,
        isBottomSheetOpen = false,
        selectedProduct = null
      )
    }
  }

  private fun confirmCheckout() {
    val points = _uiState.value.signaturePoints
    val signatureStr = if (points.isNotEmpty()) "SVG_PATH_${points.size}_NODES" else "CHECKOUT_SIGNED"
    val cart = _uiState.value.cart

    scope.launch {
      val orderIdResult = repository.submitOrder(signatureStr, cart).getOrDefault("ORD-9999")
      _uiState.update {
        it.copy(
          step = StorefrontStep.SUCCESS,
          orderId = orderIdResult,
          cart = emptyList(),
          signaturePoints = emptyList()
        )
      }
    }
  }

  private fun resetFlow() {
    _uiState.update {
      it.copy(
        step = StorefrontStep.FEED,
        orderId = null,
        signaturePoints = emptyList()
      )
    }
  }
}
