package org.metaborg.meta.nabl2.util.collections;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

public class HashBagMultimap<K, V> implements BagMultimap<K, V>, Serializable {

    private static final long serialVersionUID = 42L;

    private final Map<K, Multiset<V>> data;

    private HashBagMultimap(Map<K, Multiset<V>> data) {
        this.data = data;
    }

    @Override public void put(K key, V value) {
        data.computeIfAbsent(key, k -> HashMultiset.create()).add(value);
    }

    @Override public void remove(K key, V value) {
        if(data.containsKey(key)) {
            data.get(key).remove(value);
        }
    }

    @Override public boolean containsKey(K key) {
        return data.containsKey(key) && !data.get(key).isEmpty();
    }

    @Override public boolean containsEntry(K key, V value) {
        return data.containsKey(key) && data.get(key).contains(value);
    }

    @Override public Multiset<V> get(K key) {
        return Multisets.unmodifiableMultiset(data.containsKey(key) ? data.get(key) : HashMultiset.create());
    }

    public static <K, V> BagMultimap<K, V> create() {
        return new HashBagMultimap<>(new HashMap<>());
    }

}
