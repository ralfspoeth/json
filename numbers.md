# JSON Numbers

The JSON pattern defines decimal values.
Since JSON originates in the JavaScript world
where the only numerical data type is a binary `double`
we opted for that very type in JSON numbers in version 1.0.x and 1.1.x.

Then, we reconsidered our choice: is `double` really the best
format to choose? What are the options in the Java world?

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
than `double`s, thus placing a somewhat tiny to large performance burden onto 
the implementation.

## Let the Users make their Choice!

So that's the best option, right? 