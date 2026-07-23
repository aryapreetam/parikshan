package io.github.aryapreetam.parikshan

import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/**
 * @suppress
 */
class ParikshanRunListener : RunListener() {
  private var lastClass: Class<*>? = null

  override fun testStarted(description: Description) {
    val currentClass = description.testClass
    if (currentClass != lastClass) {
      if (lastClass != null) {
        runAfterAll(lastClass!!)
      }
      lastClass = currentClass
      runBeforeAll(currentClass)
    }
  }

  override fun testFinished(description: Description) {
    // No-op
  }

  override fun testRunFinished(result: org.junit.runner.Result) {
    val last = lastClass
    if (last != null) {
      runAfterAll(last)
      lastClass = null
    }
  }

  private fun runBeforeAll(clazz: Class<*>) {
    // 1. Companion object
    runCatching {
      val companionField = clazz.fields.firstOrNull { it.name == "Companion" }
      if (companionField != null) {
        val companionInstance = companionField.get(null)
        companionInstance.javaClass.declaredMethods.forEach { method ->
          if (method.isAnnotationPresent(BeforeAll::class.java)) {
            method.isAccessible = true
            method.invoke(companionInstance)
          }
        }
      }
    }
    // 2. Static methods
    runCatching {
      clazz.declaredMethods.forEach { method ->
        if (java.lang.reflect.Modifier.isStatic(method.modifiers) && method.isAnnotationPresent(BeforeAll::class.java)) {
          method.isAccessible = true
          method.invoke(null)
        }
      }
    }
  }

  private fun runAfterAll(clazz: Class<*>) {
    // 1. Companion object
    runCatching {
      val companionField = clazz.fields.firstOrNull { it.name == "Companion" }
      if (companionField != null) {
        val companionInstance = companionField.get(null)
        companionInstance.javaClass.declaredMethods.forEach { method ->
          if (method.isAnnotationPresent(AfterAll::class.java)) {
            method.isAccessible = true
            method.invoke(companionInstance)
          }
        }
      }
    }
    // 2. Static methods
    runCatching {
      clazz.declaredMethods.forEach { method ->
        if (java.lang.reflect.Modifier.isStatic(method.modifiers) && method.isAnnotationPresent(AfterAll::class.java)) {
          method.isAccessible = true
          method.invoke(null)
        }
      }
    }
  }
}
