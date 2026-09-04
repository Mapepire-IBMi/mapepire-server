# mapepire-server

Server-side component of Mapepire: a WebSocket gateway that gives cloud-native clients access to Db2 for
IBM i. It runs on IBM i, authenticates callers against user profiles, and brokers JSON requests to JDBC
over jt400.

## Java coding conventions — mandatory

**Read [CODING_CONVENTIONS_JAVA.md](CODING_CONVENTIONS_JAVA.md) before editing any `.java` file in this
repository, and treat every rule in it as a hard requirement.** It is written for both humans and AI
assistants, and it ends with a checklist and a set of verification commands to run before reporting a
Java change complete.

The rules most often gotten wrong, in brief — but do not rely on this summary in place of the document:

- **LF line endings, always.** No `\r` anywhere. If a file arrives with CRLF, convert the whole file;
  this is the one sanctioned exception to "no drive-by reformatting."
- **4 spaces, never tabs.** Lines under 320 characters. Format with the in-repo
  `eclipse_formatter.xml` profile where your editor supports it.
- **Naming carries scope:** `m_` instance fields, `s_` mutable statics, `UPPER_SNAKE_CASE` constants,
  `_` method parameters, unprefixed locals.
- **`final` on everything that is not reassigned**; braces on every single-statement block.
- **Java 8 only** — no `var`, no `List.of()`, no records, no text blocks.
- **Never log credentials.** No password, passphrase, token, or credentialed JDBC URL reaches a tracer,
  log, console, or exception message.
- **The client-facing JSON is a public contract.** Never rename or remove a reply field or request `id`.
- **Never hand-edit generated sources.** `Version.java` comes from `Version.java.tpl` via Maven.
- **Flag any change to resource acquisition or closing for human review**, and never convert existing
  code to try-with-resources as an unprompted cleanup — resource lifetimes here are not always local.

Where this document and the conventions document conflict, the conventions document wins. Where either
conflicts with a pull request review comment, **the review comment wins.**

## Build

```bash
mvn -q compile      # fast check; run before reporting a Java change complete
mvn install         # full build (default goal), produces the jar-with-dependencies
```

The jt400 dependency is `provided` — it resolves from `/QIBM/ProdData/OS400/jt400/lib/jt400.jar` on IBM i.
Comment out the `<scope>` in [pom.xml](pom.xml) for local off-platform development.

There is no automated test suite. Validate changes by compiling and, where behaviour is affected, by
exercising the protocol against a running server.

## Layout

- [src/main/java/com/github/ibm/mapepire/](src/main/java/com/github/ibm/mapepire/) — server core:
  `MapepireServer` (entry point), `DataStreamProcessor` (request dispatch), `SystemConnection`,
  `ClientRequest` (handler base class), `Tracer` (per-connection diagnostics).
- [requests/](src/main/java/com/github/ibm/mapepire/requests/) — one class per request type, dispatched
  by the `id` field in the incoming JSON.
- [authfile/](src/main/java/com/github/ibm/mapepire/authfile/) — IP/user connection rules.
- [certstuff/](src/main/java/com/github/ibm/mapepire/certstuff/) — TLS certificate handling.
