package org.example.project.storefront

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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

        if (uiState.isBottomSheetOpen && uiState.selectedProduct != null) {
          ProductDetailsBottomSheet(
            product = uiState.selectedProduct!!,
            isGiftWrapped = uiState.isGiftWrapped,
            onToggleGiftWrap = { viewModel.onAction(StorefrontUserAction.ToggleGiftWrap(it)) },
            onAddToCart = { viewModel.onAction(StorefrontUserAction.AddToCart(uiState.selectedProduct!!)) },
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
      Column {
        Text("Storefront Catalog", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Logged in as ${uiState.userEmail ?: "Guest"}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
      }

      val itemCount = uiState.cart.sumOf { it.quantity }
      if (itemCount > 0) {
        Button(
          onClick = { onAction(StorefrontUserAction.ProceedToCheckout) },
          modifier = Modifier.testTag("checkout_button")
        ) {
          Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Checkout ($itemCount)", fontWeight = FontWeight.Bold)
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

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
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
          Text(text = product.category, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
      }

      Text(text = "$${product.price}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onViewDetails,
          modifier = Modifier.weight(1f).testTag("view_details_button_${product.id}")
        ) {
          Text("View Details")
        }

        Button(
          onClick = onAddToCart,
          modifier = Modifier.weight(1f).testTag("add_to_cart_button_${product.id}")
        ) {
          Text("Add to Cart")
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
        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("details_add_to_cart_button")
      ) {
        Text("Add to Cart", fontWeight = FontWeight.Bold)
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
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(scrollState)
      .testTag("storefront_checkout_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text("Checkout & Delivery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Order Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        uiState.cart.forEach { item ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${item.product.title} (x${item.quantity})", style = MaterialTheme.typography.bodyMedium)
            Text("$${item.product.price * item.quantity}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
          }
          if (item.giftWrap) {
            Text("  + Gift Wrapped ($5.00)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
          }
        }
        HorizontalDivider()
        val total = uiState.cart.sumOf { (it.product.price + if (it.giftWrap) 5.0 else 0.0) * it.quantity }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("$${total}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    Text("Draw Delivery Signature Below", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(130.dp)
        .background(Color(0xFFF0F0F0), shape = RoundedCornerShape(8.dp))
        .testTag("storefront_signature_canvas")
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            onAction(StorefrontUserAction.AddSignaturePoint(change.position))
          }
        }
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        if (uiState.signaturePoints.size > 1) {
          val path = Path().apply {
            moveTo(uiState.signaturePoints.first().x, uiState.signaturePoints.first().y)
            for (i in 1 until uiState.signaturePoints.size) {
              lineTo(uiState.signaturePoints[i].x, uiState.signaturePoints[i].y)
            }
          }
          drawPath(path = path, color = Color.Black, style = Stroke(width = 4f))
        }
      }
      if (uiState.signaturePoints.isEmpty()) {
        Text(
          "Sign here with touch or mouse...",
          color = Color.Gray,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.align(Alignment.Center)
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      TextButton(onClick = { onAction(StorefrontUserAction.ClearSignature) }) {
        Text("Clear Signature")
      }

      Button(
        onClick = { onAction(StorefrontUserAction.ConfirmCheckout) },
        modifier = Modifier.testTag("complete_order_button")
      ) {
        Text("Complete Order", fontWeight = FontWeight.Bold)
      }
    }
  }
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
