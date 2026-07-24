package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.isWasm
import io.github.aryapreetam.parikshan.isAndroid
import io.github.aryapreetam.parikshan.isIos
import sample.app.setup.dragSlider
import io.github.aryapreetam.parikshan.E2ETestLifecycle
import kotlin.test.Test
import kotlin.test.assertTrue

class FormIntegrationTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    navigateToSection("nav_form_playground")
    assertVisible("form_playground_screen")
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }

  @Test
  fun testStatefulFormValidationAndSubmission() = e2eTest {
    val name = "Alex Smith"
    // Input Name
    input("form_name_input", name)

    // Input Invalid Email & Assert Error
    input("form_email_input", "alexsmith")
    assertVisible("Invalid email address")

    // Input Valid Email & Assert Error Cleared
    input("form_email_input", "alex.smith@example.com")
    assertNotVisible("Invalid email address")

    // Input Phone Number
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_phone_input"))
    input("form_phone_input", "555-0199")

    // Input Shipping Address
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_address_input"))
    input("form_address_input", "123 Main St, Apt 4B")

    // Input Short Password & Assert Error
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_password_input"))
    input("form_password_input", "123")
    assertVisible("Password too short")

    // Complete Valid Password
    input("form_password_input", "mypassword123")
    assertNotVisible("Password too short")

    // Input Mismatched Confirm Password & Assert Error
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_category_tags"))
    input("form_confirm_password_input", "mypassword456")
    // Scroll to the error text itself to bring it into the viewport                                                                                                                                                  
    //scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Text("Passwords do not match"))
    assertVisible("Passwords do not match")
    

    // Correct Confirm Password
    input("form_confirm_password_input", "mypassword123")
    assertNotVisible("Password too short")
    assertNotVisible("Passwords do not match")

    // Scroll down to reveal the rest of the form
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("radio_Option B"))
    click("radio_Option B")

    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_switch"))
    click("form_switch")

    // Input Indic Text
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_indic_input"))
    input("form_indic_input", "नमस्ते")
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Text("Indic Value: नमस्ते"))
    assertVisible("Indic Value: नमस्ते")

    // Accept Terms & Conditions Checkbox
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_agree_row"))
    click("form_agree_row")
    
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Text("Submit"))

    // Submit the Form
    click("Submit")
    assertVisible("Successfully Submitted: $name")
  }

  @Test
  fun testSliderDrag() = e2eTest {
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_slider"))
    assertVisible("form_slider")
    
    // Drag slider to 80%
    dragSlider("form_slider", 0.8f)
    
    // Verify value is updated to a reasonable range (75% to 85%) due to physical gesture Jitter across platforms
    waitFor(Selector.Text("Range Selector:"))
    val labelNode = resolveVisibleNode(Selector.Text("Range Selector:"))
    val labelText = labelNode.text ?: ""
    val match = Regex("Range Selector: (\\d+)%").find(labelText)
    val percentage = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    assertTrue(
      percentage in 75..85,
      "Expected slider percentage to be between 75% and 85%, but got $percentage% (full text: '$labelText')"
    )
  }
}
