package io.github.aryapreetam.parikshan.gradle

internal object E2EFilterMatcher {
  /**
   * Matches a class name against a tests filter pattern.
   *
   * Supported patterns:
   * 1. Exact class match: "sample.app.SelectorScenarios"
   * 2. Simple class name match: "SelectorScenarios"
   * 3. Package/Wildcard match: "sample.app.*" or "*Scenarios"
   * 4. Class + Method match: "sample.app.SelectorScenarios.testTaskList"
   * 5. Simple class + Method match: "SelectorScenarios.testTaskList"
   */
  fun isClassMatched(clazz: String, pattern: String): Boolean {
    if (pattern.isBlank()) return true
    
    val trimmedPattern = pattern.trim()
    val cleanPattern = trimmedPattern.replace("*", ".*").replace("?", ".?")
    
    // 1. Direct match
    if (clazz.equals(trimmedPattern, ignoreCase = true)) {
      return true
    }
    
    // 2. Wildcard regex match (matches package filters like "sample.app.*")
    try {
      val regex = Regex("^$cleanPattern$", RegexOption.IGNORE_CASE)
      if (regex.matches(clazz)) {
        return true
      }
    } catch (_: Exception) {
      // Fallback if regex is invalid
    }
    
    // 3. Simple class name match (e.g. pattern is "SelectorScenarios")
    val simpleClassName = clazz.substringAfterLast('.')
    if (simpleClassName.equals(trimmedPattern, ignoreCase = true)) {
      return true
    }
    
    // 4. Exact Class + Method match (e.g. pattern is "sample.app.SelectorScenarios.testTaskList")
    if (trimmedPattern.startsWith("$clazz.", ignoreCase = true)) {
      return true
    }
    
    // 5. Simple Class + Method match (e.g. pattern is "SelectorScenarios.testTaskList")
    if (trimmedPattern.startsWith("$simpleClassName.", ignoreCase = true)) {
      return true
    }
    
    // 6. Substring match as a safe fallback
    if (clazz.contains(trimmedPattern, ignoreCase = true)) {
      return true
    }
    
    return false
  }
}
