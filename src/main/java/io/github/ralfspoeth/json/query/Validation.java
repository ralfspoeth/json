package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

public class Validation {

    public record Result(JsonValue value, Predicate<JsonValue> failed, List<Result> details) {
        public Result(JsonValue value, Predicate<JsonValue> failed) {
            this(value, failed, List.of());
        }
    }

    private Validation() {
    }

    public static Predicate<JsonValue> matchesOrThrow(Predicate<JsonValue> predicate)
            throws ValidationException
    {
        return jv -> {
            if(not(predicate).test(jv)) {
                throw new ValidationException("Predicate %s does not match %s".formatted(predicate, jv),
                        new Result(jv, predicate));
            } else return true;
        };
    }

    public static Predicate<JsonValue> required(Collection<String> keys) {
        return jv -> switch (jv) {
            case JsonObject(var members) -> members.keySet().containsAll(keys);
            case null, default -> false;
        };
    }

    public static Predicate<JsonValue> matches(Map<String, Predicate<JsonValue>> structure) {
        return jv -> switch (jv) {
            case JsonObject(var members) -> members.entrySet().stream()
                    .filter(e -> structure.containsKey(e.getKey()))
                    .allMatch(e -> structure.get(e.getKey()).test(e.getValue()));
            case null, default -> false;
        };
    }

    public static Predicate<JsonValue> matchesValuesOf(JsonObject jo) {
        return matches(
                jo.members().entrySet().stream().collect(toMap(
                        Map.Entry::getKey,
                        e -> switch (e.getValue()) {
                            case Basic<?> b -> b;
                            case JsonObject o -> matchesValuesOf(o);
                            case JsonArray a -> matchesValuesOf(a);
                        }
                ))
        );
    }

    public static Predicate<JsonValue> matchesValuesOf(JsonArray array) {
        return matches(array.elements().stream().map(e -> switch (e) {
            case Basic<?> b -> b;
            case JsonObject o -> matchesValuesOf(o);
            case JsonArray a -> matchesValuesOf(a);
        }).toList());
    }

    public static Predicate<JsonValue> matchesTypesOf(JsonArray array) {
        return matches(array.stream().map(v -> switch (v) {
            case JsonBoolean ignored -> is(JsonBoolean.class);
            case Basic<?> b -> is(b.getClass());
            case JsonArray a -> matchesTypesOf(a);
            case JsonObject o -> matchesTypesOf(o);
        }).toList());
    }

    public static Predicate<JsonValue> matchesTypesOf(JsonObject object) {
        return matches(object.members().entrySet().stream().collect(toMap(
                Map.Entry::getKey,
                e -> switch (e.getValue()) {
                    case JsonBoolean ignored -> is(JsonBoolean.class);
                    case Basic<?> b -> is(b.getClass());
                    case JsonArray a -> matchesTypesOf(a);
                    case JsonObject o -> matchesTypesOf(o);
                }))
        );
    }

    public static Predicate<JsonValue> required(Map<String, Predicate<JsonValue>> structure) {
        return required(structure.keySet()).and(matches(structure));
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

    public static Predicate<JsonValue> matches(List<Predicate<JsonValue>> structure) {
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
