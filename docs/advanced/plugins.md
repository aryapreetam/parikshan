# Plugins

Plugins extend `E2ETestScope` with target-specific and design-system-specific automation helpers.

## What Are Plugins

Plugins allow package-isolated extensions to be added to the Parikshan DSL without polluting the core `E2ETestScope` API surface. They provide dedicated methods for interacting with complex, custom-drawn components that may not expose standard accessibility semantics on all platforms.

## Upcoming: `parikshan-material3` Plugin

The upcoming `parikshan-material3` plugin package will add native support for complex Material 3 components, such as DatePicker calendar grids and TimePicker clock dials.
