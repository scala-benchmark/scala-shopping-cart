# How to build (and run) scala-shopping-cart

A complete, battle-tested guide for building this project on a clean machine,
written so a colleague on a **different OS / JDK setup** can reproduce it. If
anything goes wrong, jump straight to [Troubleshooting](#troubleshooting).

> Unlike the older Scala 2.12 reference projects (which require JDK 11), this
> project uses a **modern Scala 2.13.14 + sbt 1.9.8** toolchain. It compiled
> cleanly on **JDK 21** in our verification run. You do **not** need to downgrade
> your JDK.

## Toolchain at a glance

| Component | Version | Pinned in |
| --- | --- | --- |
| Scala | 2.13.14 | `build.sbt` (`ThisBuild / scalaVersion`) |
| sbt | 1.9.8 | `project/build.properties` |
| JDK to build | **21 verified**; 11 and 17 also supported | not pinned, you set `JAVA_HOME` |
| Build version | first 7 chars of `git rev-parse HEAD` | computed in `build.sbt:4` (see git gotcha below) |
| Macro annotations | `-Ymacro-annotations` (for `newtype`) | `build.sbt:45` |
| Compiler plugin | `kind-projector` 0.13.3 | `build.sbt:51` |
| Snapshot resolver | Sonatype snapshots (for `skunk` 0.0.26) | `build.sbt:47` |

Module layout: the root project `shopping-cart` aggregates a single module
`shopping-cart-core` at `modules/core`. All source lives under
`modules/core/src/main/scala/io/kirill/shoppingcart/`.

---

## TL;DR

```bash
# from the repo root (where build.sbt lives), in a real git clone
java -version      # 11, 17 or 21 all work; 21 is verified
sbt compile        # downloads deps on first run, then compiles
```

A clean build ends with `[success] Total time: ...`. On the verification machine
`sbt compile` took ~49 s (first run, deps already partly cached).

> IMPORTANT: `sbt compile` (main sources) succeeds. `sbt Test/compile` currently
> **fails** because the test sources are out of sync with the modified main code.
> This is a known, pre-existing breakage, not a toolchain problem. See
> [The test sources do not compile](#the-test-sources-do-not-compile-known).

---

## Prerequisites checklist

- [ ] **A JDK** reachable via `JAVA_HOME` (`java -version` prints 11.x, 17.x or 21.x in the build shell).
- [ ] **sbt launcher** on PATH (`sbt --version` runs; it bootstraps the pinned sbt 1.9.8).
- [ ] **git installed AND the project is a real git clone** (not a downloaded ZIP). `build.sbt` runs `git rev-parse HEAD` at load time. See the git gotcha below.
- [ ] **Internet access** for the first build (downloads compiler + deps, including a Sonatype snapshot, into the Coursier cache).
- [ ] **Docker** (or local Postgres + Redis + LDAP) **only if you want to RUN the app**, not needed for `sbt compile`.

---

## Critical gotcha: build.sbt calls git at load time

`build.sbt:4` sets the project version from git:

```scala
ThisBuild / version := scala.sys.process.Process("git rev-parse HEAD").!!.trim.slice(0, 7)
```

This runs **every time sbt loads the build**. Two consequences for colleagues on
different machines:

1. **git must be installed** and on PATH, or sbt fails to load the build with a
   process / `IOException` error (`Cannot run program "git"`).
2. **The project must be an actual git repository.** If you downloaded a ZIP from
   a web UI (no `.git` folder), `git rev-parse HEAD` exits non-zero and the build
   load fails with a non-zero exit value from the `git` process.

**Fixes / workarounds:**

- Preferred: get the project with `git clone <url>` so `.git` is present.
- No git available, or working from a ZIP: temporarily hard-code the version so
  the build stops shelling out to git. Edit `build.sbt:4` to:

  ```scala
  ThisBuild / version := "0.1.0-SNAPSHOT"
  ```

  (Revert before committing if you want the git-derived version back.)

---

## Getting a JDK (if you do not have one)

Any of JDK 11, 17, or 21 works. JDK 21 is what we verified. Pick one.

### Option A: Portable JDK (no admin, no system change)

```powershell
# PowerShell (Temurin 21 example)
$dir = "$env:USERPROFILE\jdk21"
New-Item -ItemType Directory -Force $dir | Out-Null
$url = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_windows_hotspot_21.0.5_11.zip"
Invoke-WebRequest -Uri $url -OutFile "$dir\jdk21.zip"
Expand-Archive -Path "$dir\jdk21.zip" -DestinationPath $dir -Force
$env:JAVA_HOME = "$dir\jdk-21.0.5+11"   # the extracted folder name
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
```

```bash
# bash / Linux / macOS (Temurin 21 example)
mkdir -p ~/jdk21 && cd ~/jdk21
curl -L -o jdk21.tar.gz "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz"
tar xzf jdk21.tar.gz
export JAVA_HOME="$PWD/jdk-21.0.5+11"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

### Option B: System install

- Windows winget: `winget install EclipseAdoptium.Temurin.21.JDK`
- Windows Chocolatey: `choco install temurin21`
- Debian/Ubuntu: `sudo apt-get install -y openjdk-21-jdk` (or `-17-jdk` / `-11-jdk`)
- macOS Homebrew: `brew install temurin@21`

---

## Installing sbt (if not already present)

The `sbt` launcher just needs to be on PATH; it reads `project/build.properties`
and bootstraps the pinned **sbt 1.9.8** automatically, so the launcher version on
your PATH does not matter.

```powershell
# Windows
winget install sbt.sbt
# or:  choco install sbt
```

```bash
# Debian/Ubuntu
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo tee /etc/apt/trusted.gpg.d/sbt.asc
sudo apt-get update && sudo apt-get install sbt
sbt --version
```

```bash
# macOS
brew install sbt
```

---

## Step-by-step build

### 1. Confirm the toolchain in your build shell

```bash
java -version        # 11.x, 17.x, or 21.x
echo "$JAVA_HOME"    # PowerShell: echo $env:JAVA_HOME
sbt --version        # launcher runs; bootstraps sbt 1.9.8
git rev-parse HEAD   # must print a commit hash (proves git + repo are OK)
```

### 2. Compile the main sources

Run from the repo root (where `build.sbt` lives):

```bash
sbt compile
```

What you should see (all of this is normal):

- `welcome to sbt 1.9.8 (Eclipse Adoptium Java 21.0.10)`
- `compiling 48 Scala sources to .../modules/core/target/scala-2.13/classes ...`
- a handful of deprecation / feature **warnings** (this build does **not** use
  `-Xfatal-warnings`, so warnings do not fail it)
- `[success] Total time: 49 s, ...`

### 3. (Optional) Faster edit loop with the sbt shell

Each `sbt <task>` invocation boots a fresh JVM (slow). Open the shell once:

```
sbt
> compile
> ~compile        # watch mode: recompile on every save
> exit
```

---

## Expected, harmless warnings

These appear and are **not** errors:

- **Binary-version conflict on `geny`**:
  ```
  found version conflict(s) in library dependencies; some are suspected to be binary incompatible:
    * com.lihaoyi:geny_2.13:1.0.0 (early-semver) is selected over 0.6.5
        +- com.lihaoyi:os-lib_2.13:0.9.3        (depends on 1.0.0)
        +- com.lihaoyi:scalatags_2.13:0.12.0    (depends on 1.0.0)
        +- org.scalameta:fastparse-v2_2.13:2.3.1 (depends on 0.6.5)
  ```
  sbt picks the newer `geny` and continues. `evictionErrorLevel := Level.Warn`
  in `build.sbt:52` is what keeps this a warning instead of an error.
- **Deprecated resolver call** in `build.sbt:47`:
  `method sonatypeRepo in class ResolverFunctions is deprecated`.
- **Compile-time deprecations / feature warnings**:
  `3 deprecations in total; re-run with -deprecation for details`,
  `17 feature warnings; re-run with -feature for details`.

---

## The test sources do not compile (known)

`sbt Test/compile` currently fails with ~35 errors. Example:

```
OrderServiceSpec.scala:24:40: type mismatch;
 found   : Any
 required: io.kirill.shoppingcart.shop.order.OrderRepository[?]
          service <- OrderService.make(repo)
OrderRepositorySpec.scala:33:17: value paymentId is not a member of
          (io.kirill.shoppingcart.shop.order.Order, String)
```

**Cause:** the main code was modified (signatures changed, for example
`OrderService.get` / `OrderRepository.find` now return a `(Order, String)` tuple
instead of `Order`), but the matching test specs were not updated. This is a
source-level drift in the **test** tree, independent of your JDK / sbt / OS.

**Implications:**

- Building and running the **app** is unaffected: use `sbt compile`, `sbt stage`,
  `sbt "runMain ..."`. None of these compile the test tree.
- `sbt test` and `sbt Test/compile` will fail until the specs are realigned with
  the current main signatures.

If you only need to verify the application builds, **`sbt compile` is the command
that matters** and it succeeds.

---

## Common commands

```bash
sbt compile                                       # compile main sources (this is the build)
sbt clean                                         # delete target/
sbt clean compile                                 # full rebuild
sbt update                                        # resolve/download deps only
sbt "show scalacOptions"                          # print active compiler flags
sbt scalafmtAll                                   # format (sbt-scalafmt; scalafmtOnCompile is off)
sbt dependencyUpdates                             # check for newer deps (sbt-updates)
sbt Docker/publishLocal                           # build a Docker image (sbt-native-packager)
sbt "runMain io.kirill.shoppingcart.Application"  # run in foreground (needs Postgres + Redis)
```

sbt plugins in this build (`project/plugins.sbt`):

| Plugin | Purpose |
| --- | --- |
| `sbt-native-packager` | Build distributable packages / Docker images (`stage`, `Docker/publishLocal`) |
| `sbt-scalafmt` | Code formatting (`scalafmtAll`); `scalafmtOnCompile` is disabled in `build.sbt:46` |
| `sbt-updates` | Check for newer dependency versions (`dependencyUpdates`) |

---

## Running the app (needs PostgreSQL + Redis, plus LDAP for one endpoint)

`sbt compile` does **not** need any services. **Running** the app does. The HTTP
server listens on `http://localhost:8080` (`application.conf`).

> Not verified in this session: the verification machine had no Docker daemon and
> no local Postgres/Redis. The steps below are derived from `docker-compose.yml`
> and `modules/core/src/main/resources/application.conf`.

### 1. Start the backing services

`docker-compose.yml` ships all three (Postgres seeded from
`modules/core/src/main/resources/database.sql`, Redis, and an OpenLDAP used by the
`/shop/integration/directory/search` endpoint):

```bash
docker compose up -d            # postgres:12 (5432), redis:5 (6379), openldap (389/636)
```

Postgres defaults from compose: DB `shop`, user `postgres`, password `postgres`.

If you do not use Docker, install Postgres 12+ and Redis 5+ locally, create the
`shop` database, load `database.sql`, and either match those credentials or
override them with the env vars below.

### 2. Run

```bash
sbt "runMain io.kirill.shoppingcart.Application"
```

### Configuration and env-var overrides

Defaults live in `modules/core/src/main/resources/application.conf`. The
following keys can be overridden by environment variables (the rest are literals
in the file):

| Env var | Config key | Default |
| --- | --- | --- |
| `REDIS_HOST` | `redis.host` | `localhost` |
| `REDIS_PORT` | `redis.port` | `6379` |
| `POSTGRES_HOST` | `postgres.host` | `localhost` |
| `POSTGRES_PORT` | `postgres.port` | `5432` |
| `POSTGRES_USER` | `postgres.user` | `postgres` |
| `POSTGRES_PASSWORD` | `postgres.password` | `postgres` |

Other fixed settings of note: `server.port = 8080`, JWT secrets and a hard-coded
admin token under `auth { ... }`, payment base URI `http://pay.com`. These are
demo values; do not treat them as production secrets.

---

## How dependencies are resolved

- Libraries are declared in **`project/Dependencies.scala`** and wired in
  `build.sbt:50`. Add new deps there, then run `sbt update` (or `sbt compile`).
- One dependency (`skunk` 0.0.26) needs the **Sonatype snapshots** resolver
  (`build.sbt:47`), so the **first build requires internet access**.
- Artifacts are cached in the **Coursier cache**:
  - Windows: `%LOCALAPPDATA%\Coursier\Cache\v1\`
  - Linux: `~/.cache/coursier/v1/`
  - macOS: `~/Library/Caches/Coursier/v1/`
- To recover from a corrupt download: delete the relevant artifact folder under
  that cache and rerun `sbt update`.

---

## Windows-specific notes

- git-bash uses POSIX paths (`/c/Users/...`); PowerShell uses `C:\Users\...`. Both
  work; stay consistent within one command.
- Quote paths containing spaces: `"C:\Program Files\..."`.
- Run `sbt` from the repo root (where `build.sbt` lives).
- `JAVA_HOME` set per terminal session keeps other projects on their default JDK:
  ```powershell
  $env:JAVA_HOME = "C:\path\to\jdk"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
  ```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `Cannot run program "git"` while sbt loads the build | git not installed / not on PATH | Install git, or hard-code the version in `build.sbt:4` (see git gotcha) |
| Build load fails with a non-zero `git` exit value | Project is a ZIP, no `.git` repo | `git clone` the project, or hard-code the version in `build.sbt:4` |
| `Test/compile` / `sbt test` fails with ~35 errors (`OrderService.make`, `value paymentId is not a member of (Order, String)`) | Test sources drifted from modified main code | Expected; build the app with `sbt compile`. Realign the specs to current signatures to fix tests |
| `found version conflict(s) ... geny ... binary incompatible` | Transitive `geny` versions differ | Harmless warning; sbt selects 1.0.0 and continues |
| `method sonatypeRepo ... is deprecated` | Old resolver API in `build.sbt:47` | Harmless warning; ignore |
| First build hangs / very slow | Downloading compiler + deps (incl. a snapshot) | Wait once; later builds are cached |
| `unresolved dependency` / download errors | No internet / proxy / corrupt cache | Check connectivity/proxy; clear the Coursier artifact folder; `sbt update` |
| `OutOfMemoryError` / `Metaspace` | Heap too small | `JAVA_TOOL_OPTIONS="-Xmx2g"` then rebuild |
| sbt stuck on stale state | Incremental cache out of date | `sbt clean compile` |
| App fails at startup with `Connection refused` / auth failure | Postgres/Redis not running or wrong creds | `docker compose up -d`; check `POSTGRES_*` / `REDIS_*` env vars |
| App starts but queries fail / tables missing | DB not initialized from `database.sql` | Recreate the DB loading `modules/core/src/main/resources/database.sql` |

### Still stuck? Gather this before asking for help

```bash
java -version
echo "$JAVA_HOME"        # PowerShell: echo $env:JAVA_HOME
sbt --version
git rev-parse HEAD
```

Run `sbt clean compile` and copy the **first** `[error]` line (errors cascade, so
the first is the real cause), plus the output of the commands above.
