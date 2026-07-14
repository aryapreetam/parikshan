package io.github.aryapreetam.parikshan

/**
 * Marks API surfaces that are internal to the Parikshan multi-module project.
 * These are required to be public due to cross-module references (e.g. between parikshan-core,
 * parikshan-client, and parikshan-server), but are not intended for direct usage by test authors.
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.ERROR,
  message = "This is an internal Parikshan API. It is not intended for public use and may change without notice."
)
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.TYPEALIAS,
  AnnotationTarget.CONSTRUCTOR
)
annotation class InternalParikshanApi
