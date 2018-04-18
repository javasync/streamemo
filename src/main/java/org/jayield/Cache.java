package org.jayield;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Cache {

    public static <T> Supplier<Stream<T>> of(Supplier<Stream<T>> dataSrc) {
        Spliterator<T> src = dataSrc.get().spliterator();
        long size = src.estimateSize();
        Recorder<T> rec = new Recorder<>(src);
        return () -> {
            // CacheIterator starts on index 0 and reads data from src or
            // from an internal cache of Recorder.
            CacheIterator<T> iter = new CacheIterator<>(rec, size, src.characteristics());
            return StreamSupport.stream(iter, false);
        };
    }

    static class Recorder<T> {
        final Spliterator<T> src;
        final List<T> cache = new ArrayList<>();
        boolean hasNext = true;
        public Recorder(Spliterator<T> src) {
            this.src = src;
        }
        public synchronized boolean getOrAdvance(
                int index,
                Consumer<? super T> cons) {
            if(index < cache.size())
                // If it is in cache then just get if from the corresponding index.
                cons.accept(cache.get(index++));
            else if(hasNext)
                // If not in cache then advance the src iterator
                hasNext = src.tryAdvance(item -> {
                    cache.add(item);
                    cons.accept(item);
                });

            return index < cache.size() || hasNext;
        }
    }

    static class CacheIterator<T> extends Spliterators.AbstractSpliterator<T> {
        final Recorder<T> rec;
        int index = 0;
        public CacheIterator(Recorder<T> rec, long estimateSize, int characteristics) {
            super(estimateSize, characteristics);
            this.rec = rec;
        }
        public boolean tryAdvance(Consumer<? super T> cons) {
            return rec.getOrAdvance(index++, cons);
        }
        public Comparator<? super T> getComparator() {
            return rec.src.getComparator();
        }
    }
}
