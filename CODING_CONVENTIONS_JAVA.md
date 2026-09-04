# Java Coding Conventions

This document defines the Java coding conventions for the **mapepire-server** project. It is written for
two audiences:

- **Humans** contributing to the project, who should read it once and then follow it by habit.
- **AI coding assistants**, which should treat every rule below as a hard requirement when generating,
  editing, or reviewing Java in this repository.

Where this document is silent, follow the style of the code immediately surrounding your change. Read a
few of the nearby files before writing anything: the rules below describe the intent, and the code you
are working in shows what that intent looks like in practice. No single file in this repository is a
canonical reference implementation, and none should be treated as one — consult the neighbourhood, not a
designated exemplar.

> **Note on scope:** These rules are about *readability and consistency*, not correctness. A change
> that follows every rule here but breaks the build is still a bad change. Never sacrifice clarity to
> satisfy a formatting rule — if a rule genuinely makes a particular piece of code worse, say so in the
> pull request rather than silently ignoring it.

> **Note on existing code:** Not every file in this repository conforms to the conventions below. Some
> code predates them. Where existing code conflicts with this document, **this document wins** — do not
> treat a non-conforming file as precedent for new code.

> **Note on precedence:** Where this document conflicts with a comment on a pull request, **the review
> comment wins.** A reviewer is looking at one specific change with context this document cannot have,
> and their call on that change is final. If the same review comment keeps recurring, that is a signal to
> update this document — not to re-argue the rule on every pull request.

---

## 1. Files and Encoding

### 1.1 Line endings must be Unix-style (LF)

**All source files must use Unix-style line endings (`LF`, `\n`). Windows-style `CRLF` line endings are
not permitted anywhere in the source tree.**

This matters because the server is built and deployed onto IBM i, and the build tooling, shell scripts,
and diff tooling all assume LF. A file that gets committed with CRLF shows up as a whole-file change in
every subsequent diff, which destroys the usefulness of `git blame` and code review.

Practical guidance:

- Configure your editor to use LF for this repository. In VS Code: `"files.eol": "\n"`.
- If you are on Windows, make sure Git is **not** rewriting line endings on checkout. Either set
  `core.autocrlf=false`, or rely on a `.gitattributes` entry that pins these files to LF.
- **AI assistants:** when writing or rewriting a file, emit `\n` only. Never emit `\r\n`. Before
  finishing an edit, verify the file has no `\r` characters.

**Repairing line endings is always in scope.** Section 5 and the checklist in section 9 tell you not to
reformat code you did not otherwise need to touch. Converting `CRLF` to `LF` is an explicit, standing
exception to that rule: if you open a file and find CRLF endings, convert the entire file as part of your
change. There are no grandfathered files, no "this one predates the rule", and no files that are exempt
because the resulting diff is large. A file with CRLF endings is a file to be fixed. Mention the
conversion in the pull request description so the reviewer knows to expect whitespace noise alongside the
substantive change.

### 1.2 Encoding

Source files are **UTF-8**. This is enforced by the Maven build (`project.build.sourceEncoding`).

Prefer plain ASCII in identifiers, comments, and log messages. Non-ASCII characters (emoji, typographic
quotes, arrows) are discouraged in source: they render inconsistently in IBM i terminals and job logs,
and can be mangled by EBCDIC-adjacent tooling.

### 1.3 Java language level

The project compiles against **Java 8** (`<source>8</source>` / `<target>8</target>` in
[pom.xml](pom.xml)). Do not use language or library features introduced after Java 8 — no `var`, no
`List.of()`, no records, no switch expressions, no text blocks. The compiler will usually catch this,
but not always (some newer APIs exist at compile time in a newer JDK and fail only at runtime on the
target JVM).

### 1.4 Generated sources are never edited by hand

Some files in the source tree are generated at build time and overwritten on every build.
[Version.java](src/main/java/com/github/ibm/mapepire/Version.java) is generated from
[Version.java.tpl](Version.java.tpl) by the `maven-replacer-plugin` during the `process-sources` phase.

**Never hand-edit a generated file.** Any change you make to one will be silently discarded by the next
build, and the resulting confusion — a fix that works locally and vanishes in CI — costs far more time
than it saves. If a generated file needs to change, change the template it is generated from.

Before editing an unfamiliar file, check [pom.xml](pom.xml) for a plugin that writes to it.

---

## 2. Whitespace and Layout

### 2.1 Spaces only, never tabs

**Indentation is 4 spaces. Tab characters must not appear in Java source files.**

