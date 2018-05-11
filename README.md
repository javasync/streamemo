# streams

Java streams utility methods for memoization

## How to replay Java streams?

Although java streams were designed to be operated 
only once, programmers still ask how to reuse a
stream? From a simple web search we can find many
posts with this same issue asked in many different
ways: ["Is there any way to reuse a Stream in java
8?"][1], ["Copy a stream to avoid stream has already
been operated upon or closed"][2], ["Java 8 Stream
IllegalStateException"][3], and others.

First, these questions are biased because many
programmers wrongly think on a `Stream` as an
equivalent to `Iterable`. But an `Iterable` is just
the ability to provide an actual `Iterator`, whereas
a `Stream` is just a kind of iterator. So, the right
comparison is between `Stream` and `Iterator` wherein
both have the same restriction disallowing multiple
traversals. 

Second, if we want to traverse a `Stream<T>` multiple
times then either we have to: 1) redo the computation
to generate the stream from the data source, or 2)
store the intermediate result into a collection. 
The first approach is similar to what we do when we
get an `Iterator` object from an `Iterable`. To that
end, we might wrap the stream expression into a supplier
(e.g. `Supplier<Stream<T>> source`) and call its method
`get()` whenever we want a fresh stream chain
(e.g. `source.get()`).

However what happens if data comes from the network, or
from a file or database, or other external source?
In this case, we must return to the data source again,
which may have more overhead than storing the intermediate
result into a collection. So for multiple traversals
on immutable data maybe the second solution is more
advantageous.
Yet, if we are only going to use the data once then we
get an efficiency payback using this approach, because
we did not have to store the data source in memory.

