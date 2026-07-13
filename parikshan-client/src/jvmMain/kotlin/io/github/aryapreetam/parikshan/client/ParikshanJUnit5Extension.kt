package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.BeforeAll
import io.github.aryapreetam.parikshan.AfterAll
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.lang.reflect.Modifier

class ParikshanJUnit5Extension : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    val testClass = context.requiredTestClass
    
    // 1. Companion Object lifecycle methods
    runCatching {
      val companionField = testClass.fields.firstOrNull { it.name == "Companion" }
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

    // 2. Static methods directly on the class
    runCatching {
      testClass.declaredMethods.forEach { method ->
        if (Modifier.isStatic(method.modifiers) && method.isAnnotationPresent(BeforeAll::class.java)) {
          method.isAccessible = true
          method.invoke(null)
        }
      }
    }
  }

  override fun afterAll(context: ExtensionContext) {
    val testClass = context.requiredTestClass
    
    // 1. Companion Object lifecycle methods
    runCatching {
      val companionField = testClass.fields.firstOrNull { it.name == "Companion" }
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

    // 2. Static methods directly on the class
    runCatching {
      testClass.declaredMethods.forEach { method ->
        if (Modifier.isStatic(method.modifiers) && method.isAnnotationPresent(AfterAll::class.java)) {
          method.isAccessible = true
          method.invoke(null)
        }
      }
    }
  }
}
