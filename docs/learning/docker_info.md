# Docker Quick Reference

This guide records the Docker concepts and commands used by Agentic Knowledge Hub. Run project commands from the directory containing `docker-compose.yml`.

## 1. Core concepts

| Term | Meaning |
|---|---|
| Image | Read-only package containing an application and its runtime dependencies. |
| Container | A running or stopped instance created from an image. |
| Dockerfile | Instructions for building an image. |
| Compose file | YAML configuration describing one or more related services. |
| Service | A named application component in a Compose file, such as `postgres`. |
| Volume | Docker-managed persistent storage that survives container recreation. |
| Port mapping | Connects a Mac host port to a port inside a container. |
| Registry | Storage for images, such as Docker Hub or Google Artifact Registry. |

An **image** is similar to a class or template; a **container** is a runtime instance created from that image.

## 2. Verify the installation

```bash
docker --version
docker compose version
docker info
```

| Command | Use |
|---|---|
| `docker --version` | Displays the Docker CLI version. |
| `docker compose version` | Confirms that the Compose plugin is available. |
| `docker info` | Shows Docker Engine status, configuration, images, and containers. It fails when Docker Desktop is not running. |

On macOS, start Docker Desktop before running container commands.

## 3. Agentic Knowledge Hub commands

Move to the project directory:

```bash
cd "/Users/mashoodahmed/Documents/KB Portal/agentic-knowledge-hub"
```

Use your own checkout path if it differs. Check the current directory and Compose file:

```bash
pwd
ls -l docker-compose.yml
docker compose config --services
docker compose config
```

| Command | Use |
|---|---|
| `pwd` | Displays the current directory. |
| `ls -l docker-compose.yml` | Confirms that the Compose file exists. |
| `docker compose config --services` | Lists valid service names. This project should show `postgres`. |
| `docker compose config` | Parses and displays the resolved Compose configuration. It also detects YAML errors. |

Start only PostgreSQL:

```bash
docker compose up -d postgres
```

The command means:

| Part | Meaning |
|---|---|
| `docker` | Runs the Docker CLI. |
| `compose` | Uses the Compose configuration in the current directory. |
| `up` | Creates and starts the requested service. |
| `-d` | Runs it in the background, called detached mode. |
| `postgres` | Starts the service named `postgres`. The spelling must match the YAML file. |

For this project, Docker Compose:

1. Pulls `pgvector/pgvector:pg16` if it is not already available.
2. Creates a PostgreSQL container with pgvector support.
3. On an empty data directory, initializes the local database and user named `akh`.
4. Maps Mac port `5432` to container port `5432`.
5. Mounts `database/schema.sql` for first-time database initialization.
6. Stores database files in the persistent `akh-postgres` volume.
7. Runs `pg_isready` as a health check.

`up` also reconciles configuration changes and may recreate an existing container. A selected service can start its declared dependencies; `postgres` has none here. Detached mode returns control to the terminal without waiting for database readiness. Check `docker compose ps` and the logs before connecting.

The logical volume key is `akh-postgres`; Docker normally prefixes its actual name with the Compose project name. The schema mount is a read-only bind mount from this checkout, while the database volume is Docker-managed storage.

