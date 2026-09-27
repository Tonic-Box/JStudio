package com.tonic.ui.update;

import java.io.InterruptedIOException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.function.LongConsumer;

/** Downloads a release jar, checking its SHA-256 when published, and starts the external updater that swaps the jar and relaunches. */
public final class UpdateInstaller
{

    private static final String USER_AGENT = "JStudio-UpdateChecker";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Downloads the update jar to a temp file, verifying its SHA-256 when the release provides one.
     *
     * @param info the release to download
     * @param progress receives the percentage downloaded, or -1 when the total size is unknown; may be null
     * @return the downloaded jar, a temp file
     * @throws IOException if the release has no jar asset, the download fails or the checksum does not match; the temp file is deleted
     */
    public Path download(UpdateInfo info, LongConsumer progress) throws IOException
    {
        if (info.getJarUrl() == null)
        {
            throw new IOException("Release " + info.getTag() + " has no JStudio.jar asset");
        }
        Path temp = Files.createTempFile("JStudio-" + info.getTag() + "-", ".jar");
        try
        {
            downloadTo(info.getJarUrl(), temp, progress);
            if (info.getSha256Url() != null)
            {
                verifySha256(temp, fetchString(info.getSha256Url()));
            }
        }
        catch (IOException e)
        {
            Files.deleteIfExists(temp);
            throw e;
        }
        return temp;
    }

    /**
     * Starts the updater from the downloaded jar to replace the running jar and relaunch; the caller must exit right after so the jar is released.
     *
     * @param downloadedJar the verified update jar
     * @throws IOException if not running from a jar or the updater cannot be started
     */
    public void applyAndRestart(Path downloadedJar) throws IOException
    {
        File target = AppVersion.runningJar();
        if (target == null)
        {
            throw new IOException("Not running from a jar; cannot self-update");
        }
        Path log = Files.createTempFile("jstudio-update-", ".log");
        ProcessBuilder builder = new ProcessBuilder(javaBinary(), "-cp", downloadedJar.toString(), "com.tonic.cli.Updater", target.getAbsolutePath(), downloadedJar.toString())
                .redirectOutput(Redirect.to(log.toFile()))
                .redirectError(Redirect.appendTo(log.toFile()));
        builder.start();
    }

    private void downloadTo(String url, Path dest, LongConsumer progress) throws IOException
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200)
            {
                throw new IOException("HTTP " + response.statusCode() + " downloading update");
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(dest))
            {
                byte[] buffer = new byte[16384];
                long read = 0;
                int n;
                while ((n = in.read(buffer)) != -1)
                {
                    if (Thread.currentThread().isInterrupted())
                    {
                        throw new InterruptedIOException("Download cancelled");
                    }
                    out.write(buffer, 0, n);
                    read += n;
                    if (progress != null)
                    {
                        progress.accept(total > 0 ? read * 100 / total : -1L);
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw e;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Download cancelled");
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    private String fetchString(String url) throws IOException
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
            {
                throw new IOException("HTTP " + response.statusCode() + " fetching checksum");
            }
            return response.body();
        }
        catch (IOException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    private void verifySha256(Path file, String expectedRaw) throws IOException
    {
        String expected = expectedRaw.trim().split("\\s+")[0];
        String actual = sha256Hex(file);
        if (!actual.equalsIgnoreCase(expected))
        {
            throw new IOException("SHA-256 mismatch (expected " + expected + ", got " + actual + ")");
        }
    }

    private String sha256Hex(Path file) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file))
            {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1)
                {
                    digest.update(buffer, 0, n);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest())
            {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        }
        catch (Exception e)
        {
            throw new IOException("Could not hash update jar", e);
        }
    }

    private static String javaBinary()
    {
        String home = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return home + File.separator + "bin" + File.separator + (windows ? "java.exe" : "java");
    }
}
