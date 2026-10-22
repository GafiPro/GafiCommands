# GafiCommands 1.1.0

Native Fabric client mod for Minecraft Java 1.21.11. Requires Java 21, Fabric Loader and Fabric API; JsMacrosCE is not required.

## Features
- Public-chat trigger **Gafi Quit**: sends `okay` and then `/hub` immediately without opening the chat screen.
- **Gafi Calculate**: safe math parser with arithmetic, parentheses, powers, modulo, factorial, scientific notation, constants and math functions. 3-second anti-spam cooldown.
- Public triggers: **Gafi Jump**, **Gafi Spin**, **Gafi Crouch**.
- Persistent case-insensitive substring blacklist at `.minecraft/config/gaficommands.json`.
- Local commands: `/gaficommands help`, `/gaficommands blacklist <fragment>`, `/gaficommands blacklist remove <fragment>`, `/gaficommands blacklist list`.

Default blacklist entries: `Siebe_pro2707` and `GafiPro`. A fragment such as `fox` blocks any sender whose nickname contains that fragment; this is an example only.

## Calculator
Operators: `+ - * / % ^`, parentheses and postfix factorial `!`.
Constants: `pi`, `e`, `tau`, `phi`.
Functions include `abs`, `sqrt`, `cbrt`, `sin`, `cos`, `tan`, inverse trig functions, `ln`, `log`, `log2`, `exp`, `floor`, `ceil`, `round`, `sign`, `fact`, `pow`, `root`, `percent`, `sum`, `avg`, `hypot`, `min`, `max`, `clamp`. Trigonometric functions use degrees. Results >= 1e12 or non-zero results < 1e-6 use scientific notation.

## Build
Run `./gradlew test build` with JDK 21 and Internet access. The remapped mod JAR is produced in `build/libs/`.
