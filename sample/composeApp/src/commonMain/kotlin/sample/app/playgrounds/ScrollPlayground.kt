package sample.app.playgrounds

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class ScrollSubTab {
  LazyList,
  NestedScroll,
  GridLayout,
  Panning
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollPlayground() {
  var activeTab by remember { mutableStateOf(ScrollSubTab.LazyList) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .testTag("scroll_playground_screen"),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("Scrolling, Virtualization & Grids", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    
    // Tab Selectors
    ScrollPlaygroundTabs(activeTab = activeTab, onTabSelected = { activeTab = it })

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(Color(0xFFF9F9F9))
    ) {
      when (activeTab) {
        ScrollSubTab.LazyList -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("lazy_column_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Create 10 sections of 10 items each with sticky headers
            repeat(10) { section ->
              stickyHeader {
                Text(
                  text = "Section Heading ${section + 1}",
                  style = MaterialTheme.typography.titleMedium,
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE3F2FD))
                    .padding(8.dp)
                    .testTag("sticky_header_$section")
                )
              }
              items(10) { item ->
                val overallIndex = section * 10 + item + 1
                Card(
                  modifier = Modifier.fillMaxWidth().testTag("lazy_item_$overallIndex")
                ) {
                  Text(
                    "Virtual Item #$overallIndex",
                    modifier = Modifier.padding(16.dp)
                  )
                }
              }
            }
          }
        }
        
        ScrollSubTab.NestedScroll -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("nested_scroll_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            items(15) { rowIndex ->
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  "Horizontal Row #${rowIndex + 1}",
                  style = MaterialTheme.typography.titleSmall,
                  modifier = Modifier.testTag("nested_row_title_$rowIndex")
                )
                LazyRow(
                  modifier = Modifier.fillMaxWidth().testTag("carousel_row_$rowIndex"),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  items(20) { colIndex ->
                    Card(
                      modifier = Modifier
                        .size(120.dp, 80.dp)
                        .testTag("carousel_cell_${rowIndex}_${colIndex}"),
                      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                      Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                      ) {
                        Text("Cell $colIndex", style = MaterialTheme.typography.bodyMedium)
                      }
                    }
                  }
                }
              }
            }
          }
        }

        ScrollSubTab.GridLayout -> {
          LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().testTag("lazy_grid_container"),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(60) { index ->
              Card(
                modifier = Modifier
                  .aspectRatio(1f)
                  .testTag("grid_cell_$index"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
              ) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier.fillMaxSize()
                ) {
                  Text("Grid $index", style = MaterialTheme.typography.bodySmall)
                }
              }
            }
          }
        }

        ScrollSubTab.Panning -> {
          PanningCanvas()
        }
      }
    }
  }
}

@Composable
private fun ScrollPlaygroundTabs(
  activeTab: ScrollSubTab,
  onTabSelected: (ScrollSubTab) -> Unit
) {
  ScrollableTabRow(
    selectedTabIndex = activeTab.ordinal,
    modifier = Modifier.fillMaxWidth().testTag("scroll_tab_row")
  ) {
    Tab(
      selected = activeTab == ScrollSubTab.LazyList,
      onClick = { onTabSelected(ScrollSubTab.LazyList) },
      text = { Text("Lazy List") },
      modifier = Modifier.testTag("tab_lazy_list")
    )
    Tab(
      selected = activeTab == ScrollSubTab.NestedScroll,
      onClick = { onTabSelected(ScrollSubTab.NestedScroll) },
      text = { Text("Nested Carousels") },
      modifier = Modifier.testTag("tab_nested_scroll")
    )
    Tab(
      selected = activeTab == ScrollSubTab.GridLayout,
      onClick = { onTabSelected(ScrollSubTab.GridLayout) },
      text = { Text("Grid Layout") },
      modifier = Modifier.testTag("tab_grid_layout")
    )
    Tab(
      selected = activeTab == ScrollSubTab.Panning,
      onClick = { onTabSelected(ScrollSubTab.Panning) },
      text = { Text("Panning Area") },
      modifier = Modifier.testTag("tab_panning")
    )
  }
}

@Composable
private fun PanningCanvas() {
  var dragOffset by remember { mutableStateOf(Offset(0f, 0f)) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          dragOffset += dragAmount
        }
      }
      .testTag("panning_drag_surface"),
    contentAlignment = Alignment.Center
  ) {
    // Background Grid Pattern
    Text(
      text = "Drag Anywhere to Move Target",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
    )

    // Target Box that moves
    Box(
      modifier = Modifier
        .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
        .size(100.dp)
        .background(Color.Red)
        .testTag("panning_target_node"),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "X: ${dragOffset.x.toInt()}\nY: ${dragOffset.y.toInt()}",
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("panning_coords_text")
      )
    }
  }
}
