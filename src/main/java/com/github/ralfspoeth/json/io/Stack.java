package com.github.ralfspoeth.json.io;

import static java.util.Objects.requireNonNull;

class Stack<T> {

    private record Node<T>(T item, Node<T> next) {}

    private Node<T> top = null;

    boolean isEmpty() {
        return top == null;
    }

    T pop() {
        var tmp = requireNonNull(top).item;
        top = top.next;
        return tmp;
    }

    T top() {
        return top==null?null:top.item;
    }

    Stack<T> push(T elem) {
        top = new Node<>(requireNonNull(elem), top);
        return this;
    }
}
