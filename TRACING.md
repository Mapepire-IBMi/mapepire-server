# Tracing and Debuggability

This document describes how diagnostics work in the Mapepire server. Nearly all of the
machinery lives in [Tracer.java](src/main/java/com/github/ibm/mapepire/Tracer.java); the
rest is wiring in [MapepireServer.java](src/main/java/com/github/ibm/mapepire/MapepireServer.java),
[SetConfig.java](src/main/java/com/github/ibm/mapepire/requests/SetConfig.java), and
[GetTraceData.java](src/main/java/com/github/ibm/mapepire/requests/GetTraceData.java).

There are two independent tracing systems in play:

1. **Mapepire's own trace facility** — one tracer per client connection, plus one global tracer.
2. **IBM Java Toolbox (JTOpen) tracing** — enabled entirely through the Toolbox's own standard
   mechanisms, not through Mapepire.

## 1. Per-connection trace facilities

Every client connection gets its own `Tracer` instance, created through `Tracer.getNew()`
(see [SystemConnection.java:53](src/main/java/com/github/ibm/mapepire/SystemConnection.java#L53)
and [DbWebsocketClient.java:23](src/main/java/com/github/ibm/mapepire/ws/DbWebsocketClient.java#L23)).
This keeps one client's diagnostics completely separate from every other client's. Each tracer
is stamped with a connection ID (a monotonically increasing number handed out by
`s_connectionIdGenerator`), which is also what shows up in the request layer via
`ClientRequest.getConnectionId()`.

### Destinations

A connection tracer writes to one of the destinations in `Tracer.Dest`:

| Destination | Behavior |
| ----------- | -------- |
| `IN_MEM` | **Default.** A bounded, insertion-ordered in-memory buffer (`InMemCache`) holding the most recent 100 entries. Once full, the oldest entry is discarded. |
| `FILE` | Entries are appended, as HTML, to a server-side file. |
| `DEV_NULL_OR_STDERR` | For a connection tracer, entries are simply dropped. (For the global tracer this means stderr — see below.) |

When `FILE` is selected, the trace file is created lazily on first write by `Tracer.getFile()`:

- Preferred location is `/QOpenSys/QIBM/UserData/AI/db_server/logs`, if it exists and is writable.
- If it does not exist and the server is running as `QUSER`, the directory is created and
  `chmod 600` is applied before the file is created inside it.
- Otherwise the file lands in the JVM's default temp directory.
- File names look like `vscode-<yyyy-MM-dd.HH.mm.ss.SSS>-<pseudo-pid>-<random>.html`. The
  pseudo-PID is a per-JVM-run random value (Java 8 cannot portably obtain a real PID), so all
  files from a single server run share it and can be picked out of the log directory together.
- If the file cannot be opened for any reason, the tracer silently falls back to `IN_MEM` so that
  tracing never takes down a connection.

Trace files are self-contained HTML documents: entries are timestamped, labeled with their event
type, and color-coded (errors in `tomato`, warnings in `orange`, datastreams in dark slate).

### Trace levels

`Tracer.TraceLevel` controls which event types are recorded. The default for a connection tracer
is `INPUT_AND_ERRORS`.

| Level | Records |
| ----- | ------- |
| `OFF` | Nothing |
| `ON` | Everything except datastream events |
| `ERRORS` | Errors only |
| `DATASTREAM` | Everything, including inbound and outbound datastreams |
| `INPUT_AND_ERRORS` | Inbound datastreams and errors |

Event types are `INFO`, `WARN`, `ERR`, `DATASTREAM_IN`, and `DATASTREAM_OUT`; the mapping is
implemented in `EventType.isLoggedAt()`.

One behavior worth knowing: if the traced payload is a `Throwable` and the server is *not* running
on IBM i, the stack trace is printed to stderr regardless of trace level. This is a developer
convenience for off-platform testing.

### Controlling and retrieving a connection's trace

Clients drive their own tracer over the wire:

- **`setconfig`** accepts `tracelevel` (any of the levels above) and `tracedest` (`file` or
  `in_mem`), and echoes back the resulting `tracelevel` and `tracedest`. For `FILE`, the echoed
  destination is the absolute path of the trace file.
- **`gettracedata`** returns the connection's accumulated trace as a single HTML string in the
  `tracedata` field. This works for both destinations: in-memory entries are rendered on demand,
  and a file-backed trace is read back off disk. The request is forced synchronous so that it
  captures a coherent snapshot.

See the request tables in [README.md](README.md) for the full data stream reference.

## 2. The global tracer

There is exactly one global tracer, reachable through `Tracer.getGlobalTracer()` (or the
`Tracer.globalInfo()` / `globalWarn()` / `globalErr()` / `globalDatastreamIn()` /
`globalDatastreamOut()` convenience methods). It handles server-wide messages that do not belong
to any single client: startup and shutdown, user profile swapping, keystore selection, unsecure-mode
warnings, and top-level failures.

Its defaults differ from a connection tracer: level `ON` and destination `DEV_NULL_OR_STDERR`,
with the connection ID `global`.

### With native capabilities installed

When the IBM i native service program is loaded — `/qsys.lib/qaie.lib/qaijvantv.srvpgm` by default,
overridable with `-Dmapepire.natives=<path>` (see
[SystemNativeUtils.java](src/main/java/com/ibm/ibmi_util/SystemNativeUtils.java)) — the global
tracer writes its entries **into the IBM i job log** of the server job. Startup also calls
`SystemNativeUtils.enableJobLogging(JobLogEnabling.FOUR_ZERO_SECLVL_JOBEND)` so the job log is
produced at job end at message level 4/0 with second-level text.

As an additional benefit of native mode, the server redirects `System.err` into the global tracer
(while still echoing to the original stderr), so stray stack traces from libraries end up in the
job log alongside Mapepire's own messages.

This makes the job log the primary debugging artifact on a properly installed IBM i system:
`DSPJOBLOG` or `WRKJOB` against the server job gives the full server-level narrative.

### Without native capabilities

When the native library is not loaded — off-platform development, or an installation missing the
service program — the global tracer falls back to exactly the same conventions as the
connection-level trace facilities: the same `TraceLevel` filtering, the same `Dest` options, the
same HTML rendering, and the same `getRawData()` retrieval. In its default
`DEV_NULL_OR_STDERR` destination it prints entries to stderr; set to `IN_MEM` or `FILE` it behaves
like any other tracer.

### Setting the global trace level

The global trace level is set with command-line switches at startup:

| Switch | Effect |
| ------ | ------ |
| `--traceErrors` | `TraceLevel.ERRORS` |
| `--traceOn` | `TraceLevel.ON` |
| `--traceDs` | `TraceLevel.DATASTREAM` |

## 3. Connection trace changes are recorded globally

Changing a connection's tracing does not happen silently. When a client alters its trace
configuration via `setconfig`, the change is written to the **global** tracer, including the
connection ID, the connection's client special registers, and the resolved destination — for
example:

```
Connection id 7 (...CSRs...) logging to /QOpenSys/QIBM/UserData/AI/db_server/logs/vscode-....html
```

This means a server administrator reading the job log (or the global trace) can always tell which
connections have tracing turned on and where each of them is writing, without having to inspect
individual connections. It also provides the pointer needed to go find a connection's trace file
on disk.

> Note: today this global record is emitted when the *destination* is changed
> ([SetConfig.java:29-31](src/main/java/com/github/ibm/mapepire/requests/SetConfig.java#L29-L31));
> a `tracelevel`-only change is applied to the connection tracer but is not currently echoed to the
> global tracer.

## 4. IBM Java Toolbox (JTOpen) tracing

Mapepire does **not** define its own controls for Toolbox tracing. It is enabled through the
standard mechanisms documented in the IBM guide:

**<https://www.ibm.com/support/pages/all-one-toolbox-tracing-guide>**

That is, the usual `com.ibm.as400.access.Trace` system properties and API calls — for example
`-Dcom.ibm.as400.access.Trace.category=...` and `-Dcom.ibm.as400.access.Trace.file=...`, or
programmatic `Trace.setTraceOn(...)` — apply unchanged. Refer to that guide for the authoritative
list of categories, properties, and file options.

### Startup reporting

Because Toolbox tracing is configured outside of Mapepire, the server reports its state on startup
so there is never any doubt about what was actually in effect. The global trace facility logs three
lines during initialization
([MapepireServer.java:68-70](src/main/java/com/github/ibm/mapepire/MapepireServer.java#L68-L70)):

1. `Tracer.getJtOpenStatusString()` — whether Toolbox tracing and Toolbox JDBC tracing are on.
2. `Tracer.getJtOpenComponentStatusString()` — the on/off state of each individual component:
   `CONVERSION`, `DATASTREAM`, `DIAGNOSTIC`, `ERROR`, `INFO`, `PCML`, `PROXY`, `THREAD`.
3. `Tracer.getJtOpenFileString()` — the Toolbox trace file name, if one is configured.

On a native-enabled IBM i installation, these three lines land in the server job's job log, so the
Toolbox trace configuration is captured right alongside everything else the server reports.

## Quick reference: where do I look?

| Symptom | Where to look |
| ------- | ------------- |
| Server won't start, certificate/keystore issues, user swap problems | Global tracer — job log on IBM i, stderr otherwise |
| One client's requests misbehave | That connection's tracer: `setconfig` with `tracelevel=datastream`, then `gettracedata` |
| Need a durable artifact to send to support | `setconfig` with `tracedest=file`; the reply and the global trace both give the absolute path |
| Toolbox/JDBC-level protocol problems | Enable Toolbox tracing per the guide above; confirm via the startup lines in the global trace |
| Which connections have tracing on? | Global tracer — it records every trace destination change |
