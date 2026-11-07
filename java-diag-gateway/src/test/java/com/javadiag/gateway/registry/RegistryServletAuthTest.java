package com.javadiag.gateway.registry;

import com.javadiag.gateway.json.JsonSupport;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryServletAuthTest {

    private Server jetty;
    private int port;
    private InMemoryRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new InMemoryRegistry(Duration.ofSeconds(90));
        JsonSupport json = JsonSupport.createDefault();
        jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        jetty.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(
                new ServletHolder(new RegistryServlet(registry, json)),
                "/registry/*");
        jetty.setHandler(context);
        jetty.start();
        port = connector.getLocalPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (jetty != null) {
            jetty.stop();
        }
    }

    @Test
    void acceptsRegisterWithoutToken() throws Exception {
        HttpResponse<String> resp = post("/registry/register", """
                {"instanceId":"a","serviceName":"svc","mcpBaseUrl":"http://127.0.0.1:8765","probeToken":"p"}
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"ok\":true"));
        assertEquals(1, registry.size());
        assertFalse(registry.get("a").orElseThrow().toPublicMap().containsKey("probeToken"));
    }

    @Test
    void rejectsNonHttpMcpBaseUrl() throws Exception {
        HttpResponse<String> resp = post("/registry/register", """
                {"instanceId":"a","mcpBaseUrl":"file:///etc/passwd"}
                """);
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("http"));
        assertEquals(0, registry.size());
    }

    @Test
    void acceptsHeartbeatAndDeregisterWithoutToken() throws Exception {
        assertEquals(200, post("/registry/register", """
                {"instanceId":"b","mcpBaseUrl":"http://127.0.0.1:8765"}
                """).statusCode());
        assertEquals(200, post("/registry/heartbeat", "{\"instanceId\":\"b\"}").statusCode());
        assertEquals(200, post("/registry/deregister", "{\"instanceId\":\"b\"}").statusCode());
        assertEquals(0, registry.size());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