This applies to continuation lines and alignment as well — if you are lining something up, use spaces.

### 2.2 Line length

**Individual lines should be no longer than 320 characters where it is reasonable to do so.**

320 matches the `lineSplit` setting in the project's formatter profile (§8), so a file formatted with
that profile already satisfies this rule. It is generous on purpose: it is a limit, not a target. Most
lines should be far shorter. The rule exists to catch the pathological cases — a deeply chained builder
expression, or a `String.format()` with a dozen inline arguments — that become unreadable in
side-by-side diffs.

If you are editing without the profile configured, this is a limit you check yourself; see the
verification commands in section 9.

When a line must be broken, break it at a point that makes the structure obvious (before a `.` in a
method chain, after a `,` in an argument list) and indent the continuation.

Occasionally the auto-formatter will make a block *less* readable than hand-formatting — a long
`String.format()` with one argument per line, for example. In that case, and only in that case, wrap
the region in formatter-off markers, as is done in `Tracer.getJtOpenStatusString()`:

```java
//@formatter:off
final String components = String.format("CONVERSION:%B,DATASTREAM:%B,DIAGNOSTIC:%B",
        Trace.isTraceConversionOn(),
        Trace.isTraceDatastreamOn(),
        Trace.isTraceDiagnosticOn()
    );
//@formatter:on
```


Use this sparingly. A file peppered with formatter-off markers is a sign the code needs restructuring,
not more markers.

### 2.3 Braces

Use **Egyptian / K&R braces**: the opening brace goes at the end of the line that opens the block, and
the closing brace goes on its own line.

```java
public void doSomething() {
    if (condition) {
        // ...
    } else {
        // ...
    }
}
```

### 2.4 Braces are required on single-statement blocks

**Every `if`, `else`, `for`, `while`, and `do` body must be wrapped in curly braces `{}`, even when the
body is a single statement and the braces are syntactically unnecessary.**

```java
// Correct
if (null != rule) {
    ret.add(rule);
}

// WRONG - do not do this
if (null != rule)
    ret.add(rule);

// WRONG - do not do this either
if (null != rule) ret.add(rule);
```

The reason is defensive: braceless bodies are where a later "just add one more line" edit silently
changes the control flow. Requiring braces removes the whole class of bug.

---

## 3. Naming

Naming in this codebase is deliberately explicit about a variable's *scope and lifetime*. The prefix
tells the reader, at the point of use, whether they are looking at instance state, static state, a
parameter, or a local — without having to scroll to a declaration.

### 3.1 Summary table

| Kind of identifier                    | Convention                          | Example                          |
| ------------------------------------- | ----------------------------------- | -------------------------------- |
| Class / interface / enum              | `TitleCase`                         | `AuthFile`, `ClientRequest`      |
| Method                                | `camelCase`                         | `getRules()`, `sendReply()`      |
| Instance field (class field)          | `m_camelCase`                       | `m_file`, `m_reqObj`             |
| Non-constant static field             | `s_camelCase`                       | `s_defaultInstance`, `s_isSingleMode` |
| Constant (`static final`)             | `UPPER_SNAKE_CASE`                  | `DEFAULT_SEC_FILE`, `CLIENT_USER`|
| Method parameter                      | `_camelCase`                        | `_lineNumber`, `_reqObj`         |
| Local variable                        | `camelCase` (no prefix)             | `lastMatchingRule`, `ruleType`   |
| Package                               | all lowercase                       | `com.github.ibm.mapepire.requests` |
| Enum constant                         | `UPPER_SNAKE_CASE`                  | `DENY`, `ALLOW`                  |
| Generic type parameter                | single uppercase letter             | `T`, `K`, `V`                    |

### 3.2 Class fields: `m_` prefix

**Instance fields must begin with `m_`, followed by a camelCased name.**

```java
public abstract class ClientRequest implements Runnable {
    private final SystemConnection m_conn;
    private final String m_id;
    private final DataStreamProcessor m_io;
    private final JsonObject m_reqObj;
}
```

### 3.3 Static fields: `s_` prefix (non-constant) or `UPPER_SNAKE_CASE` (constant)

**Non-constant static variables must begin with `s_`, followed by a camelCased name. Constants — that
is, `static final` values — must be named in all uppercase with underscores separating words.**

