package io.github.ralfspoeth.json.query;

import io.github.ralfspoeth.json.*;

import java.util.*;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

public class Validation {

    public record Result(JsonValue value, Predicate<JsonValue> predicate, List<Result> details) {
        public Result {
            details = List.copyOf(Objects.requireNonNullElse(details, List.of()));
            Objects.requireNonNull(value);
        }

        public Result(JsonValue value, Predicate<JsonValue> failed) {
            this(value, failed, List.of());
        }

        public boolean failed() {
            return predicate != null;
        }
    }

    sealed interface Structured extends Predicate<JsonValue> {
        Result explain(JsonValue value);

        record MapBased(Map<String, Predicate<JsonValue>> structure) implements Structured {
            @Override
            public boolean test(JsonValue jv) {
                return switch (jv) {
                    case JsonObject(var members) -> members.entrySet().stream()
                            .filter(e -> structure.containsKey(e.getKey()))
                            .allMatch(e -> structure.get(e.getKey()).test(e.getValue()));
                    case null, default -> false;
                };
            }

            @Override
            public Result explain(JsonValue value) {
                return switch (value) {
                    case JsonObject(var members) -> {
                        List<Result> details = new ArrayList<>();
                        members.entrySet().stream()
                                .filter(e -> structure.containsKey(e.getKey()))
                                .filter(e -> !structure.get(e.getKey()).test(e.getValue()))
                                .forEach(e -> {
                                    if (structure.get(e.getKey()) instanceof Structured structured) {
                                        details.add(structured.explain(e.getValue()));
                                    } else {
                                        details.add(new Result(e.getValue(), structure.get(e.getKey())));
                                    }
                                });
                        yield new Result(value, this, details);
                    }
                    case null, default -> new Result(value, this);
                };
            }
        }

        record ListBased(List<Predicate<JsonValue>> structure) implements Structured {
            @Override
            public boolean test(JsonValue jv) {
                return switch (jv) {
                    case JsonArray(var elements) when elements.size() == structure.size() -> {
                        for (int i = 0; i < elements.size(); i++) {
                            if (!structure.get(i).test(elements.get(i))) yield false;
                        }
                        yield true;
                    }
                    case null, default -> false;
                };
            }

            @Override
            public Result explain(JsonValue value) {
                return switch (value) {
                    case JsonArray(var elems) when elems.size() == structure.size() -> {
                        List<Result> details = new ArrayList<>();
                        for (int i = 0; i < elems.size(); i++) {
                            if (!structure.get(i).test(elems.get(i))) {
                                if (structure.get(i) instanceof Structured structured) {
                                    details.add(structured.explain(elems.get(i)));
                                } else {
                                    details.add(new Result(elems.get(i), structure.get(i)));
                                }
                            }
                        }
                        yield new Result(value, this, details);
                    }
                    case null, default -> new Result(value, this);
                };
            }
        }

        record All(Predicate<JsonValue> predicate) implements Structured {

            @Override
            public Result explain(JsonValue value) {
                return switch (value) {
                    case JsonArray(var elems) -> {
                        List<Result> details = new ArrayList<>();
                        elems.stream().filter(not(predicate)).forEach(e -> {
                            if (predicate instanceof Structured structured) {
                                details.add(structured.explain(e));
                            } else {
                                details.add(new Result(e, predicate));
                            }
                        });
                        yield new Result(value, this, details);
                    }
                    case null, default -> new Result(value, this);
                };
            }

            @Override
            public boolean test(JsonValue jsonValue) {
                return switch (jsonValue) {
                    case JsonArray(var elems) -> elems.stream().allMatch(predicate);
                    case null, default -> false;
                };
            }
        }

        record Any(Predicate<JsonValue> predicate) implements Structured {

            @Override
            public Result explain(JsonValue value) {
                return new Result(value, this);
            }

            @Override
            public boolean test(JsonValue jsonValue) {
                return switch (jsonValue) {
                    case JsonArray(var elems) -> elems.stream().anyMatch(predicate);
                    case null, default -> false;
                };
            }

        }

        record Wrapped(Predicate<JsonValue> predicate) implements Structured {

            @Override
            public Result explain(JsonValue value) {
                return predicate instanceof Structured s? s.explain(value):new Result(value, predicate);
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
        if (result.failed()) {
            return explain(result.value, result.predicate);
        } else {
            return result;
        }
    }

    public static JsonValue throwIfFailed(Result result) throws ValidationException {
        if (result.failed()) {
            throw new ValidationException("Predicate %s does not match %s".formatted(result.predicate, result.value),
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
            case null, default -> false;
        };
    }

    public static Predicate<JsonValue> matches(Map<String, Predicate<JsonValue>> structure) {
        return new Structured.MapBased(structure);
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
        return new Structured.All(predicate);
    }

    public static Predicate<JsonValue> any(Predicate<JsonValue> predicate) {
        return new Structured.Any(predicate);
    }

    public static Predicate<JsonValue> matches(List<Predicate<JsonValue>> structure) {
        return new Structured.ListBased(structure);
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

    public static Predicate<JsonValue> always() {
        return jv -> true;
    }

    public static Predicate<JsonValue> never() {
        return jv -> false;
    }

    public static Result explain(JsonValue jsonValue, Predicate<JsonValue> predicate) {
        return switch (predicate) {
            case Structured structured -> structured.explain(jsonValue);
            case null, default -> new Result(jsonValue, predicate);
        };
    }
}
