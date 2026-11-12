# oeHosts-Agent

> **CLAUDE.md update rule:** Record development rules and procedures only; user-facing description and usage belong in `README.md`. All text in this file is written in English.

A `-javaagent` that maps host names to the addresses listed at an oeHubApp oeHosts share URL (`../oeHubApp`, `ShareServer`), through the JDK 18 resolver SPI (`java.net.spi.InetAddressResolverProvider`, JEP 418).

## Build

- `./gradlew test jar` → `build/libs/oehosts-agent-<version>.jar` (the version from `build.gradle.kts`, also in the manifest and the first log line). JDK 25 toolchain.
- Supported JDKs: 8, 11, 17 and 18+. `src/main` (premain `Agent`, loader `HostsAgent`, `HostsTable`, `LegacyResolver`) is compiled with `--release 8`: keep it free of any API newer than Java 8 (no `strip`, `isBlank`, `List.of`, text blocks...); call the module API by reflection. Only `src/jdk18` (`OeHostsResolverProvider`, the JEP 418 SPI) is compiled with `--release 18`; nothing in `src/main` may reference it (the JDK loads it through `META-INF/services`).
- Hooks per version (`Agent.premain`): 18+ the SPI; 9-17 `LegacyResolver.installJdk9To17` (opens `java.net` with `Instrumentation.redefineModule`, wraps `InetAddress.nameService` in a proxy); 8 `LegacyResolver.installJdk8` (proxy first in `InetAddress.nameServices`). These touch JDK internals; a failure must only log and leave the app unmapped.
- No runtime dependencies: the jar is put on the application's system class path, where any library would clash with the application's own.

## Rules

- The agent must never stop or break the application: `premain` catches everything, a failed load keeps the previous mapping, and an unreachable URL is logged once per failure streak.
- The provider is found through `META-INF/services` (the agent jar is on the system class path). Nothing in the resolver path may do a name lookup itself; IP literals are parsed without the resolver (`HostsTable.literal`).
- Unlisted hosts and reverse lookups go to the built-in resolver unchanged.
- Never log a share URL's token (`HostsAgent.label`).
- Options are system properties named `-Doehosts.agent.<name>`; document new ones in `README.md`.
- The hosts parsing follows the hosts-file format plus Chrome-style `*.domain` wildcards; when oeHubApp's share output format changes, update `HostsTable` and its tests.

## Tests

- `AgentTest` starts child JVMs on JDK 8, 11, 17 (Gradle toolchains, downloaded by the foojay resolver) and the build JDK with `-javaagent:<built jar>` against a local HTTP server; the child runs `src/testApp` (compiled for 8) and nothing else on its class path, so the agent is loaded from the jar only. Run the whole suite after any change to the hooks, the resolver or the loader.

## Release

- Tag = bare version (`0.1.0`, no `v`), the same as `version` in `build.gradle.kts` and the jar manifest's `Implementation-Version`. Release assets: `oehosts-agent.jar` (the built `oehosts-agent-<version>.jar`, renamed) and `SHA256SUMS.txt` (`sha256sum` format, two spaces, no `*`). Release notes in Korean.
- Keep both asset names and the tag rule exactly: oeHubApp (`../oeHubApp`, `HostsAgent`) installs `releases/latest/download/oehosts-agent.jar` on every start when the tag is newer than the installed agent, and rejects it unless the checksum and the manifest version match.
- Run the whole test suite before releasing. After publishing, download the jar back and check its sha256.
- oeHubApp also bundles the agent from this checkout at its own build time; a new agent release reaches users without an oeHubApp release, but bump oeHubApp's bundled copy at its next release.

## Documentation Language

- Source code comments are in English; `README.md` is in Korean.
