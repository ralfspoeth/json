# Greyson

A small, opinionated JSON library for Java.

```xml
<dependency>
    <groupId>io.github.ralfspoeth</groupId>
    <artifactId>json</artifactId>
    <version>1.3.0</version>
</dependency>
```

No annotations. No reflection. No code generation. Greyson parses JSON
into immutable algebraic data types and gets out of your way. The whole
library, with dependencies, fits in under 100 kB.

If your project already pulls in Jackson, you don't need Greyson. But if
you've ever wanted a JSON library that you can read end-to-end in an
afternoon, that doesn't ask you to decorate your records, and that
treats `Optional` and `Stream` as first-class citizens rather than
afterthoughts — this is that library.

---

## At a glance

```java
record Car(String make, int year) {}

Car car = Greyson.readValue(reader)
    .map(v -> new Car(
        v.get("make").flatMap(JsonValue::string).orElseThrow(),
        v.get("year").flatMap(JsonValue::intValue).orElseThrow()
    ))
    .orElseThrow();
```

Three things to notice:

- `readValue` returns `Optional<JsonValue>`. There are no nulls anywhere
  in the API.
- `JsonValue` is a sealed interface (`Basic | Aggregate`), so the
  compiler can prove your switches are exhaustive.
- Field extraction is just `Optional` chaining. No annotations on
  `Car`, no `TypeReference`, no `ObjectMapper` configuration.

Writing is the mirror image:

```java
JsonObject jo = Aggregate.objectBuilder()
    .putBasic("make", car.make())
    .putBasic("year", car.year())
    .build();

Greyson.writeValue(writer, jo);
```

---

## The duality: `JsonValue` ↔ `Builder`

Every `JsonValue` is immutable. Every `Builder` is its mutable twin.
The two are wired together so that round-tripping is always identity:

```java
JsonValue v = ...;
assert v.equals(v.builder().build());
```

This makes one-shot edits painless. To stamp a timestamp onto an
incoming object:

```java
Greyson.readBuilder(src)
    .filter(Builder.ObjectBuilder.class::isInstance)
    .map(Builder.ObjectBuilder.class::cast)
    .map(ob -> ob.putBasic("ts", Instant.now().toString()))
    .ifPresent(ob -> Greyson.writeBuilder(target, ob));
```

To wrap any incoming payload in an envelope:

```java
Greyson.readBuilder(src)
    .map(b -> objectBuilder()
        .putBasic("ts", Instant.now().toString())
        .put("msg", b))
    .ifPresent(ob -> Greyson.writeBuilder(target, ob));
```

Use `JsonValue` when you're mapping into your domain. Use `Builder`
when you're rewriting JSON in place.

---

## Real-world resilience

JSON in the wild is messy. Greyson treats single objects and arrays of
objects identically, ignores unknown keys without complaint, and lets
you mark fields mandatory or optional one at a time:

```java
record UserProfile(String id, List<String> tags, double balance) {}

static List<UserProfile> fromJson(Reader rdr) throws IOException {
    return Greyson.readValue(rdr).stream()
        .flatMap(Selector.all())                            // single object or array — both work
        .map(v -> new UserProfile(
            Pointer.self().member("id").stringValue(v).orElseThrow(),
            Pointer.self().member("tags").elements(v).stream()
                .flatMap(t -> t.string().stream())
                .toList(),
            Pointer.self().member("balance").doubleValue(v).orElse(0.0)
        ))
        .toList();
}
```

The same code accepts all of these without a configuration change:

```json
{"id": "p1", "tags": ["one", "two"]}
```

```json
[
    {"id": "p1", "tags": ["one", "two"]},
    {"id": "p2"},
    {"id": "p3", "balance": 100}
]
```

```json
{"id": "p4", "misspelled": "ignored", "balance": -500}
```

---

## Query API

Two abstractions, with a clear division of labor.

**`Selector`** is a function from `JsonValue` to `Stream<JsonValue>`.
Use it inside `Stream::flatMap` to pick out *a set of* values. Three
flavors ship in the box:

- `Selector.all()` — flattens an array, or yields the value itself
- `Selector.range(min, max)` — slices an array
- `Selector.regex(pattern)` — picks object members by key

Selectors stack:

