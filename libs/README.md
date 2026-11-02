# libs

`touhoulittlemaid-1.5.3.jar` is a local copy of Touhou Little Maid, kept only as an offline
fallback for the `flatDir` repo. The build normally pulls TLM from the Modrinth maven repo
(`tlm_version` in `gradle.properties`), so this jar can be deleted without breaking the build.

`SereneSeasons-forge-1.20.1-9.1.0.3.jar` is a compileOnly soft dependency (winter work skip).
It is never shipped and never loaded at runtime — the mod only reaches into it when Serene Seasons
is actually installed. Copy a 9.1.x build here to compile without network access.
