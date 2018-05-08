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

  [1]: https://stackoverflow.com/questions/36255007/is-there-any-way-to-reuse-a-stream-in-java-8
  [2]: https://stackoverflow.com/questions/23860533/copy-a-stream-to-avoid-stream-has-already-been-operated-upon-or-closed
  [3]: https://stackoverflow.com/questions/22918207/java-8-stream-illegalstateexception
  