```java
private static final String DEFAULT_SEC_FILE = "/QOpenSys/etc/mapepire/iprules.conf";            // constant
private static final String DEFAULT_SEC_FILE_SINGLEMODE = "/QOpenSys/etc/mapepire/iprules-single.conf";

private static AuthFile s_defaultInstance = null;                                                // mutable static
```

The distinction is meaningful: `s_` is a warning label. It tells the reader that this value is shared
process-wide *and* can change, which in a multi-connection server is something to think carefully about.
`UPPER_SNAKE_CASE` carries no such warning because a constant is safe to read from anywhere.

A `static final` reference to a *mutable object* (say, a `static final Map`) is a grey area. Name it
`UPPER_SNAKE_CASE` if it is genuinely used as a constant lookup table; name it `s_camelCase` if its
contents change during normal operation.

Note that `s_` means **static**. Do not use it on an instance field, even one that holds a
conceptually-constant value like a compiled `Pattern` — that is an `m_` field, or better, a genuine
`static final` constant.

### 3.4 Method parameters: `_` prefix

**Method parameters must begin with `_`, followed by a camelCased name.**

```java
protected ClientRequest(final DataStreamProcessor _io, final SystemConnection _conn, final JsonObject _reqObj) {
    m_io = _io;
    m_reqObj = _reqObj;
    m_conn = _conn;
}

private AuthRule parseAuthRuleFromLine(final int _lineNumber, final String _line) throws IOException {
    // ...
}
```

This convention has a very practical payoff: **a parameter can never shadow a field.** `_conn` and
`m_conn` are visibly different identifiers, so assignments read unambiguously and there is no need for
`this.` disambiguation (see §4.1).

Prefer descriptive parameter names — `_traceLevel` over `_l`. Some older code uses single-letter
parameter names; that is not a pattern to copy in new code.

### 3.5 Local variables

Local variables take **no prefix** and are plain `camelCase`. The absence of a prefix is itself
information: it means "this lives and dies inside this method."

```java
final Matcher m = s_authRulePattern.matcher(line);
final RuleType ruleType = RuleType.valueOf(m.group(1).toUpperCase());
final String user = m.group(2);
final String ip = m.group(3);
```

### 3.6 Classes and methods

**Classes, interfaces, and enums are `TitleCase`. Methods are `camelCase`.**

Method names begin with a lowercase letter, always — including private helpers. A method named
`DoTheThing()` is indistinguishable from a constructor at the call site, which is exactly the confusion
the convention exists to prevent.

### 3.7 Modifier order, acronyms, and other declaration details

**Modifier order follows the order given in the Java Language Specification:**

```
public protected private abstract default static final transient volatile synchronized native strictfp
```

So `public static final String FOO`, never `static public final String FOO`. This is the order every Java
tool, formatter, and reader expects; an unusual order makes a declaration snag the eye for no reason.

**Acronyms and initialisms are cased as ordinary words.** Write `getDbJob()`, not `getDBJob()`;
`SqlRequest`, not `SQLRequest`; `parseJsonUrl()`, not `parseJSONURL()`. Shouted acronyms become
unreadable the moment two of them collide (`parseJSONURLID`). A handful of existing class names do not
follow this — leave them alone, since renaming a request-handler class is a bigger change than it looks;
just do not add new ones.

**Boolean accessors begin with `is` or `has`:** `isForcedSynchronous()`, `hasResultSet()`. A boolean
method named `resultSet()` reads at the call site like a getter that returns a `ResultSet`.

**Generic type parameters are a single uppercase letter** — `T` for an unconstrained type, `K` and `V`
for a key and value, `E` for an element. A multi-letter type parameter is indistinguishable from a class
name where it is used.

**`@Override` goes on its own line**, directly above the method signature, and is required on every
method that overrides a superclass method *or* implements an interface method. It is not decoration: it
is the compiler check that catches a signature that has silently drifted out of sync with the type it was
meant to override.

---

## 4. Language Usage

### 4.1 Use `this.` only when it is needed

**Do not prefix field accesses with `this.` unless it is actually required.**

Because fields carry an `m_` prefix and parameters carry an `_` prefix, shadowing is impossible, so
`this.` is almost always noise:

```java
// Correct
public AuthFile setRules(final List<AuthRule> _rules) {
    m_rules = _rules;
    return this;
}

// WRONG - the "this." adds nothing
public AuthFile setRules(final List<AuthRule> _rules) {
    this.m_rules = _rules;
    return this;
}
```

`this` on its own is of course fine and often necessary — returning `this` for method chaining, passing
`this` to a collaborator, or comparing identity with `this != other`. The rule is specifically about the
redundant `this.` *member-access* prefix.

