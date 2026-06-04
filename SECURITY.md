# Security Policy

## Supported versions

| Version | Supported |
|--------|-----------|
| 0.1.x (latest) | ✅ |

## Reporting a vulnerability

**Do not open a public issue for security vulnerabilities.**

Please report responsibly by email:

📧 **darioajr@gmail.com**

Include in your message:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (optional)

You will receive a response within **72 hours**. After confirmation and a fix, the vulnerability will be publicly disclosed along with credit to the reporter (unless you prefer to remain anonymous).

## Scope

This project is a static-analysis CLI tool. The relevant attack surfaces are:

- **Reading malicious ZIP/JAR/WAR/EAR files** — the scanner opens and iterates entries of compressed archives
- **External process execution** (`mta-cli`) — via `ProcessBuilder` with user-controlled arguments
- **JSON deserialization** (Jackson) — MTA configuration via `--mta-config`

## Dependencies

Third-party dependencies and their licenses are listed in [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt).
Security updates to dependencies are applied in regular releases.
