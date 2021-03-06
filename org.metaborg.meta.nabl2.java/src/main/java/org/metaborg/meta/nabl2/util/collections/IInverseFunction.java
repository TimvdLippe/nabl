package org.metaborg.meta.nabl2.util.collections;

public interface IInverseFunction<K, V> {

    IFunction<V, K> inverse();

    boolean containsKey(K key);

    boolean containsEntry(K key, V value);

    boolean containsValue(V value);

    ISet<K> keySet();

    ISet<V> valueSet();

    ISet<V> get(K key);

    interface Mutable<K, V> extends IInverseFunction<K, V> {

        boolean put(K key, V value);

        boolean remove(K key, V value);

    }

}