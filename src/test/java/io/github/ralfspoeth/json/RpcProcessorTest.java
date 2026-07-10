package io.github.ralfspoeth.json;

import io.github.ralfspoeth.json.data.*;
import io.github.ralfspoeth.json.io.JsonParseException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;

import static io.github.ralfspoeth.json.data.Builder.objectBuilder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Implements and tests a compliant
 * <a href="https://www.jsonrpc.org/specification">JSON-RPC 2.0</a> processor
 * built on top of {@link Greyson} and the mutable {@link Builder} API.
 * The test cases are taken from section 7 ("Examples") of the specification.
 */
class RpcProcessorTest {

    /**
     * A JSON-RPC 2.0 server-side processor.
     * <p>
     * The incoming request is read into a {@link Builder} which is then
     * transformed <em>in place</em> into the response: the {@code jsonrpc}
     * and {@code id} members of a valid request are retained, {@code method}
     * and {@code params} are removed, and either {@code result} or
     * {@code error} is added.
     * <p>
     * The business function receives the method name and the {@code params}
     * value ({@link JsonNull#INSTANCE} if absent) and returns the result as
     * a {@link JsonValue}, never {@code null} &mdash; use {@link JsonNull#INSTANCE}
     * for a null result. Exceptions map to
     * spec error codes: {@link NoSuchElementException}/{@link UnsupportedOperationException}
     * &rarr; -32601 (method not found), {@link IllegalArgumentException}
     * &rarr; -32602 (invalid params), any other {@link RuntimeException}
     * &rarr; -32603 (internal error).
     */
    static class RpcProcessor {
        private static final String VERSION = "2.0";

        private final BiFunction<String, JsonValue, JsonValue> businessFunction;

        RpcProcessor(BiFunction<String, JsonValue, JsonValue> businessFunction) {
            this.businessFunction = Objects.requireNonNull(businessFunction);
        }

        /**
         * Read a single request or a batch of requests from {@code in},
         * process it, and write the response(s) to {@code out}.
         * Writes nothing at all if the input consists of notifications only.
         */
        void process(Reader in, Writer out) throws IOException {
            final Builder<? extends JsonValue> builder;
            try {
                var read = Greyson.readBuilder(in);
                if (read.isEmpty()) { // empty input
                    Greyson.writeValue(out, error(JsonNull.INSTANCE, -32700, "Parse error").build());
                    return;
                }
                builder = read.get();
            } catch (JsonParseException e) {
                Greyson.writeValue(out, error(JsonNull.INSTANCE, -32700, "Parse error").build());
                return;
            }
            switch (builder) {
                // single request
                case Builder.ObjectBuilder ob -> {
                    var response = respond(ob);
                    if (response != null) {
                        Greyson.writeValue(out, response.build());
                    }
                }
                // batch
                case Builder.ArrayBuilder ab -> {
                    if (ab.isEmpty()) { // rpc call with an empty array is invalid
                        Greyson.writeValue(out, error(JsonNull.INSTANCE, -32600, "Invalid Request").build());
                        return;
                    }
                    for(var li = ab.data().listIterator(); li.hasNext(); ) {
                        var response = respond(li.next());
                        if(response != null) {
                            li.set(response);
                        } else {
                            li.remove();
                        }
                    }

                    if (!ab.data().isEmpty()) { // anything left after dropping notifications?
                        Greyson.writeBuilder(out, ab);
                    }
                }
                // a top-level basic value is not a valid request
                case Builder.BasicBuilder ignored ->
                        Greyson.writeValue(out, error(JsonNull.INSTANCE, -32600, "Invalid Request").build());
            }
        }

        /**
         * Process a single request builder in place and return the response
         * builder, or {@code null} for notifications.
         */
        private Builder.@Nullable ObjectBuilder respond(Builder<? extends JsonValue> element) {
            if (!(element instanceof Builder.ObjectBuilder ob)) {
                return error(JsonNull.INSTANCE, -32600, "Invalid Request");
            }
            var request = ob.build(); // immutable snapshot for validation
            if (!isValidRequest(request)) {
                var id = request.get("id").filter(RpcProcessor::isValidId).orElse(JsonNull.INSTANCE);
                return error(id, -32600, "Invalid Request");
            }
            var method = request.get("method").flatMap(JsonValue::string).orElseThrow();
            var params = request.get("params").orElse(JsonNull.INSTANCE);
            if (!request.members().containsKey("id")) { // notification: invoke, never respond
                try {
                    businessFunction.apply(method, params);
                } catch (RuntimeException ignored) {
                    // errors of notifications are never reported
                }
                return null;
            }
            // in-place transformation of the request into the response:
            // jsonrpc and id survive, method and params are removed,
            // result or error is added
            ob.remove("method").remove("params");
            try {
                ob.put("result", businessFunction.apply(method, params));
            } catch (NoSuchElementException | UnsupportedOperationException e) {
                ob.put("error", errorObject(-32601, "Method not found"));
            } catch (IllegalArgumentException e) {
                ob.put("error", errorObject(-32602, "Invalid params"));
            } catch (RuntimeException e) {
                ob.put("error", errorObject(-32603, "Internal error"));
            }
            return ob;
        }

