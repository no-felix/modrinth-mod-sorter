package de.nofelix.ModManager;

/**
 * Immutable record representing a Minecraft mod and its Modrinth API data.
 * Part of the Modrinth Mod Sorter application.
 * Version 1.2
 */
public record ModInfo(
    String filename, 
    String slug, 
    String title, 
    String url, 
    String clientSide, 
    String serverSide
) {
    /**
     * Creates a new ModInfo with validation and default values for nulls.
     */
    public ModInfo {
        // Validate and provide defaults for null values
        filename = filename != null ? filename : "";
        slug = slug != null ? slug : "";
        title = title != null ? title : "";
        url = url != null ? url : "";
        clientSide = clientSide != null ? clientSide : "unknown";
        serverSide = serverSide != null ? serverSide : "unknown";
    }

    @Override
    public String toString() {
        return String.format("ModInfo[filename=%s, slug=%s, title=%s]",
            filename, slug, title);
    }
}