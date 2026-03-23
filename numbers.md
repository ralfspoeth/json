# JSON Numbers

The JSON grammar allows for numbers that are beyond the scope of IEEE754 doubles.
JavaScript uses binary doubles as its only numerical data type. Since
most JSON data that is being passed around originates from and is consumed
by web applications written in JavaScript,
we used to represent numbers as doubles up until version 1.1.x.

**Douglas Crockford**, the founder of JSON, noted that his deliberate decision
__not__ to version the specification to promote maximum interoperability
and stability. Since RFC 4627, numbers outside the range precisely representable
as IEEE 754 double-precision floating-point numbers have been allowed, but
warned against in later specifications.
Somewhat contradictory, Douglas Crockford had described JSON to be a subset of
JavaScript.

Java's **BigDecimal** allows us to represent integral and floating point as well
as decimal numbers without the risk of losing precision or scale in both
the generation and consumption of numerical data between Java and other languages.

## Treat all numbers as `double`s?

This interpretation fits perfectly to the JavaScript interpretation
of numbers—with all its downsides, most notably that seemingly 
simple numbers like 0.1 cannot be represented in a binary floating point
bit structure. `Double.parseDouble` works with the JSON representation of numbers.

## Use `BigDecimal` allover the place?

Java's `BigDecimal` type parses any legal number representation 
in a JSON document. Instances can be easily converted into
`int`, `long`, or `double` values.

The downsides are that at least for the time being,
there is no means in the Java type system to enforce these numbers to be non-null, 
and that `BigDecimal`s likely consume more memory and require more indirections
than `double`s, thus placing a performance burden onto 
the implementation.

## Let the User make their Choices?

An option that we considered was to defer the conversion of the
source into a number to the use site, such that the `JsonNumber`
is rather
```java
    record JsonNumber(String source) {
        // maybe
        int intValue() {
            return Integer.parseInt(source);
        }
        double doubleValue() {
            return Double.parseDouble(source);
        }
        // ... others
    }
```
We see two problems here: in order _not_ to fail in these
`intValue` and similar methods we need to check the syntax of the
source string, eventually leading to multiple parses of the same string.
Secondly, we cannot determine in advance whether the data can be parsed
into an int without loss of precision or scale.

Compare this with
```java
    record JsonNumber(BigDecimal value) {}
    // ...
    var num = new JsonNumber(BigDecimal.TEN);
    var i = num.intValueExact(); // throws if not lossless
```
which es both more concise, has better arithmetic support at the potential
cost of unnecessary parsing, memory allocation and indirections.

## Conclusion

Since `BigDecimal`-if guaranteed not to be `null`-provides us with 
any conversion option that we need, we go with it.