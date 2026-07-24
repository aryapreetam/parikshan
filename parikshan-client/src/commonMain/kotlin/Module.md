# Module parikshan-client

Contains the public test entrypoint and platform driver resolution for Parikshan.

The primary API is `e2eTest`:

```kotlin
@Test
fun testCheckout() = e2eTest {
  click("Add to Cart")
  assertVisible("cart_badge")
}
```

Platform drivers (Android, iOS, Desktop, WasmJs) are resolved automatically by the Gradle plugin based on the active test target. Test code does not reference drivers directly.
