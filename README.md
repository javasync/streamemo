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
We will incrementally combine these solutions: 1) using
a `Supplier<Stream<…>>`, 2) memoizing the resulting stream
into a collection to avoid multiple roundtrips to data
source, and 3) memoizing and replaying items on demand
into and from an internal buffer.

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

![weather-data-source](https://user-images.githubusercontent.com/578217/39763003-ea99b4a2-52d3-11e8-86ed-a6ffae325cab.jpg)

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


  [1]: https://stackoverflow.com/questions/36255007/is-there-any-way-to-reuse-a-stream-in-java-8
  [2]: https://stackoverflow.com/questions/23860533/copy-a-stream-to-avoid-stream-has-already-been-operated-upon-or-closed
  [3]: https://stackoverflow.com/questions/22918207/java-8-stream-illegalstateexception
  [4]: https://developer.worldweatheronline.com/api/historical-weather-api.aspx
  [5]: https://github.com/AsyncHttpClient/async-http-client
  [6]: https://en.wikipedia.org/wiki/Futures_and_promises
  [7]: https://docs.oracle.com/javase/10/docs/api/java/util/concurrent/CompletableFuture.html
  [8]: https://github.com/CCISEL/streams/blob/master/src/test/java/org/streams/test/Weather.java
  [9]: https://github.com/CCISEL/streams/blob/master/src/test/java/org/streams/test/Weather.java#L47
  