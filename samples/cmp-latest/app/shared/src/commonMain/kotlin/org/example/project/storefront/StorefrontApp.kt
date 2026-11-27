package org.example.project.storefront

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorefrontAppScreen(
  viewModel: StorefrontViewModel = remember { StorefrontViewModel() },
  onBackToHub: (() -> Unit)? = null
) {
  val uiState by viewModel.uiState.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Storefront App", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          if (onBackToHub != null) {
            IconButton(
              onClick = onBackToHub,
              modifier = Modifier.testTag("storefront_top_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Hub")
            }
          }
        }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("storefront_app_container"),
      contentAlignment = Alignment.TopCenter
    ) {
      Box(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
        when (uiState.step) {
          StorefrontStep.AUTH -> AuthScreen(
            uiState = uiState,
            onAction = viewModel::onAction
          )
          StorefrontStep.FEED -> ProductFeedScreen(
            uiState = uiState,
            onAction = viewModel::onAction
          )
          StorefrontStep.CHECKOUT -> CheckoutScreen(
            uiState = uiState,
            onAction = viewModel::onAction
          )
          StorefrontStep.SUCCESS -> SuccessScreen(
            uiState = uiState,
            onAction = viewModel::onAction
          )
        }

        val selected = uiState.selectedProduct
        if (uiState.isBottomSheetOpen && selected != null) {
          val liveProduct = uiState.products.find { it.id == selected.id } ?: selected
          ProductDetailsBottomSheet(
            product = liveProduct,
            isGiftWrapped = uiState.isGiftWrapped,
            onToggleGiftWrap = { viewModel.onAction(StorefrontUserAction.ToggleGiftWrap(it)) },
            onAddToCart = { viewModel.onAction(StorefrontUserAction.AddToCart(liveProduct)) },
            onDismiss = { viewModel.onAction(StorefrontUserAction.CloseBottomSheet) }
          )
        }
      }
    }
  }
}

