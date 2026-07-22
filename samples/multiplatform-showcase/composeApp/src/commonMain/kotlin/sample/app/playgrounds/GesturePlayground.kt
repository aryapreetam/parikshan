package sample.app.playgrounds

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GesturePlayground() {
  val coroutineScope = rememberCoroutineScope()
  var gestureResultMsg by remember { mutableStateOf("") }
  
  // Swipe Dismiss State
  var swipeItemDismissed by remember { mutableStateOf(false) }
  var swipeOffsetX by remember { mutableStateOf(0f) }

  // Drag and Drop Parent State
  var isCardInBoxB by remember { mutableStateOf(false) }
  var dragCardOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

  // Pull-To-Refresh State
  var pullToRefreshLoading by remember { mutableStateOf(false) }
  var pullDistance by remember { mutableStateOf(0f) }

  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(scrollState)
      .testTag("gesture_playground_screen"),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    Text("Rich Gestures Playground", style = MaterialTheme.typography.headlineMedium)
    
    // Results Banner
    if (gestureResultMsg.isNotEmpty()) {
      Text(
        text = gestureResultMsg,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("gesture_result_text")
      )
    }

    // 1. Swipe to Dismiss Scenario
    Card(modifier = Modifier.fillMaxWidth().testTag("swipe_container_card")) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("1. Swipe to Dismiss", style = MaterialTheme.typography.titleMedium)
        if (!swipeItemDismissed) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(60.dp)
              .background(Color.LightGray)
              .pointerInput(Unit) {
                detectHorizontalDragGestures(
                  onDragEnd = {
                    if (swipeOffsetX > 200f) {
                      swipeItemDismissed = true
                      gestureResultMsg = "Item Swiped & Dismissed"
                    } else {
                      swipeOffsetX = 0f
                    }
                  },
                  onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    swipeOffsetX = (swipeOffsetX + dragAmount).coerceAtLeast(0f)
                  }
                )
              }
              .testTag("swipe_target_row")
          ) {
            Box(
              modifier = Modifier
                .offset { IntOffset(swipeOffsetX.roundToInt(), 0) }
                .fillMaxHeight()
                .fillMaxWidth(0.8f)
                .background(Color.Blue)
                .padding(16.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Text("Swipe Right to Delete", color = Color.White)
            }
          }
        } else {
          Text("Swipe Item Deleted.", style = MaterialTheme.typography.bodyMedium)
          Button(onClick = { swipeItemDismissed = false; swipeOffsetX = 0f; gestureResultMsg = "" }) {
            Text("Reset Swipe Item")
          }
        }
      }
    }

    // 2. Drag & Drop Relocation Scenario
    Card(modifier = Modifier.fillMaxWidth().testTag("drag_drop_container")) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("2. Drag & Drop", style = MaterialTheme.typography.titleMedium)
        Text("Drag the Red Card to move it from Box A to Box B:")
        
        Row(
          modifier = Modifier.fillMaxWidth().height(120.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Box A
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .background(if (!isCardInBoxB) Color(0xFFE8F5E9) else Color(0xFFEEEEEE))
              .testTag("drag_source_box_a"),
            contentAlignment = Alignment.Center
          ) {
            Text("Box A")
            if (!isCardInBoxB) {
              Box(
                modifier = Modifier
                  .offset { IntOffset(dragCardOffset.x.roundToInt(), dragCardOffset.y.roundToInt()) }
                  .size(60.dp)
                  .background(Color.Red)
                  .pointerInput(Unit) {
                    detectDragGestures(
                      onDragEnd = {
                        if (dragCardOffset.x > 150f) {
                          isCardInBoxB = true
                          gestureResultMsg = "Card Dropped in Box B"
                        }
                        dragCardOffset = androidx.compose.ui.geometry.Offset.Zero
                      },
                      onDrag = { change, dragAmount ->
                        change.consume()
                        dragCardOffset += dragAmount
                      }
                    )
                  }
                  .testTag("draggable_red_card"),
                contentAlignment = Alignment.Center
              ) {
                Text("Red Card", color = Color.White, style = MaterialTheme.typography.bodySmall)
              }
            }
          }

          // Box B
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .background(if (isCardInBoxB) Color(0xFFE8F5E9) else Color(0xFFEEEEEE))
              .testTag("drag_target_box_b"),
            contentAlignment = Alignment.Center
          ) {
            Text("Box B")
            if (isCardInBoxB) {
              Box(
                modifier = Modifier
                  .size(60.dp)
                  .background(Color.Green)
                  .testTag("dropped_green_card"),
                contentAlignment = Alignment.Center
              ) {
                Text("Dropped", color = Color.White, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
        if (isCardInBoxB) {
          Button(onClick = { isCardInBoxB = false; gestureResultMsg = "" }) {
            Text("Reset Drag & Drop")
          }
        }
      }
    }


    // 4. Multi-tap Gestures (Long Press & Double Tap)
    Card(modifier = Modifier.fillMaxWidth().testTag("taps_container_card")) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("4. Click, Double Tap & Long Press", style = MaterialTheme.typography.titleMedium)
        
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.Yellow)
            .combinedClickable(
              onClick = { gestureResultMsg = "Single Tapped Yellow Area" },
              onDoubleClick = { gestureResultMsg = "Double Tapped Yellow Area" },
              onLongClick = { gestureResultMsg = "Long Pressed Yellow Area" }
            )
            .testTag("multi_tap_surface"),
          contentAlignment = Alignment.Center
        ) {
          Text("Tap, Double Tap, or Long Press here", color = Color.Black)
        }
      }
    }
  }
}
