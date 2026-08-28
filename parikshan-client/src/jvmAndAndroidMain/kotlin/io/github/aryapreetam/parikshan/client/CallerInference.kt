package io.github.aryapreetam.parikshan.client

internal fun inferCallerClassName(): String {
  val stack = Throwable().stackTrace

  for (element in stack) {
    val className = element.className
    if (!className.startsWith("io.github.aryapreetam.parikshan.") &&
      !className.startsWith("kotlin.") &&
      !className.startsWith("kotlinx.coroutines.") &&
      !className.startsWith("org.junit.") &&
      !className.startsWith("org.gradle.") &&
      !className.startsWith("worker.") &&
      !className.startsWith("sun.reflect.") &&
      !className.startsWith("java.")
    ) {
      return className.substringAfterLast('.')
    }
  }

  return "unknown_test"
}

internal fun inferCallerMethodName(): String {
  val stack = Throwable().stackTrace

  for (element in stack) {
    val className = element.className
    if (!className.startsWith("io.github.aryapreetam.parikshan.") &&
      !className.startsWith("kotlin.") &&
      !className.startsWith("kotlinx.coroutines.") &&
      !className.startsWith("org.junit.") &&
      !className.startsWith("org.gradle.") &&
      !className.startsWith("worker.") &&
      !className.startsWith("sun.reflect.") &&
      !className.startsWith("java.")
    ) {
      return element.methodName
    }
  }

  return "unknown_method"
}
