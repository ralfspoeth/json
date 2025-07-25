package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;

public class Validation {
    private Validation() {
    }

    private static final System.Logger LOGGER = System.getLogger(Validation.class.getName());

    private static boolean throwIllegal(Supplier<String> msg) {
        throw new IllegalArgumentException(msg.get());
    }

    public static Predicate<JsonValue> matchesOrThrow(Predicate<JsonValue> predicate, Supplier<String> msg) {
        return jv -> switch (jv) {
            case JsonValue v when predicate.test(v) -> true;
            case null, default -> throwIllegal(msg);
        };
    }

    public static Predicate<JsonValue> matchesOrLog(Predicate<JsonValue> predicate, Supplier<String> msg) {
        return jv -> {
            boolean valid = predicate.test(jv);
            if (!valid) {
                LOGGER.log(System.Logger.Level.INFO, msg);
            }
            return valid;
        };
    }

    public static Predicate<JsonValue> required(Collection<String> keys) {
        return jv -> switch (jv) {
            case JsonObject(var members) -> members.keySet().containsAll(keys);
            case null, default -> false;
        };
    }


    public static Predicate<JsonValue> structural(Map<String, Predicate<JsonValue>> structure) {
        return jv -> switch (jv) {
            case JsonObject(var members) -> members.entrySet().stream()
                    .filter(e -> structure.containsKey(e.getKey()))
                    .allMatch(e -> structure.get(e.getKey()).test(e.getValue()));
            case null, default -> false;
        };
    }

    public static Predicate<JsonValue> structuralValues(JsonObject jo) {
        return structural(
                jo.members().entrySet().stream().collect(toMap(
                        Map.Entry::getKey,
                        e -> switch (e.getValue()) {
                            case Basic<?> b -> b;
                            case JsonObject o -> structuralValues(o);
                            case JsonArray a -> structuralValues(a);
                        }
                ))
        );
    }

    public static Predicate<JsonValue> structuralValues(JsonArray array) {
        return structural(array.elements().stream().map(e -> switch (e) {
            case Basic<?> b -> b;
            case JsonObject o -> structuralValues(o);
            case JsonArray a -> structuralValues(a);
        }).toList());
    }

    public static Predicate<JsonValue> structuralTypes(JsonArray array) {
        return structural(array.stream().map(v -> switch (v) {
            case JsonBoolean ignored -> is(JsonBoolean.class);
            case Basic<?> b -> is(b.getClass());
            case JsonArray a -> structuralTypes(a);
            case JsonObject o -> structuralTypes(o);
        }).toList());
    }

    public static Predicate<JsonValue> structuralTypes(JsonObject object) {
        return structural(object.members().entrySet().stream().collect(toMap(
                Map.Entry::getKey,
                e -> switch (e.getValue()) {
                    case JsonBoolean ignored -> is(JsonBoolean.class);
                    case Basic<?> b -> is(b.getClass());
                    case JsonArray a -> structuralTypes(a);
                    case JsonObject o -> structuralTypes(o);
                }))
        );
    }

    public static Predicate<JsonValue> required(Map<String, Predicate<JsonValue>> structure) {
        return required(structure.keySet()).and(structural(structure));
    }

    public static Predicate<JsonValue> all(Predicate<JsonValue> predicate) {
        return jv -> switch (jv) {
            case JsonArray(var elements) -> elements.stream().allMatch(predicate);
            case null, default -> false;
        };
    }

    public static Predicate<JsonValue> any(Predicate<JsonValue> predicate) {
        return jv -> switch (jv) {
            case JsonArray(var elements) -> elements.stream().anyMatch(predicate);
            case null, default -> false;
        };
    }

    public static Predicate<JsonValue> structural(List<Predicate<JsonValue>> structure) {
        return jv -> switch (jv) {
            case JsonArray(var elements) when elements.size() == structure.size() -> {
                for (int i = 0; i < elements.size(); i++) {
                    if (!structure.get(i).test(elements.get(i))) yield false;
                }
                yield true;
            }
            case null, default -> false;
        };
    }

    public static Predicate<JsonValue> regex(String regex) {
        return jv -> switch (jv) {
            case JsonString(String s) -> s.matches(regex);
            case null, default -> false;
        };
    }

    public static Predicate<JsonValue> is(Class<? extends JsonValue> type) {
        return type::isInstance;
    }
}
