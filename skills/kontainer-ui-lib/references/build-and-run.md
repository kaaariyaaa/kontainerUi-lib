# Build and Run Reference

## Nix-first Commands

- Full build:
  - `nix develop -c ./gradlew clean build`
- Assemble only:
  - `nix develop -c ./gradlew assemble`
- Stop Gradle daemon when needed:
  - `nix develop -c ./gradlew --stop`

## Artifacts

- Main jar: `build/libs/kontainer-ui-lib-0.1.0.jar`
- Shaded jar: `build/libs/kontainer-ui-lib-0.1.0-min.jar`

## Local Server Deploy (if server folder exists)

1. Copy `*-min.jar` into `server/plugins/`
2. Restart server
3. Check startup logs for plugin enable success
