# streams

[![Build Status](https://sonarcloud.io/api/project_badges/measure?project=com.github.javasync%3Astreamemo&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.github.javasync%3Astreamemo)
[![Maven Central Version](https://img.shields.io/maven-central/v/com.github.javasync/streamemo.svg)](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22streamemo%22)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.github.javasync%3Astreamemo&metric=coverage)](https://sonarcloud.io/component_measures?id=com.github.javasync%3Astreamemo&metric=Coverage)


Java streams utility methods for memoization

## How to replay Java streams?

Need to use your streams over and over again? Let's cover three different 
approaches, their benefits, and their pitfalls when recycling Java streams.

Read more here https://dzone.com/articles/how-to-replay-java-streams

## Usage

```java
Random rnd = new Random();
Stream<Integer> nrs = Stream.generate(() -> rnd.nextInt(99));
Supplier<Stream<Integer>> nrsSrc = Replayer.replay(nrs);

nrsSrc.get().limit(11).map(n -> n + ",").forEach(out::print); // e.g. 88,18,78,75,98,68,15,14,25,54,22,
out.println();
nrsSrc.get().limit(11).map(n -> n + ",").forEach(out::print); // Print the same previous numbers
```

Note that you cannot achieve this result with an intermediate 
collection because `nrs` is an infinite stream.
Thus trying to collect `nrs` incurs in an infinite loop.
Only on-demand memoization like `replay()` achieves this approach. 

## Installation

First, in order to include it to your Maven project,
simply add this dependency:

```xml
<dependency>
    <groupId>com.github.javasync</groupId>
    <artifactId>streamemo</artifactId>
    <version>1.0.1</version>
</dependency>
```

To add a dependency using Gradle:

```
dependencies {
  compile 'com.github.javasync:streamemo:1.0.0'
}
```

## Changelog

### 1.0.1 (August, 2019)

Add the ability to close the original stream.
Now the the `onClose()` method of a stream from the `Supplier.get()`
will trigger a call to the original Stream's onClose() method. 
Contribution from shollander issue #2. 

### 1.0.0 (June, 2018)

First release according to the article "How to Reuse Java Streams" published on DZone at Jun. 12, 18