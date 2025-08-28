import org.jspecify.annotations.NullMarked;

@NullMarked
module io.github.ralfspoeth.json {
    requires io.github.ralfspoeth.basix;
    requires org.jspecify;
    exports io.github.ralfspoeth.json;
    exports io.github.ralfspoeth.json.io;
    exports io.github.ralfspoeth.json.query;
}