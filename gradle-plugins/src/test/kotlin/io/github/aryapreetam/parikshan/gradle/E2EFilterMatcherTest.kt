package io.github.aryapreetam.parikshan.gradle

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class E2EFilterMatcherTest {

  private val clazz = "sample.app.SelectorScenarios"

  @Test
  fun testBlankPatternMatchesEverything() {
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, ""))
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "   "))
  }

  @Test
  fun testExactClassMatch() {
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "sample.app.SelectorScenarios"))
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "SAMPLE.APP.SELECTORSCENARIOS"))
    assertFalse(E2EFilterMatcher.isClassMatched(clazz, "sample.app.FormIntegrationTest"))
  }

  @Test
  fun testSimpleClassNameMatch() {
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "SelectorScenarios"))
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "selectorscenarios"))
    assertFalse(E2EFilterMatcher.isClassMatched(clazz, "FormIntegrationTest"))
  }

  @Test
  fun testPackageWildcardMatch() {
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "sample.app.*"))
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "sample.*.SelectorScenarios"))
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "*.SelectorScenarios"))
    assertFalse(E2EFilterMatcher.isClassMatched(clazz, "sample.other.*"))
  }

  @Test
  fun testClassAndMethodMatch() {
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "sample.app.SelectorScenarios.testTaskList"))
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "sample.app.SelectorScenarios.testScrollAndTree"))
    assertFalse(E2EFilterMatcher.isClassMatched(clazz, "sample.app.FormIntegrationTest.testSubmit"))
  }

  @Test
  fun testSimpleClassAndMethodMatch() {
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "SelectorScenarios.testTaskList"))
    assertFalse(E2EFilterMatcher.isClassMatched(clazz, "FormIntegrationTest.testSubmit"))
  }

  @Test
  fun testSubstringMatchFallback() {
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "Selector"))
    assertTrue(E2EFilterMatcher.isClassMatched(clazz, "Scenarios"))
  }
}
