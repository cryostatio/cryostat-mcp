/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.mcp.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.cryostat.mcp.CryostatMCP;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(CryostatAuthorizationHttpTest.AuthorizationProfile.class)
class CryostatAuthorizationHttpTest {

    private static final String NAMESPACE = "authorization-test";
    private static final String STATIC_AUTHORIZATION = "Bearer static-token";
    private static final ConcurrentLinkedQueue<DownstreamRequest> DOWNSTREAM_REQUESTS =
            new ConcurrentLinkedQueue<>();

    private static HttpServer cryostatServer;

    @InjectMock CryostatInstanceDiscovery discovery;
    @Inject CryostatMCPInstanceManager instanceManager;

    @TestHTTPResource("/mcp")
    URI mcpEndpoint;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final AtomicInteger requestIds = new AtomicInteger(2);

    @BeforeAll
    static void startCryostatServer() throws IOException {
        cryostatServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cryostatServer.createContext("/health", CryostatAuthorizationHttpTest::handleHealth);
        cryostatServer.createContext(
                "/api/v4/graphql", CryostatAuthorizationHttpTest::handleGraphQL);
        cryostatServer.createContext(
                "/api/v4.1/metrics/reports", CryostatAuthorizationHttpTest::handleMetrics);
        cryostatServer.start();
    }

    @AfterAll
    static void stopCryostatServer() {
        cryostatServer.stop(0);
    }

    @BeforeEach
    void setUp() {
        DOWNSTREAM_REQUESTS.clear();
        requestIds.set(2);
        String applicationUrl = "http://127.0.0.1:" + cryostatServer.getAddress().getPort();
        CryostatInstance instance =
                new CryostatInstance(
                        "authorization-test", NAMESPACE, applicationUrl, Set.of(NAMESPACE));
        CryostatInstance secondInstance =
                new CryostatInstance(
                        "authorization-test-2",
                        "authorization-test-2",
                        applicationUrl,
                        Set.of("authorization-test-2"));
        when(discovery.findByNamespace(NAMESPACE)).thenReturn(Optional.of(instance));
        when(discovery.getAllInstances()).thenReturn(List.of(instance, secondInstance));
    }

    @Test
    void forwardsPerInvocationAuthorizationUsingCachedClients() throws Exception {
        String sessionId = initializeMcpSession();

        assertSuccessfulToolCall(
                callTool(
                        sessionId,
                        "Bearer rest-token",
                        "Bearer mcp-client-token",
                        "getHealth",
                        "{\"namespace\":\"" + NAMESPACE + "\"}"));
        CryostatMCP firstInstance = instanceManager.createInstance(NAMESPACE);

        assertSuccessfulToolCall(
                callTool(
                        sessionId,
                        "Bearer graphql-token",
                        null,
                        "listTargets",
                        "{\"namespace\":\""
                                + NAMESPACE
                                + "\",\"ids\":[],\"targetIds\":[],\"names\":[],\"labels\":[],"
                                + "\"useAuditLog\":false}"));
        assertSame(firstInstance, instanceManager.createInstance(NAMESPACE));

        assertSuccessfulToolCall(
                callTool(
                        sessionId,
                        null,
                        "Bearer mcp-client-only",
                        "getHealth",
                        "{\"namespace\":\"" + NAMESPACE + "\"}"));

        assertSuccessfulToolCall(
                callTool(
                        sessionId,
                        null,
                        "Bearer mcp-client-only",
                        "listTargets",
                        "{\"namespace\":\""
                                + NAMESPACE
                                + "\",\"ids\":[],\"targetIds\":[],\"names\":[],\"labels\":[],"
                                + "\"useAuditLog\":false}"));

        assertSuccessfulToolCall(
                callTool(
                        sessionId,
                        "Bearer fan-out-token",
                        null,
                        "scrapeGlobalMetrics",
                        "{\"minTargetScore\":0.5}"));

        assertSuccessfulToolCall(
                callTool(
                        sessionId,
                        "Bearer graphql-fan-out-token",
                        null,
                        "listGlobalTargets",
                        "{\"names\":[],\"labels\":[],\"useAuditLog\":false}"));

        assertEquals(
                List.of(
                        new DownstreamRequest("/health", "Bearer rest-token"),
                        new DownstreamRequest("/api/v4/graphql", "Bearer graphql-token"),
                        new DownstreamRequest("/health", STATIC_AUTHORIZATION),
                        new DownstreamRequest("/api/v4/graphql", STATIC_AUTHORIZATION),
                        new DownstreamRequest("/api/v4.1/metrics/reports", "Bearer fan-out-token"),
                        new DownstreamRequest("/api/v4.1/metrics/reports", "Bearer fan-out-token"),
                        new DownstreamRequest("/api/v4/graphql", "Bearer graphql-fan-out-token"),
                        new DownstreamRequest("/api/v4/graphql", "Bearer graphql-fan-out-token")),
                List.copyOf(DOWNSTREAM_REQUESTS));
    }

