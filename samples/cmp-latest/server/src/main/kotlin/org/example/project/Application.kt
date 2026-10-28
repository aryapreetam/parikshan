package org.example.project

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

val productInventory = ConcurrentHashMap<String, AtomicInteger>().apply {
  put("p1", AtomicInteger(2))
  put("p2", AtomicInteger(5))
  put("p3", AtomicInteger(10))
}

fun resetInventoryForTesting(p1Stock: Int = 2) {
  productInventory["p1"]?.set(p1Stock) ?: productInventory.put("p1", AtomicInteger(p1Stock))
  productInventory["p2"]?.set(5) ?: productInventory.put("p2", AtomicInteger(5))
  productInventory["p3"]?.set(10) ?: productInventory.put("p3", AtomicInteger(10))
}

fun main() {
  embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
    .start(wait = true)
}

fun Application.module() {
  routing {
    get("/") {
      call.respondText("Parikshan Storefront Ktor Backend Server Running", ContentType.Text.Plain)
    }

    get("/api/inventory/reset") {
      val stock = call.request.queryParameters["stock"]?.toIntOrNull() ?: 2
      resetInventoryForTesting(stock)
      call.respondText("""{"status":"ok","p1Stock":${productInventory["p1"]?.get()}}""", ContentType.Application.Json)
    }

    get("/api/products") {
      val p1Stock = productInventory["p1"]?.get() ?: 0
      val p2Stock = productInventory["p2"]?.get() ?: 5
      val p3Stock = productInventory["p3"]?.get() ?: 10
      val json = """[
        {"id":"p1","title":"Wireless Noise-Canceling Headphones","category":"Electronics","price":199.99,"description":"Premium over-ear headphones with active noise cancellation and 30-hour battery life.","tags":["Audio","Wireless","Premium"],"rating":4.8,"stock":$p1Stock},
        {"id":"p2","title":"Ergonomic Mechanical Keyboard","category":"Electronics","price":129.50,"description":"Custom RGB mechanical keyboard with tactile switches and PBT keycaps.","tags":["Peripheral","Office","Productivity"],"rating":4.7,"stock":$p2Stock},
        {"id":"p3","title":"Smart Fitness Watch","category":"Wearables","price":149.00,"description":"Track workouts, heart rate, oxygen levels, and sleep quality with 7-day battery.","tags":["Fitness","Health","Smart"],"rating":4.5,"stock":$p3Stock}
      ]"""
      call.respondText(json, ContentType.Application.Json)
    }

    post("/api/login") {
      val body = call.receiveText()
      if (body.contains("@")) {
        call.respondText("""{"status":"success","user":{"email":"alex@example.com"}}""", ContentType.Application.Json)
      } else {
        call.respondText("""{"status":"error","message":"Invalid credentials"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
      }
    }

    post("/api/orders") {
      val body = call.receiveText()
      val targetProduct = if (body.contains("p2")) "p2" else if (body.contains("p3")) "p3" else "p1"
      val remaining = productInventory[targetProduct]?.decrementAndGet() ?: 0
      val orderId = "ORD-" + (1000..9999).random()
      call.respondText("""{"status":"success","orderId":"$orderId","remainingStock":$remaining}""", ContentType.Application.Json)
    }
  }
}