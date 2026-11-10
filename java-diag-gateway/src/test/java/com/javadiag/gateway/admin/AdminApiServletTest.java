package com.javadiag.gateway.admin;

import com.javadiag.gateway.config.GatewayConfig;
import com.javadiag.gateway.json.JsonSupport;
import com.javadiag.gateway.registry.InMemoryRegistry;
import com.javadiag.gateway.registry.ProbeInstance;
import com.javadiag.gateway.registry.ProbeJvmApp;
import com.javadiag.gateway.registry.ProbeStatus;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminApiServletTest {

    private Server jetty;
    private int port;
    private InMemoryRegistry registry;
    private HttpClient client;
    private GatewayConfig gatewayConfig;

    @BeforeEach
    void setUp() throws Exception {
        registry = new InMemoryRegistry(Duration.ofSeconds(90));
        registry.register(new ProbeInstance(
                "10.0.0.1:8765",
                "order",
                "10.0.0.1",
                8765,
                "http://10.0.0.1:8765",
                "/sse",
                "/mcp",
                "probe-tok",
                Map.of("env", "prod"),
                List.of(new ProbeJvmApp(
                        "order.jar",
                        9001L,
                        "com.example.OrderMain",
                        "order",
                        "java -jar order.jar",
                        1_700_000_000_000L,
                        List.of(8080))),
                "0.1.0",
                Instant.now(),
                ProbeStatus.HEALTHY));

        JsonSupport json = JsonSupport.createDefault();
        AdminCredentials creds = new AdminCredentials("admin", "java-diag");
        gatewayConfig =
                GatewayConfig.from(
                        Map.of(
                                GatewayConfig.ENV_AUTH_TOKEN, "agent-secret",
                                GatewayConfig.ENV_REGISTRY_REGISTER_TOKEN, "reg-secret"),
                        java.util.List.of());

        jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        jetty.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.setSessionHandler(new SessionHandler());
        context.addServlet(
                new ServletHolder(new AdminApiServlet(registry, json, creds, gatewayConfig)), "/admin/api/*");
        jetty.setHandler(context);
        jetty.start();
        port = connector.getLocalPort();

        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (jetty != null) {
            jetty.stop();
        }
    }

    @Test
    void loginFailureAndSuccessThenInstances() throws Exception {
        assertEquals(401, get("/admin/api/instances").statusCode());

        HttpResponse<String> denied = postJson("/admin/api/login", """
                {"username":"admin","password":"wrong"}
                """);
        assertEquals(401, denied.statusCode());
        assertTrue(denied.body().contains("invalid"));

        HttpResponse<String> login = postJson("/admin/api/login", """
                {"username":"admin","password":"java-diag"}
                """);
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("\"ok\":true"));

        HttpResponse<String> instances = get("/admin/api/instances");
        assertEquals(200, instances.statusCode());
        assertTrue(instances.body().contains("10.0.0.1:8765"));
        assertTrue(instances.body().contains("\"count\":1"));
        assertTrue(instances.body().contains("\"authToken\":\"probe-tok\""));
        assertFalse(instances.body().contains("\"probeToken\""));
        assertTrue(instances.body().contains("\"apps\""));
        assertTrue(instances.body().contains("\"name\":\"order.jar\""));
        assertTrue(instances.body().contains("\"pid\":9001"));
    }

    @Test
    void sessionExposesAgentTokenOnlyWhenLoggedIn() throws Exception {
        HttpResponse<String> anon = get("/admin/api/session");
        assertEquals(200, anon.statusCode());
        assertTrue(anon.body().contains("\"authenticated\":false"));
        assertFalse(anon.body().contains("agent-secret"));
        assertFalse(anon.body().contains("agentAuthToken"));

        assertEquals(200, postJson("/admin/api/login", """
                {"username":"admin","password":"java-diag"}
                """).statusCode());

        HttpResponse<String> authed = get("/admin/api/session");
        assertEquals(200, authed.statusCode());
        assertTrue(authed.body().contains("\"authenticated\":true"));
        assertTrue(authed.body().contains("agent-secret"));
        assertTrue(authed.body().contains("\"agentAuthConfigured\":true"));
        assertTrue(authed.body().contains(AdminApiServlet.MCP_SERVER_NAME));
    }

    @Test
    void instancesRequiresSession() throws Exception {
        HttpClient fresh = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/admin/api/instances"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> resp = fresh.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