So shortly, both options have drawbacks and none of
them is best suited for all use cases. Here we will
explore the limitations and advantages of each approach
on data resulting from an HTTP request.
We will incrementally combine these solutions:
1. [using a `Supplier<Stream<…>>`](#approach-1----supplierstream)
2. [memoizing the resulting stream into a collection](#approach-2----memoize-to-a-collection)
to avoid multiple roundtrips to data source
3. [memoizing and replaying items on demand](#approach-3----memoize-and-replay-on-demand)
into and from an internal buffer.

In order to understand all details of this article you
must be familiarized with streams terminology which is
briefly revisited in the last [appendix](#appendix----streams-revisited). 

## Streams Use Case

`Stream` operations are very convenient to perform
queries on sequences of items and many times programmers
look for a way to consume a `Stream` more than once.
Moreover, in other programming environments, such as .Net
and Javascript, programmers does not stuck at this limitation
(one-shot traversal), because stream operations are not
provided at `Iterator` level, but instead at `Iterable`
level, which can be traversed multiple times.
So, in .Net or Javascript, we can operate on same data
with different queries without violating any internal
state, because every time a query is computed it will
get a fresh iterator from the source.

To highlight the drawbacks of alternative approaches on
reusing Java streams we will present a use case based on
a HTTP request. 
To that end we will use the [World Weather online API][4]
to get a sequence of items with weather information in
CSV data format.
Particularly, we are interested in a sequence of
temperatures in degrees Celsius in March at Lisbon, Portugal.
So we must perform an HTTP GET request to the URI
http://api.worldweatheronline.com/premium/v1/past-weather.ashx
with the query parameters `q=37.017,-7.933` corresponding to
Lisbon geographic coordinates,
`date=2018-03-01&enddate=2018-03-31` defining the dates interval,
`tp=24` to get data in 24 hours periods, `format=csv` to specify
data format and finally `key=` with the API key. 

To perform this HTTP request we will use a non-blocking API,
such as the [AsyncHttpClient][5], and the response will be
a sequence of lines in CSV format (e.g. `Stream<String>`).
Since we are using an asynchronous API, we do not want to wait
for response completion; hence the result of the HTTP request
is wrapped into a [promise][6] of that response. 
In Java a promise is represented by a [`CompletableFuture`][7]
instance (e.g. `CompletableFuture<Stream<String>`) and it is
obtained from the following HTTP request with the `AsyncHttpClient`:

```java
Pattern pat = Pattern.compile("\\n");
CompletableFuture<Stream<String>> csv = asyncHttpClient()
        .prepareGet("http://api.worldweatheronline.com/premium/v1/past-weather.ashx?q=37.017,-7.933&date=2018-04-01&enddate=2018-04-30&tp=24&format=csv&key=54a4f43fc39c435fa2c143536183004")
        .execute()
        .toCompletableFuture()
        .thenApply(Response::getResponseBody)
        .thenApply(pat::splitAsStream);
```

Now, in order to get a sequence of temperatures we 
must parse the CSV according to the following rules:
1. ignore lines starting with `#` that correspond to
comments; 
2. skip one line;
3. filter lines alternately;
4. extract the third value corresponding to temperature
in Celsius;
5. convert it to integer.

<img alt="weather-data-source" src="https://user-images.githubusercontent.com/578217/39763003-ea99b4a2-52d3-11e8-86ed-a6ffae325cab.jpg" width="500">

To make these transformations without waiting for
response completion we will use the method `thenApply()`
of `CompletableFuture` passing a function that will
convert the `Stream<String>` into an `IntStream`.
Thus, when the resulting `csv` is available (i.e.
`Stream<String>`) it will continue processing the
transformation without blocking:

```java
boolean [] isEven = {true};
Pattern comma = Pattern.compile(",");
CompletableFuture<IntStream> temps = csv.thenApply(str -> str
        .filter(w -> !w.startsWith("#")) // Filter comments
        .skip(1)                         // Skip line: Not Available
        .filter(l -> isEven[0] = !isEven[0]) // Filter Even line
        .map(line -> comma.splitAsStream(line).skip(2).findFirst().get()) // Extract temperature in Celsius
        .mapToInt(Integer::parseInt));
```

Finally, we can build a [`Weather`][8] service class
providing an auxiliary asynchronous method
[`getTemperaturesAsync(double lat, double log,
LocalDate from, LocalDate to)`][9] to get a sequence
of temperatures for a given location and an
interval of dates:

```java
public class Weather{
    ...
    public static CompletableFuture<IntStream> getTemperaturesAsync(double lat, double log, LocalDate from, LocalDate to) {
        AsyncHttpClient asyncHttpClient = asyncHttpClient();
        CompletableFuture<Stream<String>> csv = asyncHttpClient
                .prepareGet(String.format(PATH, lat, log, from, to, KEY))
                .execute()
                .toCompletableFuture()
                .thenApply(Response::getResponseBody)
                .thenApply(NEWLINE::splitAsStream);
        boolean[] isEven = {true};
        return csv.thenApply(str -> str
                .filter(w -> !w.startsWith("#"))     // Filter comments
                .skip(1)                             // Skip line: Not Available
                .filter(l -> isEven[0] = !isEven[0]) // Filter Even line
                .map(line -> COMMA.splitAsStream(line).skip(2).findFirst().get()) // Extract temperature in Celsius
                .mapToInt(Integer::parseInt));// Convert to Integer
    }
```

## Approach 1 -- `Supplier<Stream<...>>`

Given the method `getTemperaturesAsync()` we can get
a sequence of temperatures at Lisbon in March as:

```java
CompletableFuture<IntStream> lisbonTempsInMarch = Weather
                .getTemperaturesAsync(38.717, -9.133, of(2018,4,1), of(2018,4,30));
```

Now, whenever we want to perform a query we may get
the resulting stream from the `CompletableFuture`.
In the following example we are getting
the maximum temperature in March and counting how many
days reached this temperature.

```java
int maxTemp = lisbonTempsInMarch.join().max();
long nrDaysWithMaxTemp = lisbonTempsInMarch
                             .join()
                             .filter(maxTemp::equals)
                             .count(); // Throws IllegalStateException 
```

However, the second query will throw an exception
because the stream from `lisbonTempsInMarch.join()` has
already been operated on first query.
To avoid this exception we must get a fresh stream
combining all intermediate operations to the data source.
This means that we have to make a new HTTP request and
repeat all the transformations over the HTTP response.
To that end, we will use a `Supplier<CompletableFuture<Stream<T>>>`
that wraps the request and subsequent transformations into
a supplier:

```java
Supplier<CompletableFuture<IntStream>> lisbonTempsInMarch = () -> Weather
                .getTemperaturesAsync(38.717, -9.133, of(2018, 4, 1), of(2018, 4, 30));
```

And now whenever we want to execute a new query we
can perform a new HTTP request through the
`get()` method of the supplier ` lisbonTempsInMarch`,
then get the resulting stream from the response through
`join()` and finally invoke the desired stream operations as:

```java
int maxTemp = lisbonTempsInMarch.get().join().max();
long nrDaysWithMaxTemp = lisbonTempsInMarch
                             .get()
                             .join()
                             .filter(maxTemp::equals)
                             .count();
```

To avoid the consecutive invocation of `get()` and
`join()` we can put the call to `join()` method inside
the supplier as:

```java
Supplier<CompletableFuture<IntStream>> lisbonTempsInMarch = () -> Weather
                .getTemperaturesAsync(38.717, -9.133, of(2018, 4, 1), of(2018, 4, 30))
                .join();
```

And now we can simply write:

```java
int maxTemp = lisbonTempsInMarch.get().max();
long nrDaysWithMaxTemp = lisbonTempsInMarch
                             .get()
                             .filter(maxTemp::equals)
                             .count();
```


Briefly, according to this approach we are creating
a new stream chain (with all the transformations
specified in [`getTemperaturesAsync()`][9]) every time
we want to consume that stream.
This idea is based on a claim of the Java documentation
about [Stream operations and pipelines][10] that states: 

> if you need to traverse the same data source again,
> you must return to the data source to get a new stream.

However this technique forces the recreation of the
whole pipeline to the data source which incurs in
inevitable IO due to the HTTP request. 
Since data from past weather information is immutable
then this HTTP request is useless because we will
always get the same sequence of temperatures. 

## Approach 2 -- Memoize to a collection

To avoid useless accesses to the data source we may
first dump the stream elements into an auxiliary
collection (e.g. `List<T> list =
data.collect(Collectors.toList())`) and then get a
new `Stream` from the resulting collection whenever
we want to operate that sequence (e.g.
`list.stream().filter(…).map(…)….`).

Using this technique we can transform the resulting
promise from the `getTemperaturesAsync()` into a new
promise of a list of integers (i.e.
`CompletableFuture<List<Integer>>`).
Thus, when we get the HTTP response and after it is
transformed into an `IntStream`, then it will proceed
to be collected into a `List<Integer>`:

```java
CompletableFuture<List<Integer>> mem = Weather
                .getTemperaturesAsync(38.717, -9.133, of(2018, 4, 1), of(2018, 4, 30))
                .thenApply(strm -> strm.boxed().collect(toList()));
```

With this `CompletableFuture<List<Integer>>` we can
build a `Supplier<Stream<Integer>>` that returns a
new stream from the list contained in the `CompletableFuture`.

```java
Supplier<Stream<Integer>> lisbonTempsInMarch = () -> mem.join().stream();
```

Now, when we ask for a new stream to `lisbonTempsInMarch`,
instead of chaining a stream pipeline to the data source
(approach 1) we will get a fresh stream from the auxiliary
list contained in `mem` that collected the intermediate
sequence of temperatures.

```java
Integer maxTemp = lisbonTempsInMarch.get().max(Integer::compare).get();
long nrDaysWithMaxTemp = lisbonTempsInMarch.get().filter(maxTemp::equals).count();
```

Yet, this approach incurs in an additional traversal to
first collect the stream items. 
We are wasting one first traversal which is not used to
operate the stream elements (i.e. `strm.boxed().collect(toList())`)
and then we incur in a second traversal to query that
sequence (i.e. `lisbonTempsInMarch.get().max(Integer::compare).get()`).
If we are only going to use the data once then we get
a huge efficiency payback, because we did not have to
store it in memory.
Moreover, we are also wasting powerful "_loop fusion_"
optimizations offered by stream, which let data flow
through the whole pipeline efficiently from the data
source to the terminal operation.

To highlight the additional traversal that first occurs
on collect consider the following example where we
replace the `getTemperaturesAsync()` with a random
stream of integers:

```java
IntStream nrs = new Random()
        .ints(0, 7)
        .peek(n -> out.printf("%d, ", n))
        .limit(10);
out.println("Stream nrs created!");

CompletableFuture<List<Integer>> mem = CompletableFuture
        .completedFuture(nrs)
        .thenApply(strm -> strm.boxed().collect(toList()));
out.println("Nrs wraped in a CF and transformed in CF<List<Integer>>!");

Supplier<Stream<Integer>> nrsSource = () -> mem.join().stream();

Integer max = nrsSource.get().max(Integer::compare).get();
out.println("Nrs traversed to get max = " + max);
long maxOccurrences = nrsSource.get().filter(max::equals).count();
out.println("Nrs traversed to count max occurrences = " + maxOccurrences);
```

The following output results from the execution of the previous code:

> Stream nrs created!  
> 1, 0, 4, 6, 0, 6, 6, 3, 1, 2, Nrs wraped in a CF and transformed in CF<List<Integer>>!  
> Nrs traversed to get max = 6  
> Nrs traversed to count occurrences of max = 3  

Note, when the resulting stream (i.e. `nrsSource.get()`)
is traversed by the `max()` operation, the stream from
data source `nrs` has already been computed by the
`collect()` operation resulting in the output:
`1, 0, 4, 6, 0, 6, 6, 3, 1, 2,`.
So, instead of executing just 2 traversals to compute
2 queries: the maximum value and the number of occurrences
of the maximum value; we are performing one more traversal
first that is not used in none of the end queries.

## Approach 3 -- Memoize and replay on-demand  

Now we propose a third approach where we memoize items
only when they are accessed by a traversal.
Later an item may be retrieved from: 1) the `mem` or
2) the data source, depending on whether it has already
been requested by a previous operation, or not.
These two streams are expressed by the following
stream concatenation: 

```
() -> Stream.concat(mem.stream(), StreamSupport.stream(srcIter, false))
```

This supplier produces a new stream resulting from the
concatenation of two other streams: one from the `mem`
(i.e. `mem.stream()`) and other from the data source
(i.e. `stream(srcIter, false)`).
When the stream from `mem` is empty or an operation
finishes traversing it, then it will proceed in the
second stream built from the `srcIter`.
The `srcIter` is an instance of an iterator (`MemoizeIter`)
that retrieves items from the data source and add
them to `mem`.
Considering that `src` is the data source then the
definition of [`MemoizeIter`][11] is according to the
following implementation of the [`memoize()`][12] method:

```java
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
    }
    MemoizeIter srcIter = new MemoizeIter();
    return () -> concat(mem.stream(), stream(srcIter, false));
}
```

We could also build a stream from an iterator
implementing the `Iterator` interface, but that is
not the iteration approach followed  by `Stream`,
which would require a conversion of that iterator to
a `Spliterator`. 
For that reason, we implement `MemoizeIter` with the
`Spliterator` interface to avoid further indirections.
Since ` Spliterator` requires the implementation of
several abstract methods related with partition
capabilities for parallel processing, we choose to
extend `AbstractSpliterator` instead, which permit
limited parallelism and need only implement a
single method.
The method `tryAdvance(action)` is the core iteration
method, which performs the given `action` for each
remaining element sequentially until all elements have
been processed.
So, on each iteration it adds the current `item` to
the internal `mem` and retrieves that item to the
consumer `action`:

```java
item -> { 
        mem.add(item);
        action.accept(item);
}
```

Yet, this solution does not allow concurrent
iterations on a stream resulting from the
concatenation, while the source has not been
entirely consumed. 
When the stream from the source accesses a
new item and adds it to the `mem` list it will
invalidate any iterator in progress on this list.
Consider the following example where we get two
iterators from a stream of integers memoized with
the `memoize()` method.
We get two items (i.e. `1` and `2`) from the first
stream (`iter1`) and then we get a second stream
(`iter2`) which is composed by one stream with the
previous two items (i.e. `1` and `2`) and other
stream from the source. 
After that we get the third item from the first
stream (`iter1`) which is added to the internal
`mem` and thus invalidates the internal stream of
`iter2`.
So when we get an item of `iter2` we get a
`ConcurrentModificationException`.

```java
Supplier<Stream<Integer>> nrs = memoize(IntStream.range(1, 10).boxed());
Spliterator<Integer> iter1 = nrs.get().spliterator();
iter1.tryAdvance(out::println); // > 1
iter1.tryAdvance(out::println); // > 2
Spliterator<Integer> iter2 = nrs.get().spliterator();
iter1.tryAdvance(out::println); // > 3
iter2.forEachRemaining(out::print); // throws ConcurrentModificationException
System.out.println(); 
```

To avoid this scenario and allow concurrent iterations,
instead of the `concat()` we will use an alternative
solution where the supplier resulting from `memoize()`
always return a new `Spliterator` based stream which
accesses items from `mem` or from data source. 
It takes the decision on whether to read `mem` or data
source on-demand when the `tryAdvance()` is invoked.
To that end our solution compromises two entities:
`Recorder` and `MemoizeIter`.
The `Recorder` reads items from source iterator (i.e.
`srcIter`); store them in internal buffer (i.e. `mem`) and
pass them to a consumer. 
The `MemoizeIter` is a random access iterator that gets
items from the `Recorder`, which in turn gets those items
from the internal buffer (i.e. `mem`) or from the source
(i.e. `srcIter`). 
The resulting stream pipeline creates a chain of:

```
dataSrc ----> srcIter ----> Recorder ----> MemoizeIter ----> stream
                              ^
                              |
                 mem <--------|
```

In the following listing we present the implementation of
[`replay()`][13] utility method that creates a supplier
responsible for chaining the above stream pipeline:

```java
public static <T> Supplier<Stream<T>> replay(Supplier<Stream<T>> dataSrc) {
    final Recorder<T> rec = new Recorder<>(dataSrc);
    return () -> {
        // MemoizeIter starts on index 0 and reads data from srcIter or
        // from an internal mem replay Recorder.
        Spliterator<T> iter = rec.memIterator();
        return stream(iter, false);
    };
}

static class Recorder<T> {
    final Supplier<Stream<T>> dataSrc;
    Spliterator<T> srcIter = dataSrc.get().spliterator();
    long estimateSize = srcIter.estimateSize();
    final List<T> mem = new ArrayList<>();
    boolean hasNext = true;

    public Recorder(Supplier<Stream<T>> dataSrc) {
        this.dataSrc= dataSrc;
    }

    public synchronized boolean getOrAdvance(int index, Consumer<? super T> cons) {
        if (index < mem.size()) {
            // If it is in mem then just get if from the corresponding index.
            cons.accept(mem.get(index));
            return true;
        } else if (hasNext)
            // If not in mem then advance the srcIter iterator
            hasNext = srcIter.tryAdvance(item -> {
                mem.add(item);
                cons.accept(item);
            });
        return hasNext;
    }

    public Spliterator<T> memIterator() { return new MemoizeIter(); }

    class MemoizeIter extends Spliterators.AbstractSpliterator<T>  {
        int index = 0;
        public MemoizeIter(){
            super(estimateSize, srcIter.characteristics());
        }
        public boolean tryAdvance(Consumer<? super T> cons) {
            return getOrAdvance(index++, cons);
        }
        public Comparator<? super T> getComparator() {
            return srcIter.getComparator();
        }
    }
}
```

For each data source we have a single instance
of `Recorder` and one instance of `MemoizeIter`
per stream created by the supplier.
Since, the `getOrAdvance()`of  Recorder may be
invoked by different instances of `MemoizeIter`
then we made this method synchronized to guarantee
that just one resulting stream will get a new item
from the source iterator.
This implementation solves the requirement of
concurrent iterations on the same data source.

## Conclusion 

Reusing a stream is a realistic need which should
not only be circumscribed to 1) redo the computation
to the data source, or 2) collect the intermediate
result.
For example, a lazy intersection operation requires
multiple traversals of the same stream, but it is
not mandatory that the second stream be fully traversed. 

