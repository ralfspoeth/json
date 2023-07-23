# RS JSON Implementation
This project implements a JSON parser which operates around immutable data structures for 
the JSON elements.

My name is Ralf Spöth, I am the author of this 
library. It has been implemented in my spare time.
Please note that I am not a fulltime developer or
Java professional of any kind.
Nevertheless, I am grateful for feedback; may this 
code be useful for you.

## Motivation

I've read a number of articles around data oriented
programming (cf. [Brian Goetz, Data-Oriented Programming](
https://www.infoq.com/articles/data-oriented-programming-java/), 
note the section "Example: JSON" in particular)
where the JSON format has been of special interest.
The JSON type hierarchy is very simple and strict
enough to apply the algabraic data types introduced
through `sealed` classes (union types)
and interfaces and `record`s (product types)
efficiently. These ideas struck with me, so I
started to look around for a parser which 
returns an immutable `JsonElement` from a stream
of characters. 

I then found [JEP 198: Light-Weight JSON API](https://bugs.openjdk.org/browse/JDK-8046390)
which names immutable data types and a builder-style 
API as part if its goals. The immutable type hierarchy,
the builder-style API plus the implicitly required
parser which returns immutable instances of the JSON
type hierarchy finally lead to this experiment.

Learning a tiny little bit of  [Clojure](https://clojure.org/about/rationale)
taught me another series of important things,
the most striking being Rich Hickey's keynote
about [The Value of Values](https://www.youtube.com/watch?v=-6BsiVyC1kM).
at the Jaxconf 2012 in San Francisco.
Treating values as immutable things changes the mental
model of programming at least if you're coming 
from the object-oriented world.

Yet reading a potentially large file of JSON text
and returning a single immutable instance of some
type is an interesting tasks which requires some 
intermediate mutable objects hopefully hidden beneath
the facade of the parser. We finally managed to use 
mutable builders throughout the parsing phase and
to return immutable instances in the end.

## JSON

[RFC 7159](https://datatracker.ietf.org/doc/html/rfc7159)
specifies the JSON data interchange format which 
has become the _lingua franca_ for RESTful webservices.
JSON serializes structured data in a human-readable 
text format. It supports four primitive types
(strings, `double` numbers, booleans and `null`) and 
two aggregates types (arrays of any kind
and objects which are basically
maps of names (strings) and values of any kind).

### Example:

    [{
        "name": "Gaius",
        "age": 41,
        "pro": false,
        "publications": ["De bello gallico"],
        "
    }, {
        "name": "Cicero",
        "senator": true,
        "wars": null
    }]

This text represents an array of two objects; the
out form reads `[a, b]` where `a` and `b` are the 
objects.
Braces `{` and `}` enclose these two objects
with name-value-pairs separated by commas, like 
`{ nvp1, nvp2, ...}`. 
Each name-value-pair consists of a name of type
string and a value of any other data type mentioned
above. The name-value-pairs make the properties or 
attributes of an object.
The `name` property of the first object is associated
with the string `"Gaius"`, the `pro` attribute 
with the value `false`. The value of the `publications`
attribute is an array of a single string valued
`"De bello gallico"`.

Wikipedia has more on [JSON](https://en.wikipedia.org/wiki/JSON).

### Remarks

The objects do not expose some notation of a type
or class. Two objects are considered equal of their 
attributes match. Arrays may contain any combination
of instances, including both primitive and structured 
types as in `[null, true, false, 1, {"x":5}, [2, 3, 4]]`

# Modelling the data in Java

## First Attempt

The first attempt can be easily copied from 
the sources cited above. Let's define a sealed 
interface

    package json;
    sealed interface Element permits ...;

and provide implementations very much like

    package json;
    final class Boolean implements Element{...}
    final class Number implements Element{...}
    final class Null implements Element{...}
    final class String implements Element{...}
    final class Array implements Element{...}
    final class Object implements Element{...}

The problem is that while possible almost
all the names collide with class names in the 
core package `java.lang`; once we consider modelling
the `String` class as `record` with single
component of class `java.lang.String` things 
start to get clumsy. We therefore decided to 
prefix the class names with `Json` or `JSON`. 

While `JSON` is clearly closer to the JSON specification,
it's more difficult to read than `Json`; since
following the spec was not so much a goal as 
the ease of use we decided to go with `Json` instead 
of `JSON` as the prefix.

At the top of the hierarchy we then had

    public sealed interface JsonElement {}

All implementations must be `final` or `non-sealed`
in order to comply with the contract for sealed
interfaces; since we don't design for further 
inheritance we will implement `final` classes only.


## Modelling `Boolean` as Enum

The two only instance of type `Boolean` are `true` and 
`false` in JSON notation; we model them as an enum
because it is implicitly final and the behaviour
of its `equals` and `hashCode` methods comes without
any surprises.

    public enum JsonBoolean implements JsonElement {
        TRUE, FALSE
    }

## Modell `Null` as Singleton

As with booleans we decided to implement the `Null`
instances as copies of the only possible instance.
The singleton pattern which goes like 

    final class Singleton {
        static final Singleton INSTANCE = new Singleton();
        private Singleton(){}
    }

and translates into

    public final class JsonNull implements JsonElement {
        private JsonNull() {}
        public static final JsonNull INSTANCE = new JsonNull(); 
    }

## Model `String` as Record of String

There is no technical need to wrap strings into 
records with a single component of type string.
But in order to make strings part of the sealed
hierarchy we have to do so:

    public record JsonString(String value) implements JsonElement {
    }

This comes in handy once we deal with aggregate
types like arrays of `JsonElement` rather than 
arrays of `JsonElement` UNION `String` which we 
cannot express in Java.

## Model `Number` as Record of Double

With the same reasoning we model numbers like this:

    public record JsonNumber(double value) implements JsonElement {
    }

Note that JavaScript doesn't cater for differences
between numerical data types -- which is enormously
limiting, and that we use the primitive Java type
because `null` values or not acceptable either way.

## Model `Array` as Record of an Immutable List

As with strings we need to wrap the array in some
container - a final class or a record - plus
we want to make sure the contents is immutable:

    public record JsonArray(List<JsonElement> elements) implements JsonElement {
        public JsonArray {
            elements = List.copyOf(elements);
        }
    }

The canonical constructor is overridden such that 
is instantiates a copy of the list provided; 
that method is clever enough NOT to copy the list
parameter if it can be sure that that parameter
is already an immutable instance.
This method also makes sure not actual `null` instance
is passed in with the list of elements.
(`JsonNull`s are acceptable of course.)

## Model `Object` as Record of an Immutable Map

    public record JsonObject(Map<String, JsonElement> members) implements JsonElement {
        public JsonObject {
            members = Map.copyOf(members);
        }
    }

The same is true for `JsonObject`s.

