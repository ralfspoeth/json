# Strategic Considerations

## The Hierarchy

### Sealing the Implementation

The first attempt can be easily copied from
the sources cited above. Let's define a sealed
a `json` package with an `Value` interface as

    package json;
    sealed interface Value permits ...;

and provide implementations very much like

    package json;
    final class Boolean implements Value {...}
    final class Number implements Value {...}
    final class Null implements Value {...}
    final class String implements Value {...}
    final class Array implements Value {...}
    final class Object implements Value {...}

The problem is that while possible almost
all the names collide with class names in the
core package `java.lang`; once we consider modeling
the `String` class as `record` with a single
component of class `java.lang.String` things
start to get clumsy. We therefore decided to
prefix the class names with `Json` or `JSON`.

While `JSON` is clearly closer to the JSON specification,
it's more challenging to read than `Json`; since
following the spec was not so much a goal as
the ease of use we decided to go with `Json` instead
of `JSON` as the prefix for the concrete types.

At the top of the hierarchy we then had

    public sealed interface JsonValue {}

All implementations must be `final` or `non-sealed`
in order to comply with the contract for sealed
interfaces; since we don't design for further
inheritance we will implement `final` classes only.

### Records—Always?

The next most tempting take on the design of implementation
classes is records:

    record JsonNumber(BigDecimal value) implements JsonValue {...}
    record JsonString(String value) implements JsonValue {...}
    record JsonBoolean(boolean value) implements JsonValue {...}
    record JsonNull() implements JsonValue {...}
    record JsonObject(Map<String, JsonValue> value) implements JsonValue {}
    record JsonArray(List<JsonValue> value) implements JsonValue {}

So it's a no-brainer... or not?
We think that a `boolean` should rather be an `enum`, as in

    enum JsonBoolean implements JsonValue {
        TRUE, FALSE
    }

and `Null` a singleton:

    class JsonNull implements JsonValue {
        public static final JsonNull INSTANCE = new JsonNull();
        private JsonNull(){} // prevent instantiation
    }

### Distinction between Literal and Aggregate Values

There are a number of reasons to separate literal from aggregate values
when modeling JSON data. We found two separate interfaces useful:

    sealed interface JsonValue permits Basic, Aggregate {...}
    sealed interface Basic extends JsonValue permits JsonNull, JsonBoolean, JsonNumber, JsonString {...}
    sealed interface Aggregate extends JsonValue permits JsonArray, JsonObject {...}

The aggregate types should provide builders for their construction.
We want to keep the data immutable for __all__ elements.


## Querying and Converting Data

### Traversal of Structures

Consider this not utterly complex example of
a data structure which resembles some personal data
in a CRM or similar system:

    {
        "id": 12341234,
        "ssn": "123-45-678",
        "addresses": [
            {"address": "10 Downing Street", 
             "city":    "London",
             "code": "SW1A 2AA"
             "country": {"ISO": "GB", "name":"UK"},
             "type": "work"},
            {"address": "Hauptstrasse 1234",
             "city": "Frankfurt",
             "country": {"ISO": "DE", "name": ""},
             "type": "home"}],
        "name": {"first": "John", "last": "Doe"}
    }

The representation in Java after parsing is this

    var person = new JsonObject(Map.of(
        "id", Basic.of(12341234),
        "ssn", new JsonString("123-45-678"),
        "addresses", new JsonArray(List.of(
            new JsonObject(Map.of(
                "address", "10 Downing Street",
                "city", "London",
                "code", "SW1A 2AA", 
                "country", new JsonObject(Map.of(
                    "ISO", "GB",
                    "name", "UK"
                )),
                "type", "work"
            )),
            new JsonObject(Map.of(
                "address": "Hauptstrasse 1234",
                "city": "Frankfurt",
                "country": new JsonObject(Map.of(
                    "ISO", "DE",
                    "name": ""
                )),
                "type": "home"
            ))
        )),
        "name", new JsonObject(Map.of(
            "first": "John",
            "last": "Doe"
        ))
    ));

Nice... or isn't it? Well... Let's see. First of all, the Java use site construction
of the person is lengthier than the JSON representation-one of the reasons why
JSON is so attractive.

We want to have the work address of John Doe.
The standard approach would be

    record WorkAddress(String city, String address) {}

    var wa = person.members()
        .get("addresses") // a JSON array
        .stream()
        .filter(e -> e instanceof JsonObject(m) && "work".equals(m.get("type"))) // that with type 'work'
        .map(e -> e instanceof JsonObject(m) ? new WorkAddress(m.get("city"), m.get("address")))
        .findFirst()
        .orElseThrow();

Instead, we'd rather do something like

    var wa = new WorkAddress(
        MAGIC.get("addresses[type=work]", "address"),
        MAGIC.get("addresses[type=work]", "city")
    );

or even better

    var wa = MAGIC.convert(WorkAdress.class, person, "addresses[type=work]");

But MAGIC doesn't work in practice when things are becoming more complex.
Consider this sealed hierarchy

    sealed interface I permits A, B{int x();}
    record A(int x, double d) implements I {}
    record B(int x, boolean b) implements I {}

and then

    record R(String s, I i) {}

and a JSON text

    {"s":"a string", "i":{"x":5}}

Both `new R("a string", new A(5, 1d))` and `new R("a string", new B(5, true)`
are possible conversions, but which one shall we use?

Take this example:

    {"t":"2025-05-05"}

Given

    record R(LocalDate t){}

we can easily convert the text into an instance of R using
the `parse` method of `LocalDate`

    var element = (JsonObject)JsonReader.readJsonValue(text);
    var r = new R(LocalDate.parse(element.members().get("t").toString()));

We can reflectively get the record components and the static
methode `parse` which takes a string and returns a `LocalDate`, so 
we should be able to implement the magic here.
We found in experiments that things get challenging when
there is more than one static instance creation method
or constructor
in a given class for a single 
argument of the same type, as is the case for `BigDecimal`.
These do not necessarily produce identical instances, so it matters
which of the methods we select.

