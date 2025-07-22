# JSON Numbers

The JSON pattern defines decimal values.
Since JSON originates in the JavaScript world
where the only numerical data type is a binary `double`
we opted for that very type in JSON numbers in version 1.0.x and 1.1.x.

Then, we reconsidered our choice: is `double` really the best
format to choose? What are the options in the Java world?


## Using `double`s