### 4.2 Declare `final` wherever possible

**Mark variables `final` whenever they are not reassigned** — fields, parameters, and locals alike.

```java
public void go() throws Exception {
    final String sql = getRequestField("sql").getAsString();
    final int numRows = super.getRequestFieldInt("rows", 1000);
    final Connection jdbcConn = getSystemConnection().getJdbcConnection();
    final Statement stmt = jdbcConn.createStatement();
    final boolean hasRs = stmt.execute(sql);
    // ...
}
```

`final` is documentation that the compiler checks. It tells the reader "this value is settled, you do
not need to scan the rest of the method for a reassignment." In a server handling concurrent connections,
that is a genuinely useful guarantee to make visible.

Prefer `final` fields specifically — a class whose fields are all `final` is trivially safe to share
across threads.

A variable assigned differently in several branches can still be `final`: declare it `final` without an
initializer and assign it exactly once in each branch of the if/else. Do this where it reads cleanly,
but do not contort the code to achieve it.

### 4.3 Constant-first ("Yoda") comparisons

The codebase consistently writes the constant on the **left** of an equality comparison:

```java
if (null != s_defaultInstance) {
    return s_defaultInstance;
}
if (null == lastMatchingRule) {
    return;
}
if (RuleType.DENY == lastMatchingRule.getRuleType()) {
    throw new IOException("Connection refused by security rule at line " + lastMatchingRule.getLineNumber());
}
```

The strongest reason is that for anything other than a primitive comparison, constant-first is genuinely
safer: `"localhost".equalsIgnoreCase(remoteServer)` cannot throw a `NullPointerException`, whereas
`remoteServer.equalsIgnoreCase("localhost")` can. Putting the constant first turns a whole class of
latent NPE into code that simply returns `false`.

Beyond that, it is the prevailing style, and new code should follow it for consistency. The historical
rationale (preventing an accidental `=` in place of `==`) is weaker in Java than in C, but the
consistency is worth preserving, and it does make null checks scan quickly.

### 4.4 Imports

- Use explicit, single-type imports. **Avoid wildcard imports** (`import java.sql.*;`). A few older
  files still use them; do not add more.
- Do not import unused types.
- Keep imports grouped roughly as: `java.*`, then `javax.*`, then third-party, then project packages.
  The formatter will handle ordering within groups.

### 4.5 Exception handling

- Never swallow an exception silently in new code. At minimum, log it through the connection's tracer:
  ```java
  } catch (final Exception _e) {
      getConnection().getTracer().logErr(_e);
  }
  ```
- Prefer the per-connection tracer (`getTracer()` / `getConnection().getTracer()`) over
  `e.printStackTrace()` or `System.err`. Per-connection tracing keeps one client's diagnostics out of
  another client's trace output.
- Catch the narrowest exception type that is actually thrown. Catching `Exception` is acceptable at a
  request boundary, where the whole point is to convert any failure into an error response, but not as a
  general habit.

### 4.6 Never log credentials or secrets

**No password, passphrase, authentication token, or credential-bearing string may ever be written to a
tracer, a log, the console, or an exception message.**

This is the one rule in this document that is a security requirement rather than a style preference. The
server authenticates users against IBM i and holds their credentials in memory; the tracer writes to a
file on disk that is not necessarily readable only by the connecting user. A password that reaches a
trace file has leaked.

Concretely, never pass any of the following to a logging or tracing call:

- A user password or passphrase, in any form, including inside a wrapped exception's message.
- A keystore, truststore, or private-key passphrase.
- A JDBC URL or connection-property map that has credentials embedded in it. Log the host and the user
  profile if you need them; never log the whole URL or the whole property set.

Where an object might carry a secret, log the specific fields you actually need rather than the object.
`toString()` on a connection or configuration object is exactly how credentials end up in logs by
accident. When in doubt, log less.

### 4.7 Close resources with try-with-resources

**Anything that implements `AutoCloseable` — `Connection`, `Statement`, `PreparedStatement`,
`ResultSet`, streams, readers, sockets — is acquired in a try-with-resources block.**

```java
try (final Statement stmt = jdbcConn.createStatement()) {
    // ...
}
```

Try-with-resources is available in Java 8 and closes the resource on every exit path, including the ones
you did not think about. A manual `close()` in a `finally` block is easy to write *almost* correctly, and
a leaked `Statement` or `ResultSet` in a long-lived server connection is a slow leak that only shows up
under load on a customer's system.

