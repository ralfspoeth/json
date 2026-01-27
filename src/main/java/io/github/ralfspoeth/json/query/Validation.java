package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.basix.fn.Indexed;
import io.github.ralfspoeth.json.data.*;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Gatherer;

import static io.github.ralfspoeth.basix.fn.Functions.indexed;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

public class Validation {

    public record Result(JsonValue value, @Nullable Predicate<JsonValue> failed, List<Result> details) {
        public Result {
            details = List.copyOf(details);
        }

        public Result(JsonValue value, @Nullable Predicate<JsonValue> predicate) {
            this(value, predicate, List.of());
        }

        public boolean testFailed() {
            return failed != null;
        }
    }

    sealed interface Structure extends Predicate<JsonValue> {
        Result explain(JsonValue value);

        record MapBased(Map<String, Predicate<JsonValue>> structure) implements Structure {
            @Override
            public boolean test(JsonValue jv) {
                return jv instanceof JsonObject(var members) && members.entrySet().stream()
                        .filter(e -> structure.containsKey(e.getKey()))
                        .allMatch(e -> structure.get(e.getKey()).test(e.getValue()));
            }

            @Override
            public Result explain(JsonValue value) {
                if (value instanceof JsonObject(var members)) {
                    return new Result(value, this,
                            members.entrySet()
                                    .stream()
                                    .filter(e -> structure.containsKey(e.getKey()))
                                    .filter(e -> !structure.get(e.getKey()).test(e.getValue()))
                                    .gather(Gatherer.<Map.Entry<String, JsonValue>, ArrayList<Result>, Result>ofSequential(
                                            ArrayList::new,
                                            (l, e, d) ->
                                                    structure.get(e.getKey()) instanceof Structure s && d.push(s.explain(e.getValue()))
                                                            || d.push(new Result(e.getValue(), structure.get(e.getKey()))))
                                    )
                                    .toList()
                    );
                } else return new Result(value, this);
            }
        }

        record ListBased(List<Predicate<JsonValue>> structure) implements Structure {
            @Override
            public boolean test(JsonValue jv) {
                if (jv instanceof JsonArray(var elements) && elements.size() == structure.size()) {
                    for (int i = 0; i < elements.size(); i++) {
                        if (!structure.get(i).test(elements.get(i))) return false;
                    }
                    return true;
                } else return false;
            }


            @Override
            public Result explain(JsonValue value) {
                if(value instanceof JsonArray(var elems) && elems.size()== structure().size()) {
                    return new Result(value, this, elems.stream()
                            .map(indexed(0))
                            .gather(Gatherer.<Indexed<JsonValue>, ArrayList<Result>, Result>ofSequential(
                                    ArrayList::new,
                                    (l, iv, d) -> {
                                        if(structure().get(iv.index()) instanceof Structure s) {
                                            return d.push(s.explain(iv.value()));
                                        } else {
                                            return d.push(new Result(iv.value(), this));
                                        }
                                    }

                            ))
                            .toList()
                    );
                } else {
                    return new Result(value, this);
                }
            }
        }

        record All(Predicate<JsonValue> predicate) implements Structure {

            @Override
            public Result explain(JsonValue value) {
                return switch (value) {
                    case JsonArray(var elems) -> {
                        List<Result> details = new ArrayList<>();
                        elems.stream().filter(not(predicate)).forEach(e -> {
                            if (predicate instanceof Structure structure) {
                                details.add(structure.explain(e));
                            } else {
                                details.add(new Result(e, predicate));
                            }
                        });
                        yield new Result(value, this, details);
                    }
                    default -> new Result(value, this);
                };
            }

            @Override
            public boolean test(JsonValue jsonValue) {
                return switch (jsonValue) {
                    case JsonArray(var elems) -> elems.stream().allMatch(predicate);
                    default -> false;
                };
            }
        }

        record Any(Predicate<JsonValue> predicate) implements Structure {

            @Override
            public Result explain(JsonValue value) {
                return new Result(value, this);
            }

            @Override
            public boolean test(JsonValue jsonValue) {
                return switch (jsonValue) {
                    case JsonArray(var elems) -> elems.stream().anyMatch(predicate);
                    default -> false;
                };
            }

        }

        record Wrapped(Predicate<JsonValue> predicate) implements Structure {

            @Override
            public Result explain(JsonValue value) {
                return predicate instanceof Structure s ? s.explain(value) : new Result(value, predicate);
            }

            @Override
            public boolean test(JsonValue jsonValue) {
                return predicate.test(jsonValue);
            }
        }
    }

    private Validation() {}

    public static Result check(JsonValue value, Predicate<JsonValue> predicate) {
        return predicate.test(value) ? new Result(value, null) : new Result(value, predicate);
    }

    public static Result explainIfFailed(Result result) {
        if (result.testFailed()) {
            return explain(result.value, result.failed);
        } else {
            return result;
        }
    }

    public static JsonValue throwIfFailed(Result result) throws ValidationException {
        if (result.testFailed()) {
            throw new ValidationException("Predicate %s does not match %s".formatted(result.failed, result.value),
                    result);
        }
        return result.value;
    }

    public static Predicate<JsonValue> matchesOrThrow(Predicate<JsonValue> predicate)
            throws ValidationException {
        return jv -> {
            if (not(predicate).test(jv)) {
                throw new ValidationException("Predicate %s does not match %s".formatted(predicate, jv),
                        new Result(jv, predicate));
            } else return true;
        };
    }

    public static Predicate<JsonValue> required(Collection<String> keys) {
        return jv -> switch (jv) {
            case JsonObject(var members) -> members.keySet().containsAll(keys);
            default -> false;
        };
    }

    public static Predicate<JsonValue> matches(Map<String, Predicate<JsonValue>> structure) {
        return new Structure.MapBased(structure);
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
        return matches(array.elements().stream().map(v -> switch (v) {
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
        return new Structure.All(predicate);
    }

    public static Predicate<JsonValue> any(Predicate<JsonValue> predicate) {
        return new Structure.Any(predicate);
    }

    public static Predicate<JsonValue> matches(List<Predicate<JsonValue>> structure) {
        return new Structure.ListBased(structure);
    }

    public static Predicate<JsonValue> regex(String regex) {
        return jv -> switch (jv) {
            case JsonString(String s) -> s.matches(regex);
            default -> false;
        };
    }

    public static Predicate<JsonValue> is(Class<? extends JsonValue> type) {
        return type::isInstance;
    }

    public static Predicate<JsonValue> always() {
        return jv -> true;
    }

    public static Predicate<JsonValue> never() {
        return jv -> false;
    }

    public static Result explain(JsonValue jsonValue, @Nullable Predicate<JsonValue> predicate) {
        return switch (predicate) {
            case Structure structure -> structure.explain(jsonValue);
            case null, default -> new Result(jsonValue, predicate);
        };
    }
}
