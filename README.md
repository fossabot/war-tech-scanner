# WAR Tech Scanner

[![CI/CD](https://github.com/darioajr/war-tech-scanner/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/darioajr/war-tech-scanner/actions/workflows/ci-cd.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.darioajr/war-tech-scanner.svg)](https://central.sonatype.com/artifact/io.github.darioajr/war-tech-scanner)
[![Coverage](https://codecov.io/gh/darioajr/war-tech-scanner/branch/main/graph/badge.svg)](https://codecov.io/gh/darioajr/war-tech-scanner)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=darioajr_war-tech-scanner&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=darioajr_war-tech-scanner)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![FOSSA Status](https://app.fossa.com/api/projects/custom%2B50664%2Fgit%40github.com%3Adarioajr%2Fwar-tech-scanner.git.svg?type=shield&issueType=license)](https://app.fossa.com/projects/custom%2B50664%2Fgit%40github.com%3Adarioajr%2Fwar-tech-scanner.git?ref=badge_shield&issueType=license)
[![Java](https://img.shields.io/badge/java-21%2B-orange.svg)](https://adoptium.net)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fdarioajr%2Fwar-tech-scanner.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fdarioajr%2Fwar-tech-scanner?ref=badge_shield)

Java CLI to detect technologies in `.war`, `.ear`, `.jar`, and `.rar` files, focused on inventory for JBoss EAP / Jakarta EE migration.

## Detected technologies

| Technology | Evidence sources |
|---|---|
| EJB | `@Stateless`, `@Stateful`, `@MessageDriven`, `ejb-jar.xml`, `jboss-ejb3.xml` |
| JPA | `@Entity`, `@PersistenceContext`, `persistence.xml` |
| Hibernate | `SessionFactory`, `hibernate.cfg.xml`, `*.hbm.xml`, `hibernate-core-*.jar` |
| CDI | `@Inject`, `@ApplicationScoped`, `beans.xml` |
| JSF | `@ManagedBean`, `faces-config.xml`, `*.xhtml` |
| JAX-RS | `@Path`, `@GET`, `@POST`, `resteasy-*.jar`, `jersey-*.jar` |
| JAX-WS/SOAP | `@WebService`, `@WebMethod`, `cxf-*.jar`, `axis-*.jar` |
| Servlet | `HttpServlet`, `web.xml`, `servlet-api-*.jar` |
| Spring | `@Component`, `@Service`, `applicationcontext.xml`, `spring-*.jar` |
| Struts | `struts.xml`, `struts-*.jar` |

Detection uses three layers:

1. **Bytecode** — reading `.class` files with ASM to find annotations and `javax.*`, `jakarta.*`, `org.hibernate.*` types, etc.
2. **XML descriptors** — `persistence.xml`, `ejb-jar.xml`, `hibernate.cfg.xml`, `*.hbm.xml`, `beans.xml`, `faces-config.xml`, `web.xml`, etc.
3. **Libraries** — JAR names inside `WEB-INF/lib` and nested archives.

## Prerequisites

- Java 21+
- Maven 3.9+ (build only)

## Build

```bash
mvn -DskipTests package
```

The generated artifact is `target/war-tech-scanner-0.1.0-SNAPSHOT.jar` (fat JAR with all dependencies).

## Usage

```
java -jar target/war-tech-scanner-0.1.0-SNAPSHOT.jar <artifact> [options]
```

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `<artifact>` | positional | `.war`, `.ear`, `.jar`, or `.rar` file to analyze |
| `--json` | flag | Prints result as JSON (disables rich UI) |
| `--no-nested` | flag | Does not analyze nested archives (JARs inside WARs, etc.) |
| `--max-evidence=N` | integer | Max evidences listed per technology (default: `5`) |
| `--target-eap=X.Y` | string | Target JBoss EAP version (e.g. `7.4`, `8.0`, `8.1`) |
| `--target-java=N` | integer | Target Java version (e.g. `11`, `17`, `21`) |
| `--mta-config=PATH` | path | MTA configuration file. **Required** to generate `mta-cli` command suggestions |
| `-h`, `--help` | flag | Shows help |
| `-V`, `--version` | flag | Shows version |

### Examples

**Basic scan with rich UI:**
```bash
java -jar target/war-tech-scanner-0.1.0-SNAPSHOT.jar my-app.war
```

**JSON output:**
```bash
java -jar target/war-tech-scanner-0.1.0-SNAPSHOT.jar my-app.war --json > report.json
```

**Migration analysis for EAP 8.1 + Java 21:**
```bash
java -jar target/war-tech-scanner-0.1.0-SNAPSHOT.jar my-app.ear \
  --target-eap=8.1 \
  --target-java=21
```

**Generate MTA command suggestion:**
```bash
java -jar target/war-tech-scanner-0.1.0-SNAPSHOT.jar my-app.ear \
  --target-eap=8.1 \
  --target-java=21 \
  --mta-config war-tech-scanner-config.json \
  --json > report.json
```

**Without analyzing nested JARs:**
```bash
java -jar target/war-tech-scanner-0.1.0-SNAPSHOT.jar my-app.war --no-nested
```

**Bulk inventory:**
```bash
find /apps -type f \( -name "*.war" -o -name "*.ear" \) \
  -exec java -jar target/war-tech-scanner-0.1.0-SNAPSHOT.jar {} \
    --target-eap=8.1 --target-java=21 --json \; \
  > inventory.jsonl
```

## MTA command suggestion

When `--mta-config` is provided, the scanner runs each configured MTA installation to discover the available `sources`, `targets`, and `providers`, cross-references them with the detected technologies, and generates a ready-to-run command per installation.

### Configuration file (`war-tech-scanner-config.json`)

```json
{
  "mtaInstallations": [
    {
      "label": "MTA 7.2 - Local",
      "type": "BARE_METAL",
      "path": "/opt/mta-7.2/bin/mta-cli"
    },
    {
      "label": "MTA 7.2 - Docker",
      "type": "CONTAINER",
      "image": "registry.redhat.io/mta/mta-cli-rhel9:7.2",
      "containerEngine": "docker"
    },
    {
      "label": "MTA 7.2 - Podman",
      "type": "CONTAINER",
      "image": "registry.redhat.io/mta/mta-cli-rhel9:7.2",
      "containerEngine": "podman"
    },
    {
      "label": "MTA 6.2 - Docker",
      "type": "CONTAINER",
      "image": "registry.redhat.io/mta/mta-cli-rhel8:6.2",
      "containerEngine": "docker"
    },
    {
      "label": "MTA 6.2 - Podman",
      "type": "CONTAINER",
      "image": "registry.redhat.io/mta/mta-cli-rhel8:6.2",
      "containerEngine": "podman"
    },
    {
      "label": "MTA 7.2 - OpenShift",
      "type": "OPENSHIFT",
      "namespace": "mta",
      "hubRoute": "https://mta-mta.apps.cluster.example.com",
      "operatorChannel": "stable-v7",
      "operatorCatalog": "redhat-operators"
    }
  ]
}
```

### Installation types

#### `BARE_METAL`

Runs the locally installed `mta-cli` binary.

| Field | Required | Description |
|---|---|---|
| `path` | yes | Absolute path to the `mta-cli` binary |

Generated command:
```bash
/opt/mta-7.2/bin/mta-cli analyze \
  --input my-app.ear \
  --output ./mta-report \
  --target eap81,java21 \
  --source ejb,jpa,hibernate
```

#### `CONTAINER`

Runs via Docker or Podman using official Red Hat images from `registry.redhat.io`.

| Field | Required | Default | Description |
|---|---|---|---|
| `image` | yes | — | MTA CLI image. Use `registry.redhat.io/mta/mta-cli-rhel9:<version>` (MTA 7.x) or `registry.redhat.io/mta/mta-cli-rhel8:<version>` (MTA 6.x) |
| `containerEngine` | no | `docker` | Container engine: `docker` or `podman` |

> **Note:** `registry.redhat.io` requires authentication with a Red Hat account (<https://access.redhat.com>).

Generated command:
```bash
docker login registry.redhat.io
docker run --rm \
  -v my-app.ear:/app/input/my-app.ear:ro,z \
  -v $(pwd)/mta-report:/app/output:z \
  registry.redhat.io/mta/mta-cli-rhel9:7.2 analyze \
  --input /app/input/my-app.ear \
  --output /app/output \
  --target eap81,java21 \
  --source ejb,jpa
```

#### `OPENSHIFT`

Installs the MTA operator via OLM from the `redhat-operators` catalog and creates the analysis through the MTA Hub API.

| Field | Required | Default | Description |
|---|---|---|---|
| `namespace` | no | `mta` | Namespace where the operator is installed |
| `hubRoute` | yes | — | Base URL of the MTA Hub exposed by the operator |
| `operatorChannel` | no | `stable-v7` | OLM channel: `stable-v7` (MTA 7.x) or `stable-v6` (MTA 6.x) |
| `operatorCatalog` | no | `redhat-operators` | OLM CatalogSource |

Generated command (two steps):
```bash
# Step 1 — install the MTA operator via OLM
oc apply -f - <<'EOF'
apiVersion: v1
kind: Namespace
metadata:
  name: mta
---
apiVersion: operators.coreos.com/v1
kind: OperatorGroup
metadata:
  name: mta-operatorgroup
  namespace: mta
spec:
  targetNamespaces: [mta]
---
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: mta
  namespace: mta
spec:
  channel: stable-v7
  installPlanApproval: Automatic
  name: mta
  source: redhat-operators
  sourceNamespace: openshift-marketplace
EOF

# Step 2 — create analysis via MTA Hub API (wait for the operator to be Running)
MTA_HUB=https://mta-mta.apps.cluster.example.com
curl -s -X POST "$MTA_HUB/hub/applications" \
  -H "Content-Type: application/json" \
  -d '{"name":"my-app","bucket":{"name":"my-app"}}' | tee /tmp/mta-app.json

APP_ID=$(jq -r '.id' /tmp/mta-app.json)
curl -s -X POST "$MTA_HUB/hub/analyses" \
  -H "Content-Type: application/json" \
  -d '{
    "application":{"id":'$APP_ID'},
    "sources":["ejb","jpa"],
    "targets":["eap81","java21"]
  }'
```

### Automatic source/target discovery

When the binary (BARE_METAL) or the image (CONTAINER) is available locally, the scanner runs:

```
mta-cli analyze --list-sources
mta-cli analyze --list-targets
mta-cli analyze --list-providers
```

The returned tokens are cross-referenced with the detected technologies to produce a command with only the `--source` and `--target` values actually supported by the installed version. If the binary/image is not available, the command is generated from static mappings and a warning note is included.

### Output JSON structure (`--json`)

```json
{
  "artifact": "/path/to/app.ear",
  "artifactType": "EAR",
  "scannedAt": "2026-06-03T15:00:00Z",
  "technologies": [
    { "name": "EJB", "score": 42, "evidences": ["..."] }
  ],
  "descriptors": ["META-INF/persistence.xml"],
  "libraries": ["hibernate-core-5.6.jar"],
  "classesWithEvidence": ["com/example/MyBean.class"],
  "warnings": [],
  "migrationHints": [
    "[EAP 8.1] EJB: replace javax.ejb.* with jakarta.ejb.*"
  ],
  "mtaSuggestions": [
    {
      "mtaLabel": "MTA 7.2 - Docker",
      "mtaPath": "registry.redhat.io/mta/mta-cli-rhel9:7.2",
      "installationType": "CONTAINER",
      "command": "docker login registry.redhat.io\ndocker run ...",
      "resolvedSources": ["ejb", "jpa"],
      "resolvedTargets": ["eap81", "java21"],
      "note": null
    }
  ]
}
```

## Publishing to Maven Central

The `pom.xml` already includes the required metadata, `sources`, `javadocs`, GPG signing, and the `central-publishing-maven-plugin`.

Configure `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_USERNAME}</username>
      <password>${env.CENTRAL_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

Then:

```bash
mvn clean verify
mvn deploy -DskipTests
```

## Licenses

This project is distributed under the **Apache License 2.0**. See [LICENSE](LICENSE).

Third-party dependencies and their licenses are listed in [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt).

Dependency license compliance is monitored by [FOSSA](https://fossa.com):

[![FOSSA Status](https://app.fossa.com/api/projects/custom%2B50664%2Fgit%40github.com%3Adarioajr%2Fwar-tech-scanner.git.svg?type=large&issueType=license)](https://app.fossa.com/projects/custom%2B50664%2Fgit%40github.com%3Adarioajr%2Fwar-tech-scanner.git?ref=badge_large&issueType=license)
