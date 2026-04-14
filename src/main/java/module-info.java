import org.jspecify.annotations.NullMarked;

@NullMarked
module io.github.ralfspoeth.greyson {
    requires io.github.ralfspoeth.basix;
    requires static org.jspecify;
    exports io.github.ralfspoeth.json;
    exports io.github.ralfspoeth.json.io;
    exports io.github.ralfspoeth.json.query;
    exports io.github.ralfspoeth.json.data;
}