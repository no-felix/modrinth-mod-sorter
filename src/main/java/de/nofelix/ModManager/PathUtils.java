package de.nofelix.ModManager;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Utility class for path normalization and related helpers.
 * Part of the Modrinth Mod Sorter application.
 * Version 1.2
 */
public class PathUtils {
    private static final Logger logger = Logger.getLogger(PathUtils.class.getName());
    
    /**
     * Normalizes a file path by resolving relative paths and ensuring consistent format.
     * 
     * @param path The path to normalize
     * @return The normalized path as a string
     */
    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        
        // Replace backslashes with forward slashes for consistency
        String normalized = path.replace('\\', '/');
        
        // Handle ~/ at the beginning (user home directory)
        if (normalized.startsWith("~/") || normalized.startsWith("~\\")) {
            String userHome = System.getProperty("user.home");
            normalized = userHome + normalized.substring(1);
        }
        
        try {
            // Convert to canonical path to resolve .. and . segments
            File file = new File(normalized);
            Path resolvedPath = file.isAbsolute() ? 
                Paths.get(normalized).normalize() : 
                Paths.get(System.getProperty("user.dir"), normalized).normalize();
            
            return resolvedPath.toString().replace('\\', '/');
        } catch (Exception e) {
            logger.warning("Failed to normalize path: " + path + " - " + e.getMessage());
            return path; // Return original if normalization fails
        }
    }
}
