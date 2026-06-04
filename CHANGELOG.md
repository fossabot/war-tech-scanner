# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Detection of Java EE / Jakarta EE technologies via bytecode (ASM), XML descriptors, and libraries
- Support for `.war`, `.ear`, `.jar`, and `.rar` files, including nested archives
- Rich terminal UI with spinner, progress bar, and bar chart (ANSI + Unicode)
- JSON output with `--json`
- Migration hints for JBoss EAP 7.x / 8.x and Java 11 / 17 / 21 via `--target-eap` and `--target-java`
- Per-installation MTA command generation via `--mta-config`:
  - `BARE_METAL` type: runs the local binary with automatically resolved targets/sources
  - `CONTAINER` type: generates a Docker/Podman command with official `registry.redhat.io` images
  - `OPENSHIFT` type: generates `oc apply` with an OLM Subscription + MTA Hub API calls
- Automatic discovery of sources and targets via MTA CLI's `--list-sources` / `--list-targets`
- Fallback to static mappings when the binary/image is not available
- `THIRD-PARTY-NOTICES.txt` generated automatically by the `license-maven-plugin`

[Unreleased]: https://github.com/darioajr/war-tech-scanner/compare/HEAD...HEAD