    @Test
    void permitsRequestsWithoutAnyDownstreamCredential() throws Exception {
        String sessionId = initializeMcpSession();
        CryostatMCPInstanceManager unwrappedManager = ClientProxy.unwrap(instanceManager);
        unwrappedManager.staticAuthorizationHeader = Optional.empty();
        try {
            assertSuccessfulToolCall(
                    callTool(
                            sessionId,
                            null,
                            null,
                            "listTargets",
                            "{\"namespace\":\""
                                    + NAMESPACE
                                    + "\",\"ids\":[],\"targetIds\":[],\"names\":[],\"labels\":[],"
                                    + "\"useAuditLog\":false}"));
        } finally {
            unwrappedManager.staticAuthorizationHeader = Optional.of(STATIC_AUTHORIZATION);
        }

        assertEquals(
                List.of(new DownstreamRequest("/api/v4/graphql", null)),
                List.copyOf(DOWNSTREAM_REQUESTS));
    }

    @Test
    void isolatesCredentialsAcrossConcurrentRequests() throws Exception {
        String sessionId = initializeMcpSession();
        int requestCount = 8;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<HttpResponse<String>>> responses =
                    executor.invokeAll(
                            IntStream.range(0, requestCount)
                                    .<Callable<HttpResponse<String>>>mapToObj(
                                            index -> {
                                                boolean graphQL = index % 2 == 0;
                                                return () ->
                                                        callTool(
                                                                sessionId,
                                                                "Bearer concurrent-" + index,
                                                                null,
                                                                graphQL
                                                                        ? "listTargets"
                                                                        : "getHealth",
                                                                graphQL
                                                                        ? "{\"namespace\":\""
                                                                                + NAMESPACE
                                                                                + "\",\"ids\":[],"
                                                                                + "\"targetIds\":[],"
                                                                                + "\"names\":[],"
                                                                                + "\"labels\":[],"
                                                                                + "\"useAuditLog\":false}"
                                                                        : "{\"namespace\":\""
                                                                                + NAMESPACE
                                                                                + "\"}");
                                            })
                                    .toList());
            for (Future<HttpResponse<String>> response : responses) {
                assertSuccessfulToolCall(response.get());
            }
        }

        Set<String> expected =
                IntStream.range(0, requestCount)
                        .mapToObj(index -> "Bearer concurrent-" + index)
                        .collect(Collectors.toSet());
        Set<String> actual =
                DOWNSTREAM_REQUESTS.stream()
                        .map(DownstreamRequest::authorization)
                        .collect(Collectors.toSet());
        assertEquals(requestCount, DOWNSTREAM_REQUESTS.size());
        assertEquals(expected, actual);
    }

    private String initializeMcpSession() throws Exception {
        HttpResponse<String> initializeResponse =
                post(
                        """
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"authorization-test","version":"1"}}}
                        """,
                        null,
                        null,
                        null);
        assertEquals(200, initializeResponse.statusCode());
        String sessionId = initializeResponse.headers().firstValue("Mcp-Session-Id").orElseThrow();

        HttpResponse<String> initializedResponse =
                post(
                        """
                        {"jsonrpc":"2.0","method":"notifications/initialized"}
                        """,
                        sessionId,
                        null,
                        null);
        assertTrue(
                initializedResponse.statusCode() == 200 || initializedResponse.statusCode() == 202);
        return sessionId;
    }

    private HttpResponse<String> callTool(
            String sessionId,
            String cryostatAuthorization,
            String authorization,
            String toolName,
            String arguments)
            throws Exception {
        return post(
                "{\"jsonrpc\":\"2.0\",\"id\":"
                        + requestIds.getAndIncrement()
                        + ",\"method\":\"tools/call\",\"params\":{"
                        + "\"name\":\""
                        + toolName
                        + "\",\"arguments\":"
                        + arguments
                        + "}}",
                sessionId,
                cryostatAuthorization,
                authorization);
    }

    private HttpResponse<String> post(
            String body, String sessionId, String cryostatAuthorization, String authorization)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(mcpEndpoint)
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json, text/event-stream")
                        .header("Content-Type", "application/json");
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        if (cryostatAuthorization != null) {
            builder.header(CryostatAuthorization.PASSTHROUGH_HEADER, cryostatAuthorization);
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertSuccessfulToolCall(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"result\""), response.body());
        assertFalse(response.body().contains("\"error\""), response.body());
        assertFalse(response.body().contains("\"isError\":true"), response.body());
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        recordRequest(exchange);
        respond(
                exchange,
                """
                {"cryostatVersion":"4.2.0","dashboardConfigured":false,"dashboardAvailable":false,"datasourceConfigured":false,"datasourceAvailable":false,"reportsConfigured":false,"reportsAvailable":false,"build":null}
                """);
    }

    private static void handleGraphQL(HttpExchange exchange) throws IOException {
        recordRequest(exchange);
        respond(exchange, "{\"data\":{\"targetNodes\":[]}}");
    }

    private static void handleMetrics(HttpExchange exchange) throws IOException {
        recordRequest(exchange);
        respond(exchange, "test_metric 1\n", "text/plain");
    }

    private static void recordRequest(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        DOWNSTREAM_REQUESTS.add(
                new DownstreamRequest(
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization")));
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, body, "application/json");
    }

    private static void respond(HttpExchange exchange, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record DownstreamRequest(String path, String authorization) {}

    public static class AuthorizationProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("k8s.mux.authorization.header", STATIC_AUTHORIZATION);
        }
    }
}
