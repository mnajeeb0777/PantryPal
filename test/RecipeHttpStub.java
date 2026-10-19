import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/**
 * In-memory HTTP transport for deterministic recipe API tests, with no sockets.
 * @author PantryPal team (GenAI-assisted tests)
 * @version 2026.10.18
 */
final class RecipeHttpStub extends HttpClient
{
    /** Next response JSON. */
    String body = "{\"meals\":null}";
    /** Next HTTP status. */
    int status = 200;
    /** Last request for URI and timeout assertions. */
    HttpRequest lastRequest;
    /** Optional simulated transport failure. */
    IOException failure;
    /** Whether the request simulates cancellation. */
    boolean interrupt;

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request,
        HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException
    {
        lastRequest = request;
        if (failure != null) { throw failure; }
        if (interrupt) { throw new InterruptedException("Cancelled"); }
        // The importer exclusively uses BodyHandlers.ofString().
        T content = (T)body;
        return new HttpResponse<T>()
        {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return request; }
            @Override public Optional<HttpResponse<T>> previousResponse()
            { return Optional.empty(); }
            @Override public HttpHeaders headers()
            { return HttpHeaders.of(Collections.emptyMap(), (a, b) -> true); }
            @Override public T body() { return content; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return request.uri(); }
            @Override public Version version() { return Version.HTTP_1_1; }
        };
    }

    @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
    @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
    @Override public Redirect followRedirects() { return Redirect.NEVER; }
    @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
    @Override public SSLContext sslContext()
    {
        try { return SSLContext.getDefault(); }
        catch (java.security.NoSuchAlgorithmException exception)
        { throw new IllegalStateException(exception); }
    }
    @Override public SSLParameters sslParameters() { return new SSLParameters(); }
    @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
    @Override public Version version() { return Version.HTTP_1_1; }
    @Override public Optional<Executor> executor() { return Optional.empty(); }
    @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, HttpResponse.BodyHandler<T> handler)
    { throw new UnsupportedOperationException("Synchronous stub only."); }
    @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, HttpResponse.BodyHandler<T> handler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler)
    { throw new UnsupportedOperationException("Synchronous stub only."); }
}
