package io.github.ralfspoeth.json.io;

import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

class Stack<T> {

    private record Elem<T>(T item, Elem<T> next) {}

    private Elem<T> top = null;

    public boolean isEmpty() {
        return top == null;
    }

    public T pop() {
        var tmp = requireNonNull(top).item;
        top = top.next;
        return tmp;
    }

    public T top() {
        return top==null?null:top.item;
    }

    public Stack<T> push(T elem) {
        top = new Elem<>(requireNonNull(elem), top);
        return this;
    }

    /**
     * Swap topmost element by calling the {@code replacement}
     * function on that element and push the result on top
     * of the stack.
     *
     * @param replacement a function
     * @return {@code this}
     */
    public Stack<T> swap(UnaryOperator<T> replacement) {
        return push(replacement.apply(pop()));
    }
}
