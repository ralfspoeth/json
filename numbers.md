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

## Treat all numbers as `double`s

This interpretation fits perfectly to the JavaScript interpretation
of numbers—with all its downsides, most notably that seemingly 
simple numbers like 0.1 cannot be represented in a binary floating point
bit structure.

## Use `BigDecimal` allover the place

Java's `BigDecimal` type parses any legal number representation 
in a JSON document. Instances can be easily converted into
`int` or `double` values. The downsides are that at least for the time being, 
there is no means in the Java type system to enforce these numbers to be non-null, 
and that `BigDecimal`s tend to consume more memory and require more indirections
than `double`s, thus placing a performance burden onto 
the implementation.

## Let the User make their Choices?

Both GSON and Jackson provide customizations as to the data type
mapping from JSON to Java objects and vice versa. While
both libraries implement a lot of magic to allow for (GSON)

    var result = new Gson().fromJson(text, Result.class);

or (Jackson)

    var result = new ObjectMapper().readValue(test, Result.class);

the Greyson library intentionally does not support this type
of direct JSON-to-Java conversion.

## Conclusion

Since `BigDecimal`-if guaranteed not to be `null`-provides us with 
any conversion option that we need, we go with it.