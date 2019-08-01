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

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class Weather{
    static final String KEY = "e841239881974af5b1480345192207";
    static final String HOST = "http://api.worldweatheronline.com/";
    static final String PATH = HOST + "premium/v1/past-weather.ashx?q=%s,%s&date=%s&enddate=%s&tp=24&format=csv&key=%s";
    static final Pattern NEWLINE = Pattern.compile("\\n");
    static final Pattern COMMA = Pattern.compile(",");

    public static CompletableFuture<IntStream> getTemperaturesAsync(
            double lat,
            double log,
            LocalDate from,
            LocalDate to)
    {
        AsyncHttpClient asyncHttpClient = asyncHttpClient();
        CompletableFuture<Stream<String>> csv = asyncHttpClient
                .prepareGet(String.format(PATH, lat, log, from, to, KEY))
                .execute()
                .toCompletableFuture()
                .thenApply(Weather::checkResponseStatus)
                .thenApply(Response::getResponseBody)
                .thenApply(NEWLINE::splitAsStream);
        boolean[] isEven = {true};
        CompletableFuture<IntStream> temps = csv.thenApply(str -> str
                .filter(w -> !w.startsWith("#"))     // Filter comments
                .skip(1)                             // Skip line: Not Available
                .filter(l -> isEven[0] = !isEven[0]) // Filter Even line
                .map(line -> COMMA.splitAsStream(line).skip(2).findFirst().get()) // Extract temperature in Celsius
                .mapToInt(Integer::parseInt));// Convert to Integer
        return temps.thenApply(__ -> {
            close(asyncHttpClient);
            return __;
        });
    }

    private static Response checkResponseStatus(Response resp) {
        if(resp.getStatusCode() != 200)
            throw new IllegalStateException(String.format(
                "%s: %s -- %s", resp.getStatusCode(), resp.getStatusText(), resp.getResponseBody()
        ));
        return resp;
    }

    private static void close(AsyncHttpClient asyncHttpClient) {
        try {
            asyncHttpClient.close();
        } catch (IOException e) {
            new UncheckedIOException(e);
        }
    }
}