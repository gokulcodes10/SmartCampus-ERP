# Judge0 on this machine — what was tried, what happened, what to do instead

**Status: self-hosted Judge0 does not work on this development machine.**
It is left in `docker-compose.yml` behind the `judge0` compose profile so it does not
start by default. Phase 7 must use the hosted fallback locally, or run self-hosted
Judge0 on an amd64 Linux host.

This was tested end to end on 2026-08-19, not inferred. The transcript is below.

---

## Environment

| | |
|---|---|
| Host | macOS 26.5.1, Apple Silicon (`uname -m` → `arm64`) |
| Docker | Docker Desktop 29.7.2, server arch `arm64` |
| Judge0 | `judge0/judge0:1.13.1` with `platform: linux/amd64` |
| Supporting | `postgres:16.2-bookworm`, `redis:7.0.11-bookworm` |

---

## What actually worked

The architecture is **not** the problem. Rosetta/QEMU emulation of the amd64 images
is fine, and Judge0 comes up far enough to look healthy:

- All four containers started; Postgres and Redis both reached `healthy`.
- `uname -m` inside the worker container → `x86_64`. Emulation is working.
- `GET /about` answered:
  ```json
  {"version":"1.13.1","homepage":"https://judge0.com", ...}
  ```
- `GET /languages` returned the full language list (Java, C++, Python, ~60 entries).
- `POST /submissions` was accepted and returned a token:
  ```json
  {"token":"8e2b483b-80fe-4c3a-9524-8f049b38ee24"}
  ```
- The worker picked the job off the Resque queue and moved it to status 2 (Processing).

Anyone stopping at "the API responds" would wrongly conclude Judge0 works here.
It does not. Execution is where it dies.

## Where it fails

Submitting `print(6*7)` (language_id 71, Python 3.8.1) resolved to:

```json
{"stdout":null,"time":null,"memory":null,"stderr":null,
 "compile_output":null,
 "message":"No such file or directory @ rb_sysopen - /box/script.py",
 "status":{"id":13,"description":"Internal Error"}}
```

The worker log gives the real cause — the `/box` message above is only a downstream
symptom of the sandbox never being created:

```
Failed to create control group /sys/fs/cgroup/memory/box-5/: No such file or directory
...
chown: cannot access '/box': No such file or directory
```

## Root cause: cgroup v1 vs cgroup v2

Judge0 1.13.1 bundles **isolate 1.8.1** (confirmed: `isolate --version` →
`The process isolator 1.8.1 ... Built on 2021-03-06 from Git commit v1.8.1-8-gad39cc4`).
isolate 1.x drives the **cgroup v1** hierarchy directly, creating per-submission
control groups at paths like `/sys/fs/cgroup/memory/box-<id>/`. cgroup v2 support
did not land in isolate until the 2.x line.

Docker Desktop's LinuxKit VM is **cgroup v2 unified only**. Kernel command line,
read via `nsenter` into the VM's init namespace:

```
init=/initd loglevel=1 root=/dev/vdb ... linuxkit.unified_cgroup_hierarchy=1 ...
```

Inside the worker container:

```
$ mount | grep cgroup
cgroup on /sys/fs/cgroup type cgroup2 (rw,nosuid,nodev,noexec,relatime)

$ cat /sys/fs/cgroup/cgroup.controllers
cpuset cpu io memory hugetlb pids rdma
```

There is no `/sys/fs/cgroup/memory` directory because there is no v1 hierarchy at all.

### Why it cannot be worked around

The obvious fix — mount a cgroup v1 memory hierarchy inside the privileged
container — is impossible, because the kernel has every controller bound to the
unified v2 hierarchy:

```
$ cat /proc/cgroups
#subsys_name  hierarchy  num_cgroups  enabled
cpuset        0          16           1
cpu           0          16           1
cpuacct       0          16           1
blkio         0          16           1
memory        0          16           1
...
```

`hierarchy 0` for every controller means "attached to the v2 unified hierarchy,
unavailable for a v1 mount". Attempting it anyway, as root in a `privileged: true`
container:

```
$ docker exec -u root smartcampus-judge0-workers \
    sh -c 'mkdir -p /sys/fs/cgroup/memory && mount -t cgroup -o memory cgroup /sys/fs/cgroup/memory'
mount: /sys/fs/cgroup/memory: permission denied.
```

`privileged: true` is already set and is not the limitation — the kernel simply has
nothing to mount. `linuxkit.unified_cgroup_hierarchy=1` is baked into Docker
Desktop's VM boot and is not exposed as a user-configurable setting, so there is no
supported way to flip this VM back to cgroup v1.

This is a **Docker Desktop** constraint, not strictly an Apple Silicon one — an Intel
Mac running current Docker Desktop hits the identical wall.

---

## The fallback

### For local development on this machine — hosted Judge0 (RapidAPI)

The backend reads `JUDGE0_URL` and `JUDGE0_API_KEY`, so this is a config change with
no code change. In `.env`:

```
JUDGE0_URL=https://judge0-ce.p.rapidapi.com
JUDGE0_API_KEY=<your RapidAPI key>
```

Get a key from <https://rapidapi.com/judge0-official/api/judge0-ce>. The free tier is
rate-limited (roughly 50 submissions/day at time of writing), which is fine for
developing and testing Phase 7 but **not** for running a live contest (§31–§32).

Note the auth header differs between deployments: RapidAPI expects
`X-RapidAPI-Key` / `X-RapidAPI-Host`, whereas a self-hosted instance with
`AUTHN_TOKEN` set expects `X-Auth-Token`. `Judge0Service` needs to handle this — flag
it when Phase 7 is built.

### For contests or CI — self-host on amd64 Linux

The compose profile is correct and will work unchanged on a host that provides
cgroup v1:

```
cp judge0.conf.example judge0.conf   # set POSTGRES_PASSWORD and REDIS_PASSWORD
docker compose --profile judge0 up -d
```

On a systemd distro that defaults to cgroup v2 (Ubuntu 22.04+, Debian 11+, Fedora 31+),
boot the host with cgroup v1 first:

```
# /etc/default/grub
GRUB_CMDLINE_LINUX="systemd.unified_cgroup_hierarchy=0"
sudo update-grub && sudo reboot
```

Then verify — do not assume — with the same probe used above:

```bash
curl -s -X POST 'http://localhost:2358/submissions?base64_encoded=false&wait=true' \
  -H 'Content-Type: application/json' \
  -d '{"language_id":71,"source_code":"print(6*7)"}'
```

A working instance returns `status.id` 3 (`Accepted`) and `stdout` `"42\n"`.
Status 13 (`Internal Error`) means the cgroup problem above is still present.

### Longer-term option

Judge0 v2 / isolate 2.x add native cgroup v2 support. If a v2 image is published and
stabilises, retest the profile here before paying for hosted capacity.

---

## Files involved

- `docker-compose.yml` — `judge0-server`, `judge0-workers`, `judge0-db`, `judge0-redis`,
  all under `profiles: ["judge0"]`
- `judge0.conf.example` — template; copy to `judge0.conf`, which is local-only
- `.env.example` — `JUDGE0_URL`, `JUDGE0_API_KEY`
