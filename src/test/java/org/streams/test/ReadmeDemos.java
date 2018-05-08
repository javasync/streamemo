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

package org.streams.test;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.System.out;
import static java.time.LocalDate.of;
import static java.util.stream.Collectors.toList;


public class ReadmeDemos {

    @Test
    public void thirdExample() throws IOException, InterruptedException {
        IntStream nrs = new Random()
                .ints(0, 7)
                .peek(n -> out.printf("%d, ", n))
                .limit(10);
        out.println("Stream nrs created!");

        CompletableFuture<List<Integer>> mem = CompletableFuture
                .completedFuture(nrs)
                .thenApply(strm -> strm.boxed().collect(toList()));
        out.println("Nrs wraped in a CF and transformed in CF<List<Integer>>!");

        Supplier<Stream<Integer>> cache = () -> mem
                .join()
                .stream();

        Integer max = cache.get().reduce(Integer::max).get();
        out.println("Nrs traversed to get max = " + max);
        long maxOccurrences = cache.get().filter(max::equals).count();
        out.println("Nrs traversed to count max occurrences = " + maxOccurrences);
    }

    @Test
    public void secondApproach() throws IOException, InterruptedException {
        CompletableFuture<List<Integer>> lst = Weather
                .getTemperaturesAsync(38.717, -9.133, of(2018, 4, 1), of(2018, 4, 30))
                .thenApply(strm -> strm.boxed().collect(toList()));

        Supplier<Stream<Integer>> lisbonTempsInMarch = () -> lst
                .join()
                .stream();

        Integer maxTemp = lisbonTempsInMarch.get().reduce(Integer::max).get();
        long nrDaysWithMaxTemp = lisbonTempsInMarch.get().filter(maxTemp::equals).count();

        out.println(maxTemp);
        out.println(nrDaysWithMaxTemp);
    }

    @Test
    public void firstApproach() throws IOException, InterruptedException {
        Supplier<CompletableFuture<IntStream>> lisbonTempsInMarch = () -> Weather
                .getTemperaturesAsync(38.717, -9.133, of(2018, 4, 1), of(2018, 4, 30));

        long count = lisbonTempsInMarch.get().join().distinct().count();
        int maxTemp = lisbonTempsInMarch.get().join().max().getAsInt();

        out.println(count);
        out.println(maxTemp);
    }

    @Test
    public void testMemoizeReplayWithReactorFlux() throws InterruptedException {

        CompletableFuture<Stream<Integer>> temps = Weather
                .getTemperaturesAsync(38.717, -9.133, of(2018, 4, 1), of(2018, 4, 30))
                .thenApply(IntStream::boxed);
        Flux<Integer> cache = Flux
                .fromStream(temps::join)
                .cache();
        out.println("HTTP request sent");
        Thread.currentThread().sleep(2000);
        out.println("Wake up");
        cache.distinct().count().subscribe(out::println);
        cache.reduce(Integer::max).subscribe(out::println);
    }
}
