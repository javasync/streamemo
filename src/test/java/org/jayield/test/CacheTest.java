package org.jayield.test;

import org.jayield.Cache;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.System.out;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.*;

public class CacheTest {
    @Test
    public void testPrintInfiniteRandomStream() {
        Random rnd = new Random();
        Supplier<Stream<String>> nrs = () -> generate(() -> rnd.nextInt(99)).map(Object::toString);
        IntStream.range(1, 6).forEach(size -> out.println(nrs.get().limit(size).collect(joining(","))));

        out.println();
        Supplier<Stream<String>> nrsReplay = Cache.of(nrs);
        IntStream.range(1, 6).forEach(size -> out.println(nrsReplay.get().limit(size).collect(joining(","))));
    }

    @Test
    public void testInfiniteRandomStream() {
        Random rnd = new Random();
        Supplier<Stream<Integer>> nrs = () -> generate(() -> rnd.nextInt(99));
        Supplier<Stream<Integer>> nrsReplay = Cache.of(nrs);
        List<Integer> expected = new ArrayList<>();
        IntStream.range(1, 6).forEach(size -> nrsReplay
                .get()
                .limit(size)
                .reduce((p, n) -> n) // return last
                .ifPresent(expected::add));
        Assertions.assertArrayEquals(
                expected.toArray(),
                nrsReplay.get().limit(5).toArray());
    }
}
