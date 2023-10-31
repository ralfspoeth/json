package io.github.ralfspoeth.json.io;

import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

class Stack<T> {

    private static class Elem<T> {
        T item;
        Elem<T> next;

        public Elem(T newItem, Elem<T> currentTop) {
            this.item = requireNonNull(newItem);
            this.next = currentTop;
        }
    }

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
        return top == null ? null : top.item;
    }

    public void push(T elem) {
        top = new Elem<>(elem, top);
    }

    /**
     * Swap topmost element by calling the {@code replacement}
     * function on that element and push the result on top
     * of the stack.
     *
     * @param replacement a function
     */
    public void swap(UnaryOperator<T> replacement) {
        requireNonNull(top).item = replacement.apply(top.item);
    }
}
