# Module parikshan-core

Contains the test DSL scope, selector types, and protocol schemas used by Parikshan.

Developers interact with this module through:

- `E2ETestScope` — The receiver scope for all test actions (`click`, `input`, `scroll`, `assertVisible`, `waitFor`).
- `E2ETestConfig` — Configuration holder for test timeouts and failure screenshot behavior.
- `E2ETestLifecycle` — Interface for shared `beforeEach`/`afterEach` hooks across test classes.
- `Selector` — Intent-based query types (`auto`, `tag`, `text`) for locating UI elements.
- `ScrollDirection` — Enum specifying scroll gesture orientation (Up, Down, Left, Right).
- `BeforeAll` / `AfterAll` — Annotations for one-time setup and teardown on companion object functions.
