package org.example.project

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
  embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
    .start(wait = true)
}

fun Application.module() {
  routing {
    get("/") {
      call.respondText("Parikshan Storefront Ktor Backend Server Running", ContentType.Text.Plain)
    }

    get("/api/products") {
      val json = """[
        {"id":"p1","title":"Wireless Noise-Canceling Headphones","category":"Electronics","price":199.99,"description":"Premium over-ear headphones with active noise cancellation and 30-hour battery life.","tags":["Audio","Wireless","Premium"],"rating":4.8},
        {"id":"p2","title":"Ergonomic Mechanical Keyboard","category":"Electronics","price":129.50,"description":"Custom RGB mechanical keyboard with tactile switches and PBT keycaps.","tags":["Peripheral","Office","Productivity"],"rating":4.7},
        {"id":"p3","title":"Smart Fitness Watch","category":"Wearables","price":149.00,"description":"Track workouts, heart rate, oxygen levels, and sleep quality with 7-day battery.","tags":["Fitness","Health","Smart"],"rating":4.5}
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
      val orderId = "ORD-" + (1000..9999).random()
      call.respondText("""{"status":"success","orderId":"$orderId"}""", ContentType.Application.Json)
    }
  }
}