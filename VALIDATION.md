# Validation status

## Completed before packaging

- Parsed `pom.xml` as well-formed XML.
- Compiled every Java source file using JDK 17 against local API stubs covering
  the JavaFX and SpiderMonkey symbols used by the project. The project itself
  targets JDK 27. This validates Java syntax, package structure, and internal
  source references.
- Verified separate server and client `main()` entry points.
- Verified all SimEthereal-specific assumptions are confined to
  `com.example.collab3d.simethereal`.
- Verified the Maven profiles and local run scripts reference the correct main
  classes.

## Not executable in the generation environment

The generation container did not provide Maven or outbound artifact-repository
access. It therefore could not establish these runtime facts:

1. SimEthereal 1.8.0 binary compatibility with SpiderMonkey 3.9.0-stable.
2. The exact runtime constructor signatures used by SimEthereal 1.8.0.
3. The exact `SharedObject` position/rotation accessors exposed at runtime.
4. Actual TCP/UDP loopback behavior with two JavaFX clients.

The adapter is defensive about these version-sensitive points and emits detailed
diagnostics when a compatible constructor, method, or pose accessor is absent.

## First verification commands on the target machine

```text
mvn -U -DskipTests validate
mvn -U -DskipTests dependency:tree \
  -Dincludes=com.simsilica:*,org.jmonkeyengine:*
mvn -U -DskipTests clean package
```

Then run one server and two clients using the commands in `README.md`.