```java
nrs.filter(n -> others.anyMatch(n::equals))
```

Although, we need to traverse `others` multiple times,
there are some cases where we do not need to traverse it
to the end. 
Considering that all items from stream `nrs` always
match an item at the beginning of the stream `others`
then  we do not need to store all items of `others`
in an intermediate collection.

***

Reactive Streams implementations, such as [RxJava][14] or
[Reactor][15], provide similar feature to that one
proposed in [third approach](https://github.com/CCISEL/streams/tree/how_to_replay_java_streams#approach-3----memoize-and-replay- on-demand).
Thus, if using `Stream` is not a requirement, then with
reactor core we can simply convert the `Supplier<Stream<T>>`
to a `Flux<T>`, which already provides the utility
`cache()` method and henceforward use `Flux<T>`
operations rather than  `Stream<T>` operations.

Regarding our use case, where `lisbonTempsInMarch` is
a `CompletableFuture<Stream<Integer>>` with the result
of an HTTP request transformed in a sequence of temperatures
in Celsius, then we can perform both queries in the
following way with the `Flux` API:

```java
CompletableFuture<Stream<Integer>> lisbonTempsInMarch = Weather
        .getTemperaturesAsync(38.717, -9.133, of(2018, 4, 1), of(2018, 4, 30))
        .thenApply(IntStream::boxed);
Flux<Integer> cache = Flux
        .fromStream(lisbonTempsInMarch::join)
        .cache();
cache.reduce(Integer::max).subscribe(maxTemp -> …);
cache.filter(maxTemp::equals).count().subscribe(nrDaysWithMaxTemp  -> …);
```

Due to the asynchronous nature of `Flux` the result
of a terminal operation such as `reduce()` or `count`
also produce an asynchronous result in an instance of
`Mono` (i.e. the reactor equivalent to `CompletableFuture`)
which can be followed up later through the `subscribe()`
method.

## Appendix -- Streams Revisited

Here we present a set of essential notions about
Java streams taken from the article [Java Streams][16]
of Bryan Goets

> All stream computations share a common structure: They have a _stream source_, zero or more _intermediate operations_, and a single _terminal operation_. The elements of a stream can be object references (`Stream<String>`) or they can be primitive integers (`IntStream`), longs (`LongStream`), or doubles (`DoubleStream`).

> Constructing a stream source doesn't compute the elements of the stream, but instead captures how to find the elements when necessary. Similarly, invoking an intermediate operation doesn't perform any computation on the elements; it merely adds another operation to the end of the stream description. Only when the terminal operation is invoked does the pipeline actually perform the work — compute the elements, apply the intermediate operations, and apply the terminal operation.

> Intermediate operations are always _lazy_: Invoking an intermediate operation merely sets up the next stage in the stream pipeline but doesn't initiate any work.

> For sequential execution, Streams constructs a "machine" — a chain of `Consumer` objects whose structure matches that of the pipeline structure.

  [1]: https://stackoverflow.com/questions/36255007/is-there-any-way-to-reuse-a-stream-in-java-8
  [2]: https://stackoverflow.com/questions/23860533/copy-a-stream-to-avoid-stream-has-already-been-operated-upon-or-closed
  [3]: https://stackoverflow.com/questions/22918207/java-8-stream-illegalstateexception
  [4]: https://developer.worldweatheronline.com/api/historical-weather-api.aspx
  [5]: https://github.com/AsyncHttpClient/async-http-client
  [6]: https://en.wikipedia.org/wiki/Futures_and_promises
  [7]: https://docs.oracle.com/javase/10/docs/api/java/util/concurrent/CompletableFuture.html
  [8]: https://github.com/CCISEL/streams/blob/master/src/test/java/org/streams/test/Weather.java
  [9]: https://github.com/CCISEL/streams/blob/master/src/test/java/org/streams/test/Weather.java#L47
  [10]: https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html#StreamOps
  [11]: https://github.com/CCISEL/streams/blob/master/src/test/java/org/streams/test/MemoizeTest.java#L60
  [12]: https://github.com/CCISEL/streams/blob/master/src/test/java/org/streams/test/MemoizeTest.java#L57
  [13]: https://github.com/CCISEL/streams/blob/master/src/main/java/org/streams/Replayer.java#L41
  [14]: https://github.com/ReactiveX/RxJava
  [15]: https://github.com/reactor/reactor-core
  [16]: https://www.ibm.com/developerworks/java/library/j-java-streams-1-brian-goetz/
