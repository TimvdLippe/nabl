package org.metaborg.meta.nabl2.util.collections;

import java.util.Iterator;

public class WrappedSet<E> implements ISet.Mutable<E> {

    private final java.util.Set<E> elems;

    private WrappedSet(java.util.Set<E> elems) {
        this.elems = elems;
    }

    @Override public boolean contains(E elem) {
        return elems.contains(elem);
    }

    @Override public void add(E elem) {
        elems.add(elem);
    }

    @Override public void remove(E elem) {
        elems.remove(elem);
    }

    @Override public Iterator<E> iterator() {
        return elems.iterator();
    }

    public static <E> WrappedSet<E> of(java.util.Set<E> elems) {
        return new WrappedSet<>(elems);
    }

}