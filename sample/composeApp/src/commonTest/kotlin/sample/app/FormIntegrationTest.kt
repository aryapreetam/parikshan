package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class FormIntegrationTest {

  @Test
  fun testStatefulFormValidationAndSubmission() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_form_playground")
    assertVisible("form_playground_screen")

    // Input Name
    input("form_name_input", "Alex Smith")

    // Input Invalid Email & Assert Error
    input("form_email_input", "alexsmith")
    assertVisible("Invalid email address")

    // Input Valid Email & Assert Error Cleared
    input("form_email_input", "alex.smith@example.com")
    assertNotVisible("Invalid email address")

    // Input Short Password & Assert Error
    input("form_password_input", "123")
    assertVisible("Password too short")

    // Complete Valid Password
    input("form_password_input", "mypassword123")
    assertNotVisible("Password too short")

    // Input Mismatched Confirm Password & Assert Error
    input("form_confirm_password_input", "mypassword456")
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
    assertVisible("Indic Value: नमस्ते")

    // Accept Terms & Conditions Checkbox
    scrollUntilVisible(Selector.Tag("form_playground_screen"), Selector.Tag("form_agree_row"))
    click("form_agree_row")

    // Submit the Form
    click("form_submit_button")
  }
}
