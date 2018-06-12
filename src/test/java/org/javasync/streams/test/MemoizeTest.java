/*
 * MIT License
 *
 * Copyright (c) 2018, Miguel Gamboa (gamboa.pt)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.javasync.streams.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.System.out;
import static java.util.stream.Stream.concat;
import static java.util.stream.StreamSupport.stream;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MemoizeTest {

    /**
     * Creates a new stream supplier which memoizes items when they are traversed.
     * The stream created by the supplier retrieves items from: 1) the mem or
     * 2) the data source, depending on whether it has been already requested
     * by a previous operation, or not.
     * @param src
     * @param <T>
     * @return
     */
    public static <T> Supplier<Stream<T>> memoize(Stream<T> src) {
        final Spliterator<T> iter = src.spliterator();
        final ArrayList<T> mem = new ArrayList<>();
        class MemoizeIter extends Spliterators.AbstractSpliterator<T> {
            MemoizeIter() { super(iter.estimateSize(), iter.characteristics()); }
            public boolean tryAdvance(Consumer<? super T> action) {
                return iter.tryAdvance(item -> {
                    mem.add(item);
                    action.accept(item);
                });
            }
            public Comparator<? super T> getComparator() {
                return iter.getComparator();
            }
        }
        MemoizeIter srcIter = new MemoizeIter();
        return () -> concat(mem.stream(), stream(srcIter, false));
    }

    /**
     * This solution does not allow concurrent iterations while the data source has
     * not been entirely consumed.
     * In that case it throws a ConcurrentModificationException.
     */
    @Test
    public void testWrongConcurrentIteratorsOnMemoizedStream() {
        assertThrows(ConcurrentModificationException.class, () -> {
            Random rnd = new Random();
            Supplier<Stream<Integer>> nrs = memoize(IntStream.range(1, 10).boxed());
            Spliterator<Integer> iter1 = nrs.get().spliterator();
            iter1.tryAdvance(out::println);
            iter1.tryAdvance(out::println);
            Spliterator<Integer> iter2 = nrs.get().spliterator();
            iter1.tryAdvance(out::println);
            iter2.forEachRemaining(out::print); // throws ConcurrentModificationException
            System.out.println();
        });
    }

    @Test
    public void fourthExampleOfReadme() throws IOException, InterruptedException {
        IntStream nrs = new Random()
                .ints(0, 7)
                .peek(n -> out.printf("%d, ", n))
                .limit(10);
        out.println("Stream nrs created!");

        Supplier<Stream<Integer>> mem = memoize(nrs.boxed());
        out.println("Nrs wrapped in a memoizable Supplier ");

        Integer max = mem.get().max(Integer::compareTo).get();
        out.println("Nrs traversed to get max = " + max);
        long maxOccurrences = mem.get().filter(max::equals).count();
        out.println("Nrs traversed to count max occurrences = " + maxOccurrences);
    }
}
