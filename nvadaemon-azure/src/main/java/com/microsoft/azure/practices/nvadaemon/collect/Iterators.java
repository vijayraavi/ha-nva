package com.microsoft.azure.practices.nvadaemon.collect;

import com.google.common.base.Preconditions;
import com.google.common.collect.PeekingIterator;

public final class Iterators {

    private Iterators() {
    }

    public static <T> CurrentPeekingIterator<T> currentPeekingIterator(PeekingIterator<? extends T> iterator) {
        return new Iterators.CurrentPeekingIteratorImpl(iterator);
    }

    private static class CurrentPeekingIteratorImpl<E> implements CurrentPeekingIterator<E> {
        private final PeekingIterator<? extends E> peekingIterator;
        private E currentElement;

        public CurrentPeekingIteratorImpl(PeekingIterator<? extends E> peekingIterator) {
            this.peekingIterator = (PeekingIterator)Preconditions.checkNotNull(peekingIterator);
        }

        public boolean hasNext() { return this.peekingIterator.hasNext(); }

        public E next() {
            this.currentElement = this.peekingIterator.next();
            return this.currentElement;
        }

        public void remove() {
            this.peekingIterator.remove();
            // If remove() was successful, we can't have a current since it was removed.
            this.currentElement = null;
        }

        public E peek() { return this.peekingIterator.peek(); }

        public E current() { return this.currentElement; }
    }
}
