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
Supplier<Stream<String>> nrs = () -> generate(() -> rnd.nextInt(99)).map(Object::toString);
Supplier<Stream<String>> nrsReplay = Replayer.replay(nrs);
nrsReplay.get().limit(11).forEach(out::println);
nrsReplay.get().limit(11)..forEach(out::println); // Print the same previous numbers
```
