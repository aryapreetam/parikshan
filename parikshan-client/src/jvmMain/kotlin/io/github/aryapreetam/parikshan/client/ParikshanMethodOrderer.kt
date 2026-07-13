package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.Order
import org.junit.jupiter.api.MethodDescriptor
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.MethodOrdererContext
import java.util.Comparator

class ParikshanMethodOrderer : MethodOrderer {
  override fun orderMethods(context: MethodOrdererContext) {
    context.methodDescriptors.sortWith(
      Comparator { m1, m2 ->
        val o1 = m1.method.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
        val o2 = m2.method.getAnnotation(Order::class.java)?.value ?: Int.MAX_VALUE
        o1.compareTo(o2)
      }
    )
  }
}