The exception is a resource whose lifetime deliberately outlives the method that creates it — a cursor
held open across requests so a later `sqlmore` can fetch from it, for example. That is a legitimate
pattern here, but it is a deliberate design decision: the owning object is then responsible for closing
the resource, and the code should make it obvious where that happens.

> **AI assistants:** resource lifetime in this codebase is not always local, and it is not always
> apparent from the method you are editing. Wrapping a resource in try-with-resources can close a handle
> that a *later* request still needs, turning working code into an intermittent runtime failure that no
> compiler will catch. **Never convert existing resource handling to try-with-resources as an unprompted
> cleanup.** Where you do change how a resource is acquired or closed — including in new code — call it
> out explicitly in your summary and in the pull request, and flag it as requiring human review. Do not
> report such a change as routine.

---

## 5. Member Ordering

**Where practical, order the members of a class as produced by the "Sort Members" source action from the
Red Hat Java extension for VS Code** (`Java: Sort Members`, or right-click, then Source Action, then Sort
Members).

That action orders members by category and then alphabetically within each category, roughly:

1. Static fields
2. Instance fields
3. Constructors
4. Static methods
5. Instance methods
6. Nested types

**This rule is a preference, not a hard requirement.** Two caveats:

- Do not run Sort Members across a whole existing file as a drive-by change. It produces an enormous
  diff that buries the actual change and destroys `git blame` history for the file. Sort members when
  you are creating a new file, or when you are already substantially rewriting one. (Note that the
  line-ending repair described in §1.1 is a deliberate exception to this "no drive-by change" principle,
  and the only one — a large whitespace-only diff is an acceptable price for getting a file onto LF.)
- Where a deliberate grouping communicates more than alphabetical order does — a set of related overloads,
  or a field placed immediately next to the method that owns it — keep the deliberate grouping.

---

## 6. Documentation and Comments

### 6.1 Javadoc

Write Javadoc on public API: public classes, and public or protected methods whose purpose is not
obvious from the signature. Use the standard tag layout, and note that `@param` names include the `_`
prefix, matching the actual parameter name:

```java
/**
 * Verify that the given user is permitted to connect from the given address.
 *
 * @param _user
 *            the user profile requesting the connection
 * @param _ip
 *            the client IP address the connection originated from
 * @throws IOException
 *             if the connection is refused by a security rule, or the rules file cannot be read
 */
public void verify(final String _user, final String _ip) throws IOException {
    // ...
}
```

Do not write Javadoc that merely restates the signature (`/** Gets the name. @return the name */` on
`getName()`). Blank filler Javadoc is worse than none — it costs a reader time and tells them nothing.

### 6.2 Inline comments

Explain **why**, not **what**. The code already says what it does.

```java
// Capture both forms because QQQDBVE param 1 may be VARCHAR or BINARY depending on driver settings
// (especially affected by translate-binary setting)
```

Mark known deficiencies with `// TODO:` and a short description of what needs doing.

Do **not** leave commentary about the editing process in the code — markers like `// FIXED:`,
`// FIX:`, `// NEW:`, `// CHANGED:`, or checkmark emoji describe a moment in the repository's history,
not the code, and they are stale as soon as they are committed. That information belongs in the commit
message. Such comments do exist in places in this repository; they should be removed, not imitated.

---

## 7. Wire Protocol Compatibility

The JSON exchanged with clients is a **public contract**, not an internal detail. Independent client
libraries — mapepire-js, mapepire-python, and others — parse these messages, and they ship and upgrade on
their own schedules. A server change that renames a field breaks every client that has not been updated
in lockstep, on systems whose administrators did not ask for a client upgrade.

Treat the following as breaking changes, not refactors:

- **Renaming or removing a reply field.** The keys passed to `addReplyData(...)` are protocol, not
  variable names. `has_results` cannot become `hasResults` because the Java side would read better that
  way.
- **Renaming or removing a request `id` value** handled by the dispatch switch (`"sql"`, `"prepare_sql"`,
  `"sqlmore"`, and so on).
- **Changing the type or meaning of an existing field** — turning a number into a string, changing what
  `is_done` counts as done, making a previously always-present field conditional.

Additive changes are safe and are the way to evolve the protocol: add a *new* field alongside the old
one, keep populating the old one, and let clients migrate. Reply field names are `lower_snake_case` and
request ids are lowercase; match the existing style when you add one.

