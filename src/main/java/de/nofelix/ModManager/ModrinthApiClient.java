package de.nofelix.ModManager;

import java.net.http.*;
import java.net.URI;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for interacting with the Modrinth API.
 * Part of the Modrinth Mod Sorter application.
 * Version 1.2
 */
public class ModrinthApiClient {
	private static final Logger logger = Logger.getLogger(ModrinthApiClient.class.getName());
	private static final String USER_AGENT = "ModrinthModSorter/1.2";
	private static final int REQUEST_TIMEOUT_SECONDS = 15;
	private static final String API_BASE_URL = "https://api.modrinth.com/v2";
	
	private final HttpClient client;
	
	/**
	 * Creates a new Modrinth API client with default configuration.
	 */
	public ModrinthApiClient() {
		this.client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	}

	/**
	 * Asynchronously fetches mod information from the Modrinth API.
	 * 
	 * @param filename Mod filename
	 * @param slug Modrinth project slug
	 * @return CompletableFuture with ModInfo object
	 */
	public CompletableFuture<ModInfo> fetchModInfoAsync(String filename, String slug) {
		// Skip API call if slug is missing or invalid
		if (slug == null || slug.equals("null") || slug.isBlank()) {
			logger.info(String.format("[%s] No valid slug provided, skipping API call", filename));
			return CompletableFuture.completedFuture(
				new ModInfo(filename, slug, "", "", "not_found", "not_found")
			);
		}
		
		String projectUrl = String.format("%s/project/%s", API_BASE_URL, slug);
		logger.fine(String.format("[%s] Requesting data from: %s", filename, projectUrl));
		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(projectUrl))
				.header("User-Agent", USER_AGENT)
				.timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
				.GET()
				.build();
				
		return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(resp -> {
					int statusCode = resp.statusCode();
					
					if (statusCode == 200) {
						String json = resp.body();
						String title = ModFileManager.extractJsonField(json, "title");
						String clientSide = ModFileManager.extractJsonField(json, "client_side");
						String serverSide = ModFileManager.extractJsonField(json, "server_side");
						String url = "https://modrinth.com/mod/" + slug;
						
						logger.info(String.format("[%s] Successfully retrieved data for %s: %s/%s", 
							filename, slug, clientSide, serverSide));
							
						return new ModInfo(filename, slug, title, url, clientSide, serverSide);
					} else if (statusCode == 404) {
						logger.warning(String.format("[%s] Mod slug '%s' not found on Modrinth (404)", 
							filename, slug));
							
						return new ModInfo(filename, slug, "", "", "not_found", "not_found");
					} else {
						logger.warning(String.format("[%s] API request for %s failed with status code %d", 
							filename, slug, statusCode));
							
						return new ModInfo(filename, slug, "", "", "error", "error");
					}
				})
				.exceptionally(e -> {
					Throwable cause = e.getCause();
					String errorMsg;
					
					if (cause instanceof ConnectException) {
						errorMsg = "Connection failed - check your internet connection";
					} else if (cause instanceof TimeoutException) {
						errorMsg = "Request timed out after " + REQUEST_TIMEOUT_SECONDS + " seconds";
					} else if (cause instanceof HttpTimeoutException) {
						errorMsg = "HTTP request timed out";
					} else {
						errorMsg = e.getMessage();
					}
					
					logger.log(Level.WARNING, String.format("[%s] Error fetching data for %s: %s", 
						filename, slug, errorMsg), e);
						
					return new ModInfo(filename, slug, "", "", "error", "error");
				});
	}
}