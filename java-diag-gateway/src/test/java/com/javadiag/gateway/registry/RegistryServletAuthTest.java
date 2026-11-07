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

    @Test
    void derivesInstanceIdFromMcpBaseUrlWhenOmitted() throws Exception {
        HttpResponse<String> resp = post("/registry/register", """
                {"serviceName":"svc","mcpBaseUrl":"http://10.0.1.11:8765","probeToken":"p"}
                """);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("10.0.1.11:8765"));
        assertTrue(registry.get("10.0.1.11:8765").isPresent());
    }

    @Test
    void requiresRegisterTokenWhenConfigured() throws Exception {
        jetty.stop();
        JsonSupport json = JsonSupport.createDefault();
        jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        jetty.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(
                new ServletHolder(new RegistryServlet(registry, json, java.util.Optional.of("reg-secret"))),
                "/registry/*");
        jetty.setHandler(context);
        jetty.start();
        port = connector.getLocalPort();

        HttpResponse<String> denied = post("/registry/register", """
                {"instanceId":"x","mcpBaseUrl":"http://127.0.0.1:8765"}
                """, null);
        assertEquals(401, denied.statusCode());

        HttpResponse<String> ok = post("/registry/register", """
                {"instanceId":"x","mcpBaseUrl":"http://127.0.0.1:8765"}
                """, "reg-secret");
        assertEquals(200, ok.statusCode());
        assertEquals(1, registry.size());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return post(path, body, null);
    }

    private HttpResponse<String> post(String path, String body, String registryToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (registryToken != null) {
            builder.header("Authorization", "Bearer " + registryToken);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
