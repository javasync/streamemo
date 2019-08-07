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

import org.javasync.streams.Replayer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.lang.System.out;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReplayTest {

    @Test
    @SuppressWarnings("Duplicates")
    public void testConcurrentIteratorsOnMemoizedStream() {
        Random rnd = new Random();
        Supplier<Stream<Integer>> nrs = Replayer.replay(IntStream.range(1, 10).boxed());
        Spliterator<Integer> iter1 = nrs.get().spliterator();
        iter1.tryAdvance(out::println);
        iter1.tryAdvance(out::println);
        Spliterator<Integer> iter2 = nrs.get().spliterator();
        iter1.tryAdvance(out::println);
        iter2.forEachRemaining(out::print);
        System.out.println(); // throws ConcurrentModificationException
    }

    @Test
    public void testReplayInfiniteRandomStream() {
        Random rnd = new Random();
        Stream<Integer> nrs = Stream.generate(() -> rnd.nextInt(99));
        Supplier<Stream<Integer>> nrsSrc = Replayer.replay(nrs);

        nrsSrc.get().limit(11).map(n -> n + ",").forEach(out::print); // e.g. 88,18,78,75,98,68,15,14,25,54,22,
        out.println();
        nrsSrc.get().limit(11).map(n -> n + ",").forEach(out::print); // Print the same previous numbers
    }

    @Test
    public void testPrintInfiniteRandomStream() {
        Random rnd = new Random();
        Supplier<Stream<String>> nrs = () -> generate(() -> rnd.nextInt(99)).map(Object::toString);
        IntStream.range(1, 6).forEach(size -> out.println(nrs.get().limit(size).collect(joining(","))));

        out.println();
        Supplier<Stream<String>> nrsReplay = Replayer.replay(nrs);
        IntStream.range(1, 6).forEach(size -> out.println(nrsReplay.get().limit(size).collect(joining(","))));
    }

    @Test
    public void testInfiniteRandomStream() {
        Random rnd = new Random();
        // Supplier<Stream<Integer>> nrs = () -> generate(() -> rnd.nextInt(99));
        Supplier<Stream<Integer>> nrs = Replayer.replay(() -> generate(() -> rnd.nextInt(99)));
        List<Integer> expected = new ArrayList<>();
        IntStream.range(1, 6).forEach(size -> nrs
                .get()
                .limit(size)
                .reduce((p, n) -> n) // return last
                .ifPresent(expected::add));
        Assertions.assertArrayEquals(
                expected.toArray(),
                nrs.get().limit(5).toArray());
    }


    @Test
    public void testHighlightDifferentAspects() {
        boolean[] called = new boolean[10];
        Supplier<Stream<Integer>> s = Replayer.replay(() -> IntStream
                .range(0, 10)
                .peek(n -> {
                    if (called[n]) Assertions.fail("Already generated: " + n);
                    called[n] = true;
                })
                .boxed());

        s.get().findFirst();
        s.get().toArray();
        s.get()
                .filter(i -> i < 5)
                .forEach(x -> {
                });
        s.get().toArray();
    }

    @Test
    public void testIntersectionInStreams() {
        Random rnd = new Random();
        Stream<Integer> nrs1 = rnd.ints(1, 20).boxed().limit(10);
        Supplier<Stream<Integer>> nrs2 = Replayer.replay(rnd.ints(1, 20).boxed().limit(10));
        nrs1
                .filter(n1 -> nrs2.get().anyMatch(n1::equals))
                .distinct()
                .forEach(out::println);
    }

    @Test
    public void testLongStream() {
        assertThrows(IllegalStateException.class, () -> {
            long size = ((long) Integer.MAX_VALUE) + 10;
            Supplier<Stream<Long>> nrs = Replayer.replay(LongStream.range(0, size).boxed());
            assertEquals(size, nrs.get().count());
        });
    }
    @Test
    public void testParallel() {
        Random rnd = new Random();
        Supplier<Stream<Integer>> nrs = Replayer.replay(rnd.ints(1, 1024).boxed().limit(1024*1024*8));
        assertEquals(1024*1024*8, nrs.get().count());
        assertEquals(1023, (int) nrs
                .get()
                .parallel()
                .max(Integer::compareTo)
                .get());

    }
    @Test
    public void testOnClose() {
    	final AtomicInteger closeCounter = new AtomicInteger();
    	final Stream<Long> originalStream = LongStream.range(1, 5).boxed()
    			.onClose(() -> closeCounter.getAndIncrement());
    	Supplier<Stream<Long>> replayer = Replayer.replay(originalStream);
    	try(Stream<Long> s = replayer.get()) {
    		Long[] numbers = s.toArray(Long[]::new);
    		assertArrayEquals(new Long[] {1L, 2L, 3L, 4L}, numbers);
    	}
    	assertEquals(1, closeCounter.get());
    }
    @Test
    public void testOnCloseWithSupplier() {
    	final List<AtomicInteger> closeCounter = new ArrayList<>();
    	final Supplier<Stream<Long>> originalStream = () -> {
            Stream<Long> src = LongStream.range(1, 5).boxed();
            AtomicInteger c = new AtomicInteger();
            closeCounter.add(c);
            src.onClose(() -> c.getAndIncrement());
            return src;
    	};

    	Supplier<Stream<Long>> replayer = Replayer.replay(originalStream);
    	// first stream
    	try(Stream<Long> s = replayer.get()) {
    		Long[] numbers = s.toArray(Long[]::new);
    		assertArrayEquals(new Long[] {1L, 2L, 3L, 4L}, numbers);
    	}
    	assertEquals(1, closeCounter.size());
        closeCounter.forEach(c -> assertEquals(1, c.get()));
        
        // replay stream
        try(Stream<Long> s = replayer.get()) {
        	Long[] numbers = s.toArray(Long[]::new);
    		assertArrayEquals(new Long[] {1L, 2L, 3L, 4L}, numbers);
    	}
    	assertEquals(1, closeCounter.size());
        closeCounter.forEach(c -> assertEquals(1, c.get()));
    }
}