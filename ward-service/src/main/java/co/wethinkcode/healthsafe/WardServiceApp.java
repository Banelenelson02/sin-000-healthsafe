package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WardServiceApp {
    private static final String INGESTION_URL =
            System.getenv().getOrDefault("INGESTION_URL", "http://localhost:7030");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        // Read the cleaned ward data from ingestion-service rather than duplicating it.
        app.get("/wards", ctx -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(INGESTION_URL + "/wards"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            try {
                HttpResponse<String> response =
                        CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    ctx.status(502).json(java.util.Map.of(
                            "error", "Ingestion service returned status " + response.statusCode()));
                    return;
                }
                ctx.contentType("application/json").result(response.body());
            } catch (IOException e) {
                ctx.status(503).json(java.util.Map.of(
                        "error", "Ingestion service is unavailable"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ctx.status(503).json(java.util.Map.of(
                        "error", "Request to ingestion service was interrupted"));
            }
        });

        // TODO: GET /wards/{id}, departments lookup and ActiveMQ integration.
    }
}
