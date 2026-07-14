package io.github.aryapreetam.parikshan

@kotlin.annotation.Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BeforeAll

@kotlin.annotation.Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AfterAll

@kotlin.annotation.Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Order(val value: Int)
