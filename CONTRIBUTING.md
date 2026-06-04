# Contributing to WAR Tech Scanner

Thank you for your interest in contributing! This document describes how to take part in the project.

Please note that this project follows a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to respect its terms.

## How to contribute

### Reporting bugs

Before opening an issue, check whether the problem has already been reported in the [issue list](https://github.com/darioajr/war-tech-scanner/issues).

When reporting a bug, include:

- Java version and operating system
- Exact command executed
- Analyzed file (type: WAR, EAR, JAR) and expected technologies
- Full output (including errors)
- Expected behavior vs. observed behavior

### Suggesting improvements

Open an [issue](https://github.com/darioajr/war-tech-scanner/issues) describing the improvement before submitting a pull request, so we can discuss the proposal before development.

This avoids rework and ensures the contribution is aligned with the direction of the project.

### Submitting Pull Requests

1. Fork the repository
2. Create a branch from `main`:
   ```bash
   git checkout -b feat/my-contribution
   ```
3. Implement the changes following the project's conventions
4. Add or update tests where applicable
5. Make sure the build and tests pass:
   ```bash
   mvn verify
   ```
6. Commit with a clear message and sign off with DCO (see below):
   ```bash
   git commit -s -m "feat: description of the change"
   ```
7. Open the pull request describing what was done and why

## Code conventions

- Java 21 — use `var`, records, switch expressions, and other modern features when appropriate
- No new external dependencies without prior discussion — the project keeps a minimal footprint
- JUnit 5 tests for all detection and command-generation logic
- Commit messages in English, in the `<type>: <description>` format (e.g. `fix:`, `feat:`, `docs:`, `refactor:`)

## Contribution areas

- **New detected technologies** — add support for other frameworks in `TechnologyCatalog.java`
- **MTA mappings** — improve `TECH_TO_CANDIDATE_SOURCES` and `TECH_TO_TARGETS` in `MtaCommandBuilder.java`
- **Support for new MTA installation types** — e.g. Podman Compose, Helm
- **Tests** — coverage of WARs/EARs with different technology combinations
- **Documentation** — examples, tutorials, real-world use cases

## Legal — Developer Certificate of Origin (DCO)

This project uses the [Developer Certificate of Origin 1.1](https://developercertificate.org/) to manage code contributions, the same model adopted by the Linux kernel.

By submitting a contribution, you certify that:

```
Developer Certificate of Origin
Version 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I have
    the right to submit it under the open source license indicated in
    the file; or

(b) The contribution is based upon previous work that, to the best of
    my knowledge, is covered under an appropriate open source license
    and I have the right under that license to submit that work with
    modifications, whether created in whole or in part by me, under
    the same open source license, as indicated in the file; or

(c) The contribution was provided directly to me by some other person
    who certified (a), (b) or (c) and I have not modified it.

(d) I understand and agree that this project and the contribution are
    public and that a record of the contribution is maintained
    indefinitely and may be redistributed consistent with this project
    or the open source license(s) involved.
```

To sign off, add to the end of the commit message:

```
Signed-off-by: Your Name <you@email.com>
```

Using your real name. Git does this automatically with:

```bash
git commit -s
```

## License header

Every Java source file must include the Apache License 2.0 header:

```java
/*
 * Copyright 2024-present Dario Alves Junior
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

## Questions

Open an [issue](https://github.com/darioajr/war-tech-scanner/issues) with the `question` tag or get in touch by email at darioajr@gmail.com.
