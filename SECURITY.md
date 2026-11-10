# Security Policy

## Supported versions

Security fixes are accepted against the latest `main` and the most recent tagged release.

## What this project is (and is not)

java-diag is a **read-oriented** diagnostics helper for Java processes:

- Probes may read whitelisted log paths and invoke JDK tools (`jps` / `jcmd` / …) on the host where they run.
- Dangerous tools (`heap_dump`, live GC, etc.) stay **off** unless explicitly enabled.
- It is **not** a remote shell, Arthas-style rewrite platform, or public SaaS control plane.

## Deployment assumptions

1. Run probes and the gateway on a **trusted network** (VPC / cluster CNI). Do not expose `/sse`, `/mcp`, or `/registry/*` to the public Internet without additional controls.
2. Always set strong tokens:
   - Probe / Gateway Agent Bearer (`auth.token`)
   - Optional Registry token (`register.token`) when the registry API is reachable beyond a tight trust boundary
   - Admin console password (`admin.password`) — change the default before any shared environment
3. Keep `allowed.log.dirs` as narrow as practical.
4. Gateway must be able to reach each probe’s `advertise.url`; treat that path as an internal admin channel.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security-sensitive reports.

Prefer one of:

- GitHub **Private vulnerability reporting** on this repository (if enabled), or
- Email the maintainer listed in the GitHub profile for [`chenxk/java-diag`](https://github.com/chenxk/java-diag)

Include: affected version / commit, reproduction steps, impact, and any suggested fix.

We will acknowledge reports as soon as practical and coordinate a fix or advisory before public disclosure.