If a breaking change is genuinely necessary, it is a coordinated, versioned decision made with the client
maintainers — never a drive-by rename, and never something an AI assistant decides on its own while
cleaning up nearby code.

---

## 8. Formatting Tooling

**Use the formatter profile checked into the repository at [eclipse_formatter.xml](eclipse_formatter.xml)
whenever your editor can be pointed at it.** It is the authoritative profile for this project: it encodes
4-space indentation, spaces instead of tabs, K&R brace placement, the 320-character line split described
in §2.2, and the `@formatter:off` / `@formatter:on` markers.

In VS Code with the Red Hat Java extension, point the formatter at the in-repo profile by setting a
workspace-relative path in [.vscode/settings.json](.vscode/settings.json):

```json
"java.format.settings.url": "eclipse_formatter.xml"
```

In Eclipse, import it under Preferences, then Java, then Code Style, then Formatter, then Import; the
profile is named `service_commander`.

If for some reason you cannot use this profile, do not let a default formatter reflow files wholesale —
configure your editor to format only your selection, and match the surrounding code by hand. An
accidental whole-file reformat in a pull request is very hard to review.

Note that the compiler-compliance settings embedded in the profile say Java 9. They are irrelevant to
formatting and do not override §1.3 — this project is still Java 8.

---

## 9. Checklist for AI Coding Assistants

Before completing any Java edit in this repository, verify all of the following:

**Layout and encoding**

- [ ] File uses **LF** line endings only — no `\r` anywhere. If the file arrived with CRLF, the whole
      file was converted (§1.1) and the conversion is called out in the summary.
- [ ] Indentation is **4 spaces**; there are no tab characters.
- [ ] No line exceeds **320 characters** without good reason.
- [ ] No generated file was hand-edited; template changes were made to the template (§1.4).

**Naming**

- [ ] Instance fields are named `m_camelCase`.
- [ ] Non-constant static fields are named `s_camelCase`.
- [ ] Constants (`static final`) are named `UPPER_SNAKE_CASE`.
- [ ] Method parameters are named `_camelCase`.
- [ ] Classes are `TitleCase`; methods are `camelCase`.
- [ ] Modifiers are in JLS order (`public static final`, not `static public final`).
- [ ] Acronyms are cased as words (`getDbJob`, not `getDBJob`); boolean accessors start with `is`/`has`.
- [ ] `@Override` is present on every overriding and interface-implementing method.

**Language usage**

- [ ] `this.` appears only where it is actually required.
- [ ] Every variable that is never reassigned is declared `final`.
- [ ] Every `if` / `else` / `for` / `while` body is wrapped in `{}`, including single-statement bodies.
- [ ] Only Java 8 language and library features are used.
- [ ] No wildcard imports were added; no unused imports remain.
- [ ] No exception is silently swallowed; errors are logged through the per-connection tracer.
- [ ] **No password, passphrase, token, credentialed JDBC URL, or connection-property map is passed to
      any logging, tracing, or exception-message call** (§4.6).
- [ ] Any change to how a resource is acquired or closed is explicitly flagged for human review, and no
      existing resource handling was converted to try-with-resources as an unprompted cleanup (§4.7).

**Protocol and scope**

- [ ] No reply field name, request `id`, or field type/meaning was renamed, removed, or changed (§7).
- [ ] No process-narration comments (`// FIXED:`, `// NEW:`) were added.
- [ ] The change is scoped to what was asked — no drive-by reformatting or member re-sorting of
      untouched code, with the single exception of line-ending repair.

### 9.1 Verification commands

Several of the items above are mechanically checkable. Run these before reporting a Java change complete,
and report what they actually returned rather than asserting compliance:

```bash
# Must produce no output. There are no exempt files.
grep -rlP '\r'     src --include='*.java'   # CRLF line endings
grep -rlP '\t'     src --include='*.java'   # tab characters
grep -rnP '.{321}' src --include='*.java'   # lines over 320 characters

# Must succeed.
mvn -q compile
```

The equivalent in PowerShell, since much of this project's development happens on Windows:

```powershell
Get-ChildItem -Recurse -File -Filter *.java src | Where-Object {
    $t = [System.IO.File]::ReadAllText($_.FullName)
    ($t -match "`r") -or ($t -match "`t") -or (($t -split "`n" | Where-Object { $_.Length -gt 320 }).Count -gt 0)
} | Select-Object -ExpandProperty FullName
```

A non-empty result from any of these is a defect in the change, not a pre-existing condition to be
excused — see §1.1 on line endings and §2.1 on tabs.
