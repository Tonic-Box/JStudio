package com.tonic.ui.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Queries the GitHub Releases API for JStudio's latest release. All failures (offline, rate limit,
 * malformed response) resolve to {@code null} so update checks never surface as errors on their own.
 */
public final class UpdateChecker {

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/Tonic-Box/JStudio/releases/latest";
    private static final String JAR_ASSET = "JStudio.jar";
    private static final String SHA256_ASSET = "JStudio.jar.sha256";
    private static final String USER_AGENT = "JStudio-UpdateChecker";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * @return the latest release, or {@code null} on any failure.
     */
    public UpdateInfo fetchLatest() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? parse(response.body()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private UpdateInfo parse(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String tag = optString(root, "tag_name");
        if (tag == null) {
            return null;
        }
        int version = AppVersion.parse(tag);
        if (version < 0) {
            return null;
        }

        String jarUrl = null;
        String sha256Url = null;
        JsonElement assets = root.get("assets");
        if (assets != null && assets.isJsonArray()) {
            for (JsonElement element : assets.getAsJsonArray()) {
                JsonObject asset = element.getAsJsonObject();
                String name = optString(asset, "name");
                String url = optString(asset, "browser_download_url");
                if (JAR_ASSET.equals(name)) {
                    jarUrl = url;
                } else if (SHA256_ASSET.equals(name)) {
                    sha256Url = url;
                }
            }
        }
        return new UpdateInfo(tag, version, optString(root, "html_url"), jarUrl, sha256Url);
    }

    private static String optString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }
}
