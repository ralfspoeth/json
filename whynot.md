# Why not Jackson or GSON?

## Intro to Jackson and GSON

Both the Jackson and GSON libraries
serialize and deserialize between JSON documents
and Java objects, with a lot of magic and the need for customizations
by the clients of these libraries.

Let
```java title="Line.java"
    record Line(int no, String name, BigDecimal amount, double percentage) {}
```
represent a line item in an account statement.
It makes perfect sense to infer the numerical representation from the target type:
```java
    String json = """
        {
            "no": 1,
            "name": "Thingy",
            "amount": 123.45,
            "percentage": 23.79
        }""";
```
The `json` string can be easily parsed into a `String` to `String` map
like this
```java
    Map<String, String> mappedJson = Map.of(
        "no", "1",
        "name", "Thingy",
        "amount", "123.45",
        "percentage", "23.79"
    );
```
which can then be mapped into a `Line` instance
```java
    var line = new Line(
        Integer.parseInt(mappedJson.get("no")),
        mappedJson.get("name"),
        new BigDecimal(mappedJson.get("amount")),
        Double.parseDouble(mappedJson.get("percentage"))
    );
```
As simple as it looks, there are many potential problems with this approach.
All parsing methods (`parseInt`, `parseDouble`, the `BigDecimal` constructor) throw
a `NullPointerException`
if the members `no`, `amount`, or `percentage` respectively are not present, plus
`NumberFormatException`s and others when the representation in the JSON 
does not match the target type.

The Jackson way to do this looks like
```java
    var line = new ObjectMapper().readValue(json, Line.class);
```
and the GSON way
```java
    var line = new Gson().fromJson(json, Line.class);
```
Both approaches are deceptively simple. Yet as soon as 