        private static boolean isValidRequest(JsonObject request) {
            return request.get("jsonrpc").flatMap(JsonValue::string).filter(VERSION::equals).isPresent()
                    && request.get("method").flatMap(JsonValue::string).isPresent()
                    && request.get("params").map(p -> p instanceof Aggregate).orElse(true)
                    && (!request.members().containsKey("id") || isValidId(request.members().get("id")));
        }

        private static boolean isValidId(JsonValue id) {
            return id instanceof JsonString || id instanceof JsonNumber || id instanceof JsonNull;
        }

        private static Builder.ObjectBuilder error(JsonValue id, int code, String message) {
            return objectBuilder()
                    .putBasic("jsonrpc", VERSION)
                    .put("error", errorObject(code, message))
                    .put("id", id);
        }

        private static JsonObject errorObject(int code, String message) {
            return objectBuilder()
                    .putBasic("code", code)
                    .putBasic("message", message)
                    .build();
        }
    }

    // ---------------------------------------------------------------
    // test fixture: the business function used by most tests
    // ---------------------------------------------------------------

    private static final BiFunction<String, JsonValue, JsonValue> CALCULATOR = (method, params) -> switch (method) {
        case "subtract" -> Basic.of(switch (params) {
            case JsonArray a when a.elements().size() == 2 -> decimal(a, 0).subtract(decimal(a, 1));
            case JsonObject o -> decimal(o, "minuend").subtract(decimal(o, "subtrahend"));
            default -> throw new IllegalArgumentException("expected [a, b] or {minuend, subtrahend}");
        });
        case "sum" -> Basic.of(params.elements().stream()
                .map(e -> e.decimal().orElseThrow(IllegalArgumentException::new))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        case "get_data" -> JsonValue.of(List.of("hello", 5));
        case "notify_hello", "notify_sum", "update" -> JsonNull.INSTANCE;
        case "fail" -> throw new IllegalStateException("boom");
        default -> throw new NoSuchElementException(method);
    };

    private static BigDecimal decimal(JsonArray a, int index) {
        return a.get(index).flatMap(JsonValue::decimal).orElseThrow(IllegalArgumentException::new);
    }

    private static BigDecimal decimal(JsonObject o, String name) {
        return o.get(name).flatMap(JsonValue::decimal).orElseThrow(IllegalArgumentException::new);
    }

    private static String process(String requestJson) throws IOException {
        return process(requestJson, CALCULATOR);
    }

    private static String process(String requestJson, BiFunction<String, JsonValue, JsonValue> f) throws IOException {
        var out = new StringWriter();
        new RpcProcessor(f).process(Reader.of(requestJson), out);
        return out.toString();
    }

    private static void assertJsonEquals(String expected, String actual) throws IOException {
        assertEquals(
                Greyson.readValue(Reader.of(expected)).orElseThrow(),
                Greyson.readValue(Reader.of(actual)).orElseThrow()
        );
    }

    // ---------------------------------------------------------------
    // test cases, following section 7 of the JSON-RPC 2.0 specification
    // ---------------------------------------------------------------

    @Test
    void rpcCallWithPositionalParameters() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"result\": 19, \"id\": 1}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": [42, 23], \"id\": 1}")
        );
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"result\": -19, \"id\": 2}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": [23, 42], \"id\": 2}")
        );
    }

    @Test
    void rpcCallWithNamedParameters() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"result\": 19, \"id\": 3}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": {\"subtrahend\": 23, \"minuend\": 42}, \"id\": 3}")
        );
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"result\": 19, \"id\": 4}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": {\"minuend\": 42, \"subtrahend\": 23}, \"id\": 4}")
        );
    }

    @Test
    void notificationProducesNoResponse() throws IOException {
        var invocations = new ArrayList<String>();
        BiFunction<String, JsonValue, JsonValue> recording = (m, p) -> {
            invocations.add(m);
            return CALCULATOR.apply(m, p);
        };
        assertEquals("", process("{\"jsonrpc\": \"2.0\", \"method\": \"update\", \"params\": [1,2,3,4,5]}", recording));
        assertEquals("", process("{\"jsonrpc\": \"2.0\", \"method\": \"foobar\"}", recording)); // even when the method fails
        assertEquals(List.of("update", "foobar"), invocations);
    }

    @Test
    void rpcCallOfNonExistentMethod() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32601, \"message\": \"Method not found\"}, \"id\": \"1\"}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"foobar\", \"id\": \"1\"}")
        );
    }

    @Test
    void rpcCallWithInvalidJson() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32700, \"message\": \"Parse error\"}, \"id\": null}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"foobar, \"params\": \"bar\", \"baz]")
        );
    }

    @Test
    void rpcCallWithInvalidRequestObject() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32600, \"message\": \"Invalid Request\"}, \"id\": null}",
                process("{\"jsonrpc\": \"2.0\", \"method\": 1, \"params\": \"bar\"}")
        );
    }

    @Test
    void rpcCallBatchInvalidJson() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32700, \"message\": \"Parse error\"}, \"id\": null}",
                process("""
                        [
                          {"jsonrpc": "2.0", "method": "sum", "params": [1,2,4], "id": "1"},
                          {"jsonrpc": "2.0", "method"
                        ]""")
        );
    }

    @Test
    void rpcCallWithEmptyArray() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32600, \"message\": \"Invalid Request\"}, \"id\": null}",
                process("[]")
        );
    }

    @Test
    void rpcCallWithInvalidBatchOfOne() throws IOException {
        assertJsonEquals(
                "[{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32600, \"message\": \"Invalid Request\"}, \"id\": null}]",
                process("[1]")
        );
    }

    @Test
    void rpcCallWithInvalidBatch() throws IOException {
        assertJsonEquals(
                """
                [
                  {"jsonrpc": "2.0", "error": {"code": -32600, "message": "Invalid Request"}, "id": null},
                  {"jsonrpc": "2.0", "error": {"code": -32600, "message": "Invalid Request"}, "id": null},
                  {"jsonrpc": "2.0", "error": {"code": -32600, "message": "Invalid Request"}, "id": null}
                ]""",
                process("[1,2,3]")
        );
    }

    @Test
    void rpcCallBatch() throws IOException {
        assertJsonEquals(
                """
                [
                  {"jsonrpc": "2.0", "result": 7, "id": "1"},
                  {"jsonrpc": "2.0", "result": 19, "id": "2"},
                  {"jsonrpc": "2.0", "error": {"code": -32600, "message": "Invalid Request"}, "id": null},
                  {"jsonrpc": "2.0", "error": {"code": -32601, "message": "Method not found"}, "id": "5"},
                  {"jsonrpc": "2.0", "result": ["hello", 5], "id": "9"}
                ]""",
                process("""
                        [
                          {"jsonrpc": "2.0", "method": "sum", "params": [1,2,4], "id": "1"},
                          {"jsonrpc": "2.0", "method": "notify_hello", "params": [7]},
                          {"jsonrpc": "2.0", "method": "subtract", "params": [42,23], "id": "2"},
                          {"foo": "boo"},
                          {"jsonrpc": "2.0", "method": "foo.get", "params": {"name": "myself"}, "id": "5"},
                          {"jsonrpc": "2.0", "method": "get_data", "id": "9"}
                        ]""")
        );
    }

    @Test
    void rpcCallBatchAllNotifications() throws IOException {
        assertEquals("", process("""
                [
                  {"jsonrpc": "2.0", "method": "notify_sum", "params": [1,2,4]},
                  {"jsonrpc": "2.0", "method": "notify_hello", "params": [7]}
                ]"""));
    }

    @Test
    void rpcCallWithInvalidParams() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32602, \"message\": \"Invalid params\"}, \"id\": 1}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": [\"a\", \"b\"], \"id\": 1}")
        );
    }

    @Test
    void rpcCallWithInternalError() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32603, \"message\": \"Internal error\"}, \"id\": 1}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"fail\", \"params\": [], \"id\": 1}")
        );
    }

    @Test
    void rpcCallWithNullId() throws IOException {
        // id: null is discouraged but not a notification; the id is echoed back
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"result\": 19, \"id\": null}",
                process("{\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": [42, 23], \"id\": null}")
        );
    }

    @Test
    void rpcCallWithoutJsonRpcVersionIsInvalid() throws IOException {
        assertJsonEquals(
                "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32600, \"message\": \"Invalid Request\"}, \"id\": 1}",
                process("{\"method\": \"subtract\", \"params\": [42, 23], \"id\": 1}")
        );
    }
}