```java
Greyson.readValue(src).stream()
    .flatMap(Selector.all())
    .flatMap(Selector.regex("a[0-9]*"))
    .flatMap(Selector.range(0, 2))
    .map(...);
```

**`Pointer`** is a function from `JsonValue` to `Optional<JsonValue>`.
Use it inside `Optional::flatMap` to navigate to *one* value. Build by
hand, or parse a slash-separated path:

```java
var p = Pointer.self().member("a").member("b").member("c");
var q = Pointer.parse("a/b/c");          // equivalent
```

Path syntax: `name` matches an object member, `0`, `1`, … index into an
array, `#regex` filters object keys.

```java
JsonValue v = Greyson.readValue(src).orElseThrow();
boolean b = Pointer.parse("a/b/c").booleanValue(v).orElseThrow();
```

---

## Data model

```
JsonValue                       sealed
├── Basic                       sealed
│   ├── JsonBoolean             record(boolean)
│   ├── JsonNumber              record(BigDecimal)
│   ├── JsonString              record(String)
│   └── JsonNull                record()
└── Aggregate                   sealed
    ├── JsonArray               record(List<JsonValue>)
    └── JsonObject              record(Map<String, JsonValue>)
```

A few design choices worth calling out:

- **`BigDecimal`, not `double`.** Numeric precision is preserved on
  round trips. `JsonValue::intValue`, `::longValue`, `::doubleValue`
  give you the conversions when you want them.
- **Defensive copies in canonical constructors.** `JsonArray` and
  `JsonObject` copy their inputs unless they're already immutable
  (`List.of`, `Map.of`). Once constructed, the whole tree is
  effectively immutable.
- **No nulls in the API.** Every method that could return "no value"
  returns `Optional`, an empty `List`, or an empty `Map`. `null` is
  tolerated as input wherever it makes sense.
- **Aggregates are functions.** `JsonObject implements Function<String,
  JsonValue>` and `JsonArray implements IntFunction<JsonValue>`, so you
  can pass them straight into stream pipelines.

---

## Reading and writing

The `Greyson` class is the entry point. Four methods, that's it.

```java
Optional<JsonValue>                      Greyson.readValue(Reader)
Optional<Builder<? extends JsonValue>>   Greyson.readBuilder(Reader)
void                                     Greyson.writeValue(Writer, JsonValue)
void                                     Greyson.writeBuilder(Writer, Builder<?>)
```

`readValue` throws `IOException` from the underlying reader. The write
methods rethrow as `UncheckedIOException` so you can use them inside
fluent chains.

To filter at parse time — say, reject anything that isn't a JSON object
with a required key set:

```java
return Greyson.readValue(src)
    .filter(JsonObject.class::isInstance)
    .filter(jv -> jv.members().keySet().containsAll(Set.of("year", "make")))
    .map(...)
    .orElseThrow();
```

---

## JPMS

If you're using the Java module system, Greyson's module name is
`io.github.ralfspoeth.greyson`:

```java
module your.module {
    requires io.github.ralfspoeth.greyson;
}
```

It exports four packages:

- `io.github.ralfspoeth.json` — the `Greyson` entry point
- `io.github.ralfspoeth.json.data` — `JsonValue` and friends
- `io.github.ralfspoeth.json.io` — `JsonReader`, `JsonWriter`, `JsonParseException`
- `io.github.ralfspoeth.json.query` — `Pointer`, `Selector`, `Queries`

---

## Conformance

Greyson's parser is exercised against the
[nst JSON Test Suite](https://github.com/nst/JSONTestSuite). Every
`y_*` (must accept) case parses; every `n_*` (must reject) case
rejects; the `i_*` (implementation-defined) cases are documented in
the test resources.

Greyson is not the fastest JSON library on the JVM. Jackson and Gson
use bespoke streaming parsers, intern strings, and skip allocation in
the hot path. Greyson allocates an algebraic data type for every node.
If your bottleneck is JSON parsing throughput, use Jackson. If it
isn't, the simplicity is worth the trade.

---

## What's new in 1.3

Version 1.3 incorporates changes with the help of Claude.

- `README.md` has been rewritten
- `Lexer` and therefore the `JsonReader` have gained some speed-up

---

## License

MIT. Copyright 2025, 2026 Ralf Spöth.
