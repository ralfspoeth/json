# Motivation

Having read a number of articles around data oriented
programming (cf. [Brian Goetz, Data-Oriented Programming](
https://www.infoq.com/articles/data-oriented-programming-java/),
note the section "Example: JSON" in particular)
where the JSON format has been of special interest,
and being quite dissatisfied with the usage experience
of popular JSON libraries like [GSON](https://github.com/google/gson)
or [Jackson](https://github.com/FasterXML/jackson)
the motivation to implement an alternative library was high enough
to start the project.

The JSON type hierarchy is very simple and strict
enough to apply the algebraic data types introduced
through `sealed` classes and interfaces (union types)
and `record`s (product types)
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

Learning a tiny little bit of
[Clojure](https://clojure.org/about/rationale)
taught me another series of important things,
the most striking being Rich Hickey's keynote about
[The Value of Values](https://www.youtube.com/watch?v=-6BsiVyC1kM)
at the Jaxconf 2012 in San Francisco.
Treating values as immutable data changes the mental
model of programming at least if you're coming
from the object-oriented world.

Yet reading a potentially large file of JSON text
and returning a single immutable instance of some
type is an interesting tasks which requires some
intermediate mutable objects hopefully hidden beneath
the facade of the parser. We finally managed to use
mutable builders throughout the parsing phase and
to return immutable instances in the end.