@Composable
private fun AuthScreen(
  uiState: StorefrontUiState,
  onAction: (StorefrontUserAction) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp)
      .testTag("storefront_auth_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = Color(0xFF6750A4),
      modifier = Modifier.size(64.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.ShoppingCart,
          contentDescription = "Logo",
          tint = Color.White,
          modifier = Modifier.size(36.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text("Customer Sign In", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("Enter your credentials to browse catalog", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
      value = uiState.email,
      onValueChange = { onAction(StorefrontUserAction.UpdateEmail(it)) },
      label = { Text("Email Address") },
      isError = uiState.emailError != null,
      supportingText = uiState.emailError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("storefront_email_input")
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
      value = uiState.password,
      onValueChange = { onAction(StorefrontUserAction.UpdatePassword(it)) },
      label = { Text("Password") },
      isError = uiState.passwordError != null,
      supportingText = uiState.passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("storefront_password_input")
    )

    Spacer(modifier = Modifier.height(20.dp))

    Button(
      onClick = { onAction(StorefrontUserAction.SubmitLogin) },
      enabled = !uiState.isLoading,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("storefront_login_button")
    ) {
      if (uiState.isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
      } else {
        Text("Sign In", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun ProductFeedScreen(
  uiState: StorefrontUiState,
  onAction: (StorefrontUserAction) -> Unit
) {
  val itemCount = uiState.cart.sumOf { it.quantity }
  val cartSubtotal = uiState.cart.sumOf { (it.product.price + if (it.giftWrap) 5.0 else 0.0) * it.quantity }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .testTag("storefront_feed_screen")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(
          text = "Storefront Catalog",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "Logged in as ${uiState.userEmail ?: "Guest"}",
          style = MaterialTheme.typography.labelMedium,
          color = Color.Gray,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { onAction(StorefrontUserAction.RefreshCatalog) },
          modifier = Modifier.testTag("refresh_catalog_button")
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh Catalog")
        }

        TextButton(
          onClick = { onAction(StorefrontUserAction.SignOut) },
          modifier = Modifier.testTag("storefront_sign_out_button")
        ) {
          Text("Sign Out", maxLines = 1, softWrap = false)
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.weight(1f).testTag("product_lazy_column")
    ) {
      items(uiState.products, key = { it.id }) { product ->
        ProductCard(
          product = product,
          onViewDetails = { onAction(StorefrontUserAction.SelectProduct(product)) },
          onAddToCart = { onAction(StorefrontUserAction.AddToCart(product)) }
        )
      }
    }

    if (itemCount > 0) {
      Spacer(modifier = Modifier.height(12.dp))
      Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("checkout_summary_bar")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (itemCount == 1) "1 item in cart" else "$itemCount items in cart",
              style = MaterialTheme.typography.labelMedium,
              color = Color.Gray,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = formatUsd(cartSubtotal),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Button(
            onClick = { onAction(StorefrontUserAction.ProceedToCheckout) },
            modifier = Modifier.testTag("checkout_button")
          ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Checkout ($itemCount)", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
          }
        }
      }
    }
  }
}

@Composable
private fun ProductCard(
  product: Product,
  onViewDetails: () -> Unit,
  onAddToCart: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth().testTag("product_card_${product.id}")
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = product.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
          Text(text = product.category, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = formatUsd(product.price), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        if (product.stock <= 0) {
          Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
            Text(text = "Out of Stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("out_of_stock_badge_${product.id}"))
          }
        } else {
          Text(text = "Stock: ${product.stock}", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.testTag("stock_text_${product.id}"))
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onViewDetails,
          modifier = Modifier.weight(1f).testTag("view_details_button_${product.id}")
        ) {
          Text("View Details", maxLines = 1, softWrap = false)
        }

        Button(
          onClick = onAddToCart,
          enabled = product.stock > 0,
          modifier = Modifier.weight(1f).testTag("add_to_cart_button_${product.id}")
        ) {
          Text(if (product.stock > 0) "Add to Cart" else "Out of Stock", maxLines = 1, softWrap = false)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetailsBottomSheet(
  product: Product,
  isGiftWrapped: Boolean,
  onToggleGiftWrap: (Boolean) -> Unit,
  onAddToCart: () -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    modifier = Modifier.testTag("product_details_bottom_sheet")
  ) {
    Column(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = "Product Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      Text(text = product.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(text = product.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        product.tags.forEach { tag ->
          Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE0E0E0)) {
            Text(text = tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Gift Wrap (+$5.00)", style = MaterialTheme.typography.bodyMedium)
        Switch(
          checked = isGiftWrapped,
          onCheckedChange = onToggleGiftWrap,
          modifier = Modifier.testTag("gift_wrap_switch")
        )
      }

      Button(
        onClick = onAddToCart,
        enabled = product.stock > 0,
        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("details_add_to_cart_button")
      ) {
        Text(if (product.stock > 0) "Add to Cart" else "Out of Stock", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun CheckoutScreen(
  uiState: StorefrontUiState,
  onAction: (StorefrontUserAction) -> Unit
) {
  val scrollState = rememberScrollState()
  val subtotal = uiState.cart.sumOf { (it.product.price + if (it.giftWrap) 5.0 else 0.0) * it.quantity }
  val total = subtotal + uiState.shippingMethod.fee

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(scrollState)
      .testTag("storefront_checkout_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Checkout & Delivery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Order Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        uiState.cart.forEach { item ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${item.product.title} (x${item.quantity})", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(formatUsd(item.product.price * item.quantity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          }
          if (item.giftWrap) {
            Text("  + Gift Wrapped ($5.00)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(uiState.shippingMethod.label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
          Text(
            text = if (uiState.shippingMethod.fee == 0.0) "Free" else formatUsd(uiState.shippingMethod.fee),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("checkout_shipping_fee")
          )
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            text = formatUsd(total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("checkout_total_amount")
          )
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text("Delivery Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      }

      ShippingMethod.entries.forEach { method ->
        val selected = uiState.shippingMethod == method
        val optionTag = if (method == ShippingMethod.STANDARD) "shipping_option_standard" else "shipping_option_express"
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
          border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag(optionTag)
            .clickable { onAction(StorefrontUserAction.SelectShippingMethod(method)) }
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(
              selected = selected,
              onClick = { onAction(StorefrontUserAction.SelectShippingMethod(method)) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(method.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
              Text(method.eta, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(
              text = if (method.fee == 0.0) "Free" else "+${formatUsd(method.fee)}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth().testTag("storefront_payment_card")
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Icon(Icons.Default.CreditCard, contentDescription = "Payment Card", tint = MaterialTheme.colorScheme.primary)
          Column {
            Text("Payment Method", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text("Visa ending in 4242", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
          }
        }
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
          Text(
            text = "Default",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    }

    Button(
      onClick = { onAction(StorefrontUserAction.ConfirmCheckout) },
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("complete_order_button")
    ) {
      Text("Place Order • ${formatUsd(total)}", fontWeight = FontWeight.Bold)
    }
  }
}

private fun formatUsd(amount: Double): String {
  val totalCents = (amount * 100.0).roundToLong()
  val dollars = totalCents / 100
  val cents = kotlin.math.abs(totalCents % 100).toString().padStart(2, '0')
  return "$$dollars.$cents"
}

@Composable
private fun SuccessScreen(
  uiState: StorefrontUiState,
  onAction: (StorefrontUserAction) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp)
      .testTag("storefront_success_screen"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = Icons.Default.CheckCircle,
      contentDescription = "Success",
      tint = Color(0xFF2E7D32),
      modifier = Modifier.size(72.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text("Order Confirmed!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("Thank you for your purchase", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Order Reference: ${uiState.orderId ?: "ORD-1001"}",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = { onAction(StorefrontUserAction.ResetFlow) },
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("back_to_products_button")
    ) {
      Text("Back to Products", fontWeight = FontWeight.Bold)
    }
  }
}
