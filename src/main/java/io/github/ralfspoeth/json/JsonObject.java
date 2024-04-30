package io.github.ralfspoeth.json;

import java.util.Map;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

public record JsonObject(Map<String, Element> members) implements Aggregate, Function<String, Element> {

    public JsonObject {
        members = Map.copyOf(members);
    }

    @Override
    public int size() {
        return members.size();
    }

    @Override
    public int depth() {
        return members.values().stream().mapToInt(v ->
            switch (v) {
                case Aggregate a -> a.depth();
                case Basic<?> ignored -> 1;
            }
        ).max().orElse(0)+1;
    }

    /*
    public <R extends Record> R toRecord(Class<R> r) {
        var rc = r.getRecordComponents();
        var rct = new Class<?>[rc.length];
        for(int i=0; i<rc.length;i++) {
            rct[i] = rc[i].getType();
        }

        var values = new Element[rc.length];
        for(int i=0; i< rc.length; i++) {
            values[i] = members.get(rc[i].getName());
        }

        var args = new Object[values.length];
        for(int i=0; i < args.length; i++) {
            args[i] = switch (values[i]) {
                case Basic<?> b -> b.value();
                case JsonObject jo -> jo.toRecord((Class<? extends Record>)rct[i]);
                case null, default -> null;
            };
        }

        try {
            return r.getDeclaredConstructor(rct).newInstance(args);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }*/

    public <T extends Element> T get(String name, Class<T> cls) {
        return ofNullable(members.get(name)).map(cls::cast).orElse(null);
    }

    @Override
    public Element apply(String name) {
        return members.get(name);
    }
}
