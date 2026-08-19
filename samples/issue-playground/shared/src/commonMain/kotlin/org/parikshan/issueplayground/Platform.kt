package org.parikshan.issueplayground

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform