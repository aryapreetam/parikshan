package sample.app

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.PickerNotOpenedException
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.isDesktop
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FilePickerIntegrationTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    navigateToSection("nav_file_picker_playground")
    assertVisible("file_picker_playground")
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }

  @Test
  fun testSingleFileSelection() = e2eTest {
    withFilePicker("build.gradle.kts") {
      click("choose_single_file_button")
    }
    assertText("picker_action_status", "File selected")
    assertText("selected_count_text", "1")
    assertContains("selected_file_status", "build.gradle.kts")
  }

  @Test
  fun testMultipleFilesSelection() = e2eTest {
    withFilePicker("build.gradle.kts", "src/commonMain/kotlin/sample/app/App.kt") {
      click("choose_multiple_files_button")
    }
    assertText("picker_action_status", "2 files selected")
    assertText("selected_count_text", "2")
    assertContains("selected_file_status", "build.gradle.kts")
    assertContains("selected_file_status", "App.kt")
  }

  @Test
  fun testUserHomeExpansion() = e2eTest {
    if (isDesktop()) {
      withFilePicker("~") {
        click("choose_directory_button")
      }
      assertText("picker_action_status", "Directory selected")
      assertText("selected_count_text", "1")
    }
  }

  @Test
  fun testDirectorySelection() = e2eTest {
    withFilePicker("src/commonMain") {
      click("choose_directory_button")
    }
    assertText("picker_action_status", "Directory selected")
    assertText("selected_count_text", "1")
    assertContains("selected_file_status", "commonMain")
  }

  @Test
  fun testFilePickerCancellation() = e2eTest {
    withFilePickerCancel {
      click("choose_single_file_button")
    }
    assertText("picker_action_status", "Picker was cancelled")
    assertText("selected_count_text", "0")
  }

  @Test
  fun testNonExistentFileFailsFast() = e2eTest {
    assertFailsWith<Throwable> {
      withFilePicker("this_file_does_not_exist_at_all.nonexistent") {
        click("choose_single_file_button")
      }
    }
  }

  @Test
  fun testPickerNotOpenedFailsFast() = e2eTest {
    assertFailsWith<PickerNotOpenedException> {
      withFilePicker("build.gradle.kts", timeoutMs = 1500L) {
        // Deliberately do not trigger the picker
      }
    }
  }
  @Test
  fun testFilePickerEndToEndFlow() = e2eTest {
    // 1. Single file selection
    withFilePicker("build.gradle.kts") {
      click("choose_single_file_button")
    }
    assertText("picker_action_status", "File selected")
    assertText("selected_count_text", "1")
    // 2. Directory selection
    withFilePicker("src/commonMain") {
      click("choose_directory_button")
    }
    assertText("picker_action_status", "Directory selected")
    assertText("selected_count_text", "1")
    // 3. Multiple file selection
    withFilePicker("build.gradle.kts", "src/commonMain/kotlin/sample/app/App.kt") {
      click("choose_multiple_files_button")
    }
    assertText("picker_action_status", "2 files selected")
    assertText("selected_count_text", "2")
    // 4. Cancellation
    withFilePickerCancel {
      click("choose_single_file_button")
    }
    assertText("picker_action_status", "Picker was cancelled")
    assertText("selected_count_text", "0")
  }
}
