package io.github.ralfspoeth.json;

import java.util.*;

import static io.github.ralfspoeth.basix.fn.Predicates.in;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

/**
 * Implements the builder pattern for both aggregate types.
 * The builders implement a fluent API; any mutation operation
 * will by default return the builder itself.
 * Builders can be considered the mutable counterpart to their
 * immutable aggregate cousins.
 *
 * @param <T> the type of the object built by the builder.
 */
public sealed interface Builder<T extends JsonValue> {

    /**
     * Instantiate the mutable value holder.
     *
     * @param value a value, may not be {@code null}
     */
    static JsonValueBuilder valueBuilder(JsonValue value) {
        return new JsonValueBuilder(value);
    }

    /**
     * Instantiate an empty builder for {@code JsonObject}s.
     */
    static JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    /**
     * Instantiate a builder for {@code JsonObject}s with the initial
     * name-value pairs copied from given {@code JsonObject}.
     * Same as {@code objectBuilder(from.members())}
     */
    static JsonObjectBuilder objectBuilder(JsonObject from) {
        return objectBuilder().insert(from);
    }

    /**
     * Instantiate an empty builder for {@code JsonArray}s.
     */
    static JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }

    /**
     * Instantiate a builder with an initial set of elements copied
     * from the given {@code JsonArray}.
     * Same as {@code arrayBuilder(ja.elements())}
     *
     * @param ja a {@code JsonArray}, may not be {@code null}
     */
    static JsonArrayBuilder arrayBuilder(JsonArray ja) {
        return arrayBuilder().addAll(ja.elements());
    }

    int size();

    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Build the immutable aggregate instance.
     */
    T build();

    /**
     * Builder implementation for {@code JsonArray}s.
     */
    final class JsonArrayBuilder implements Builder<JsonArray> {
        private JsonArrayBuilder() {}

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public JsonArray build() {
            return new JsonArray(data
                    .stream()
                    .map(Builder::build)
                    .map(JsonValue.class::cast)
                    .toList()
            );
        }

        private final List<Builder<?>> data = new ArrayList<>();

        public JsonArrayBuilder add(JsonValue elem) {
            data.add(switch (elem) {
                case JsonObject jo -> objectBuilder(jo);
                case JsonArray ja -> arrayBuilder(ja);
                default -> valueBuilder(elem);
            });
            return this;
        }

        public JsonArrayBuilder add(Builder<?> b) {
            data.add(b);
            return this;
        }

        /**
         * Add a basic value to the builder.
         *
         * @param o an object, may not be {@code null}, will be passed to {@link Basic#of(Object)}
         * @return {@code this}
         */
        public JsonArrayBuilder addBasic(Object o) {
            return add(Basic.of(o));
        }

        /**
         * Remove the element at the given index.
         *
         * @param index the index
         * @return {@code this}
         * @throws IndexOutOfBoundsException if the index is out of bounds
         */
        public JsonArrayBuilder remove(int index) {
            data.remove(index);
            return this;
        }

        /**
         * Empty out the builder.
         *
         * @return {@code this}
         */
        public JsonArrayBuilder clear() {
            data.clear();
            return this;
        }

        /**
         * Add all elements of the given collection to this builder.
         *
         * @param elements the elements to be appended at the end of the list, may not be {@code null}
         * @return {@code this}
         */
        public JsonArrayBuilder addAll(Collection<JsonValue> elements) {
            elements.forEach(this::add);
            return this;
        }
    }

    /**
     * Builder implementation for {@code JsonObject}s.
     */
    final class JsonObjectBuilder implements Builder<JsonObject> {

        // prevent instantiation for user code
        private JsonObjectBuilder() {}

        // the data which will be turned into the
        // members map in the JsonObject instance later.
        private final Map<String, Builder<?>> data = new HashMap<>();

        /**
         * Add a name-value pair to the builder.
         *
         * @param name  the name
         * @param value the value
         * @return {@code this}
         */
        public JsonObjectBuilder put(String name, JsonValue value) {
            data.put(requireNonNull(name), switch (requireNonNull(value)) {
                case JsonObject jo -> objectBuilder(jo);
                case JsonArray ja -> arrayBuilder(ja);
                default -> valueBuilder(value);
            });
            return this;
        }

        /**
         * Add a name-builder pair.
         *
         * @param name the name
         * @param b    the builder
         * @return {@code this}
         */
        public JsonObjectBuilder put(String name, Builder<?> b) {
            data.put(name, b);
            return this;
        }

        /**
         * Add a named basic value to this builder.
         *
         * @param name the name
         * @param o    an object, may not be {@code null}, will be passed to {@link Basic#of(Object)}
         * @return {@code this}
         */
        public JsonObjectBuilder putBasic(String name, Object o) {
            return put(name, Basic.of(o));
        }

        /**
         * Updates the current mapping of names to values to the values given.
         *
         * @param map a map of names to values, may not be {@code null}.
         * @return {@code this}
         */
        public JsonObjectBuilder update(Map<String, ? extends JsonValue> map) {
            map.entrySet().stream()
                    .filter(in(data.keySet(), Map.Entry::getKey))
                    .forEach(e -> put(e.getKey(), e.getValue()));
            return this;
        }

        /**
         * Same as {@code update(o.members())}.
         */
        public JsonObjectBuilder update(JsonObject o) {
            return update(o.members());
        }

        /**
         * Merge, that is insert or update, the given map into the current map
         * of name-value pairs.
         *
         * @param map a map of names to values, may not be {@code null}.
         * @return {@code this}
         */
        public JsonObjectBuilder merge(Map<String, ? extends JsonValue> map) {
            map.forEach((key, value) -> data.put(key, switch (value) {
                case JsonObject jo -> objectBuilder(jo);
                case JsonArray ja -> arrayBuilder(ja);
                default -> valueBuilder(value);
            }));
            return this;
        }

        /**
         * Same as {@code merge(o.members())}.
         */
        public JsonObjectBuilder merge(JsonObject o) {
            return merge(o.members());
        }

        /**
         * Insert entries into the current map of name-value pairs.
         *
         * @param map
         * @return
         */
        public JsonObjectBuilder insert(Map<String, ? extends JsonValue> map) {
            map.entrySet()
                    .stream()
                    .filter(not(in(data.keySet(), Map.Entry::getKey)))
                    .forEach(e -> put(e.getKey(), e.getValue()));
            return this;
        }

        public JsonObjectBuilder insert(JsonObject o) {
            return insert(o.members());
        }

        /**
         * Remove a single name-value pair identified by the given key.
         *
         * @param key the key.
         * @return {@code this}
         */
        public JsonObjectBuilder remove(String key) {
            data.remove(key);
            return this;
        }

        /**
         * Remove all name-value pairs with a name in the given set of keys.
         *
         * @param keys the keys to be removed, may not be {@code null}
         * @returnl {@code this}
         */
        public JsonObjectBuilder removeAll(Collection<String> keys) {
            keys.forEach(data::remove);
            return this;
        }

        @Override
        public JsonObject build() {
            return new JsonObject(data.entrySet()
                    .stream()
                    .collect(toMap(Map.Entry::getKey, e -> e.getValue().build()))
            );
        }

        @Override
        public int size() {
            return data.size();
        }
    }

    /**
     * Builder implementation for {@code JsonValue}s.
     */
    final class JsonValueBuilder implements Builder<JsonValue> {
        private JsonValue value; // never null

        private JsonValueBuilder(JsonValue value) {
            setValue(value);
        }

        public JsonValueBuilder set(JsonValue value) {
            setValue(value);
            return this;
        }

        private void setValue(JsonValue value) {
            this.value = requireNonNull(value);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public JsonValue build() {
            return value;
        }
    }
}
