package com.codahale.metrics.httpclient5;

import com.codahale.metrics.impl.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.impl.Timer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InstrumentedHttpClientsTest {
    private final HttpClientMetricNameStrategy metricNameStrategy =
            mock(HttpClientMetricNameStrategy.class);
    private final MetricRegistryListener registryListener =
            mock(MetricRegistryListener.class);
    private final MetricRegistry metricRegistry = new MetricRegistry();
    private final HttpClient client =
            InstrumentedHttpClients.custom(metricRegistry, metricNameStrategy).disableAutomaticRetries().build();

    @Before
    public void setUp() {
        metricRegistry.addListener(registryListener);
    }

    @Test
    public void registersExpectedMetricsGivenNameStrategy() throws Exception {
        final HttpGet get = new HttpGet("http://example.com?q=anything");
        final String metricName = "some.made.up.metric.name";

        when(metricNameStrategy.getNameFor(any(), any(HttpRequest.class)))
                .thenReturn(metricName);

        client.execute(get);

        verify(registryListener).onTimerAdded(eq(metricName), any(Timer.class));
    }

    @Test
    public void registersExpectedExceptionMetrics() throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);

        final HttpGet get = new HttpGet("http://localhost:" + httpServer.getAddress().getPort() + "/");
        final String requestMetricName = "request";
        final String exceptionMetricName = "exception";

        httpServer.createContext("/", HttpExchange::close);
        httpServer.start();

        when(metricNameStrategy.getNameFor(any(), any(HttpRequest.class)))
                .thenReturn(requestMetricName);
        when(metricNameStrategy.getNameFor(any(), any(Exception.class)))
                .thenReturn(exceptionMetricName);

        try {
            client.execute(get);
            fail();
        } catch (NoHttpResponseException expected) {
            assertThat(metricRegistry.getMeters()).containsKey("exception");
        } finally {
            httpServer.stop(0);
        }
    }
}
