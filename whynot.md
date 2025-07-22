# Why not Jackson or GSON?

## Intro to Jackson and GSON

Both the Jackson and GSON libraries
serialize and deserialize between JSON documents
and Java objects, with some magic and some explicit options to be provided
by the clients of these libraries.

Let

    record Line(int no, String name, BigDecimal amount, double percentage) {}

represent a line item in an account statement.
It makes perfect sense to infer the numerical representation from the target type:

    String json = """
        {
            "no": 1,
            "name": "Thingy",
            "amount": 123.45,
            "percentage":23.79
        }""";

The `json` string can be easily parsed into a `String` to `String` map
like this

    Map<String, String> mappedJson = Map.of(
        "no", "1",
        "name", "Thingy",
        "amount", "123.45",
        "percentage", "23.79"
    );

which can then be mapped into a `Line` instance like this

    Line line = new Line(
        Integer.parseInt(mappedJson.get("no")),
        mappedJson.get("name"),
        new BigDecimal(mappedJson.get("amount")),
        Double.parseDouble(mappedJson.get("percentage"))
    );

As simple as it looks, there are many potential problems with this approach.
All parsing methods (`parseInt`, `parseDouble`, the `BigDecimal` constructor) throw
a `NullPointerException`
if the members `no`, `amount`, or `percentage` respectively are not present.

The Jackson databind way looks like

    var line = new ObjectMapper().readValue(json, Line.class);

and the GSON way

    var line = new Gson().fromJson(json, Line.class);

Both approaches are deceptively simple. 