The Compose file currently defines only PostgreSQL, so starting all services has the same effect here. It does not build or start the API. Follow the [root README](../../README.md#start-locally) to run FastAPI in the local Python environment.

The checked-in database password `akh` is a local development default. The current `5432:5432` mapping is not restricted to loopback. Do not use these defaults for a publicly reachable database. Changing `.env` alone does not change these hard-coded Compose credentials.

Start every service defined in the Compose file:

```bash
docker compose up -d
```

## 4. Check status and logs

```bash
docker compose ps
docker compose logs postgres
docker compose logs -f postgres
docker compose logs --tail 100 postgres
```

| Command | Use |
|---|---|
| `docker compose ps` | Shows project containers, status, health, and port mappings. |
| `docker compose logs postgres` | Displays existing PostgreSQL logs. |
| `docker compose logs -f postgres` | Follows new log messages until `Control+C` is pressed. |
| `docker compose logs --tail 100 postgres` | Displays only the latest 100 log lines. |

Expected status after startup: `running` and eventually `healthy`.

## 5. Stop, start, and restart

```bash
docker compose stop postgres
docker compose start postgres
docker compose restart postgres
docker compose down
```

| Command | Use |
|---|---|
| `stop` | Stops a container without removing it. |
| `start` | Starts an existing stopped container. |
| `restart` | Stops and starts the container; does not apply changed Compose configuration. Use `up -d` for that. |
| `down` | Stops and removes the project's containers and network. Named volumes remain by default. |

## 6. Open a shell or PostgreSQL session

Open a shell inside the container:

```bash
docker compose exec postgres bash
```

Open PostgreSQL directly:

```bash
docker compose exec postgres psql -U akh -d akh
```

If `psql` is installed on the Mac, connect through the published port:

```bash
psql -h localhost -p 5432 -U akh -d akh
```

Enter the local development password when prompted. Inside a future Compose application container, the hostname would be `postgres`, not `localhost`.

Useful commands inside `psql`:

```text
\l              List databases
\dt             List tables
\d documents    Describe the documents table
\dx             List installed extensions
\q              Exit psql
```

## 7. Images, containers, and volumes

```bash
docker images
docker ps
docker ps -a
docker volume ls
docker inspect agentic-knowledge-hub-postgres-1
```

| Command | Use |
|---|---|
| `docker images` | Lists locally downloaded or built images. |
| `docker ps` | Lists running containers. |
| `docker ps -a` | Lists running and stopped containers. |
| `docker volume ls` | Lists persistent Docker volumes. |
| `docker inspect CONTAINER` | Displays detailed container configuration and runtime state. |

The generated container name can vary. Obtain the exact name from `docker compose ps` before using `docker inspect`.

## 8. Compose filenames

Docker Compose automatically recognizes common names including:

```text
compose.yaml
compose.yml
docker-compose.yaml
docker-compose.yml
```

`compose.yaml` is preferred by Docker, but this repository uses `docker-compose.yml`. No rename is needed. Specify this project's file explicitly with:

```bash
docker compose -f docker-compose.yml up -d postgres
```

Use `-f` for a custom filename (the following custom files are examples, not files in this repository):

```bash
docker compose -f compose.dev.yml up -d
```

Combine a base file with an environment-specific override:

```bash
docker compose -f docker-compose.yml -f compose.local.yml up -d
```

Use the same file selection for status, logs, and shutdown. Alternatively, set `COMPOSE_FILE=docker-compose.yml` for a command or shell session. Later files extend or override values from earlier files. The Compose filename and service name are different: `postgres` is defined below `services:` inside the YAML file.

## 9. Build application images

This project's `postgres` service uses a prebuilt image and has no `build:` configuration. These generic Compose build commands apply only after a service defines one:

```bash
docker compose build
docker compose build --no-cache
```

Build directly from a Dockerfile:

```bash
docker build -t agentic-knowledge-hub:local .
```

| Command | Use |
|---|---|
| `docker compose build` | Builds images for services that define a build configuration. |
| `--no-cache` | Rebuilds every layer instead of reusing cached layers. |
| `-t` | Assigns an image name and tag. |
| `.` | Uses the current directory as the build context. |

## 10. Cleanup commands

Remove the project containers and network while preserving database data:

```bash
docker compose down
```

Remove containers, network, and project volumes:

```bash
docker compose down -v
```

**Warning:** `docker compose down -v` deletes the PostgreSQL volume and its local database data. Use it only when intentionally resetting the database.

View Docker disk usage:

```bash
docker system df
```

Avoid broad prune commands until you have inspected what Docker will remove.

## 11. Troubleshooting

### `no such service: postgress`

The requested service is misspelled. Use the exact service name:

```bash
docker compose up -d postgres
```

Confirm it with:

```bash
docker compose config --services
```

### `command not found: docker`

This means the shell cannot find the CLI. Check that Docker Desktop is installed and its CLI is on `PATH`; starting the engine alone does not fix a missing executable. If Docker works in the macOS Terminal but not in an IDE terminal, the IDE probably has an older or different `PATH` environment.

```bash
command -v docker
echo "$PATH"
```

Completely restart the IDE after starting Docker Desktop, or use the macOS Terminal for Docker commands.

### Cannot connect to the Docker daemon

Start Docker Desktop and wait for its engine to be ready. Inspect the selected context:

```bash
docker context ls
docker info
```

If a stale remote context is selected, choose the intended local Desktop context from the list with `docker context use CONTEXT_NAME`. Do not switch blindly if you intentionally use a remote engine.

### No configuration file found

Run from the checkout root, or supply the full Compose path with `-f`. Quote paths containing spaces, such as `KB Portal`.

### macOS mounts or image startup fail

For a mount denied error, check Docker Desktop file-sharing access to the checkout and confirm `database/schema.sql` exists. On Apple silicon, an `exec format error` or missing platform manifest can indicate an incompatible image architecture; inspect the image/platform before forcing emulation. Check Docker Desktop disk and memory availability if startup stalls. Review logs locally; do not share credentials or document content from diagnostics.

### Port `5432` is already in use

Find containers using published ports:

```bash
docker ps
```

A local PostgreSQL installation or another container may already be using port `5432`. Use `lsof -nP -iTCP:5432 -sTCP:LISTEN` to identify a Mac process. Stop a conflicting process only if you own it and know it is safe, or deliberately change the host side of the Compose mapping, for example `5433:5432`, and update the application connection port as well.

### Container starts and then exits

```bash
docker compose ps -a
docker compose logs --tail 100 postgres
```

The logs normally reveal configuration, permission, initialization, or port problems.

### Schema changes do not appear

Files in `/docker-entrypoint-initdb.d/` execute only when PostgreSQL initializes an empty data directory. Changing `database/schema.sql` does not automatically rerun it against an existing volume. Apply a migration, execute the SQL manually, or intentionally reset the local volume when losing local data is acceptable.

## 12. Interview refresh

- A container shares its Docker engine host kernel but has isolated processes, networking, and filesystem views; a virtual machine includes a guest operating system. On macOS, Docker Desktop runs Linux containers inside a Linux VM.
- Images are immutable layers. Containers add a writable runtime layer.
- Container files are ephemeral unless important state is stored in a volume or external service.
- `EXPOSE` documents an intended container port; publishing with `-p` or a Compose `ports` mapping makes it reachable through the host.
- A health check reports application readiness; it is different from the process merely running.
- Docker Compose is useful for local multi-container applications. Kubernetes provides broader production orchestration, scheduling, scaling, and recovery capabilities.
- Production images should use small trusted bases, non-root users, pinned dependencies, vulnerability scanning, and no embedded secrets.
- On GCP, application images are commonly stored in Artifact Registry and deployed to services such as Cloud Run or GKE.

## 13. Recommended daily sequence

```bash
cd "/Users/mashoodahmed/Documents/KB Portal/agentic-knowledge-hub"
docker compose up -d postgres
docker compose ps
docker compose logs --tail 50 postgres
```

When finished:

```bash
docker compose down
```

Using `down` without `-v` keeps the database volume for the next session.

## 14. Official references

- [Docker Compose model and filenames](https://docs.docker.com/compose/intro/compose-application-model/)
- [Compose CLI and file selection](https://docs.docker.com/reference/cli/docker/compose/)
- [Compose lifecycle overview](https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-docker-compose/)

Return to the [learning index](README.md).
