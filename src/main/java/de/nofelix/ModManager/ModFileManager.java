package de.nofelix.ModManager;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;
import java.util.Comparator;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParserBuilder;
import java.text.MessageFormat;

/**
 * Utility class for file operations related to mod management.
 * Handles CSV reading/writing, directory backup, and mod file copying.
 * Part of the Modrinth Mod Sorter application.
 * Version 1.2
 */
public class ModFileManager {
    private static final Logger logger = Logger.getLogger(ModFileManager.class.getName());
    private static final String CONNECTOR_DIR = ".connector";

    /**
     * Extracts a field value from a JSON string using regex.
     * @param json JSON string
     * @param field Field name to extract
     * @return Extracted value or empty string if not found
     */
    public static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Creates a backup copy of a file.
     * @param source Source file path
     * @param backup Backup file path
     * @throws IOException If an I/O error occurs
     */
    public static void backupFile(String source, String backup) throws IOException {
        try {
            Files.copy(Path.of(source), Path.of(backup), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Backup created: " + backup);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create backup: {0} -> {1}", new Object[]{source, backup});
            throw e;
        }
    }

    /**
     * Reads data from a CSV file.
     * @param file CSV file path
     * @param separator Column separator character
     * @return List of string arrays containing CSV data
     * @throws IOException If an I/O error occurs
     * @throws CsvValidationException If CSV validation fails
     */
    public static List<String[]> readCsv(String file, String separator) throws IOException, CsvValidationException {
        List<String[]> rows = new ArrayList<>();
        char sep = separator.charAt(0);
        
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(file))
                .withCSVParser(new CSVParserBuilder().withSeparator(sep).build())
                .build()) {
            String[] line;
            while ((line = reader.readNext()) != null) {
                rows.add(line);
            }
            logger.info(MessageFormat.format("Read {0} rows from CSV file: {1}", rows.size(), file));
            return rows;
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "CSV file not found: " + file, e);
            throw new IOException("CSV file could not be found: " + file, e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading CSV file: " + file, e);
            throw new IOException("Failed to read CSV file: " + file, e);
        } catch (CsvValidationException e) {
            logger.log(Level.SEVERE, "CSV validation error in file: " + file, e);
            throw new CsvValidationException("Invalid CSV format in file: " + file);
        }
    }

    /**
     * Writes mod information to a CSV file.
     * @param file CSV file path
     * @param mods List of ModInfo objects to write
     * @throws IOException If an I/O error occurs
     */
    public static void writeCsv(String file, List<ModInfo> mods) throws IOException {
        // Sort the mods alphabetically by filename
        List<ModInfo> sortedMods = new ArrayList<>(mods);
        sortedMods.sort(Comparator.comparing(ModInfo::filename, String.CASE_INSENSITIVE_ORDER));

        try (CSVWriter writer = new CSVWriter(new FileWriter(file), ';', CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {
            // Write header row
            writer.writeNext(new String[]{"filename","slug","modrinth_title","modrinth_project_url","client_side","server_side"});
            
            // Write data rows
            for (ModInfo mod : sortedMods) {
                writer.writeNext(new String[]{
                        mod.filename(),
                        mod.slug(),
                        mod.title(),
                        mod.url(),
                        mod.clientSide(),
                        mod.serverSide()
                });
            }
            
            logger.info(MessageFormat.format("Wrote {0} mods to CSV file: {1}", sortedMods.size(), file));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing CSV file: " + file, e);
            throw e;
        }
    }

    /**
     * Escapes special characters for CSV output.
     * @param s String to escape
     * @return Escaped string
     */
    public static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    /**
     * Detects the separator character used in a CSV file.
     * @param file CSV file path
     * @return Detected separator character as a string
     * @throws IOException If an I/O error occurs
     */
    public static String detectSeparator(String file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            if (header == null) return ",";
            if (header.contains("\t")) return "\t";
            if (header.contains(";")) return ";";
            return ",";
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to detect CSV separator for file: " + file, e);
            throw e;
        }
    }

    /**
     * Copies allowed mod files from source to destination directory.
     * @param allowed Set of allowed mod filenames
     * @param modsDir Source directory containing mods
     * @param outputDir Destination directory for copied mods
     * @throws IOException If an I/O error occurs
     */
    public static void copyModFiles(Set<String> allowed, String modsDir, String outputDir) throws IOException {
        int found = 0, all = 0;
        logger.info("Creating output directory: " + outputDir);
        Path outputPath = Path.of(outputDir);
        
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            throw new IOException("Failed to create output directory: " + outputDir, e);
        }

        // Check if .connector directory exists in output directory and preserve it
        Path connectorDir = outputPath.resolve(CONNECTOR_DIR);
        boolean connectorExists = Files.exists(connectorDir);
        if (connectorExists) {
            logger.info(".connector directory exists in output directory, will preserve it: " + connectorDir);
        }

        logger.info(MessageFormat.format("Copying mod files from {0} to {1}", modsDir, outputDir));
        try {
            Path modsPath = Path.of(modsDir);
            if (!Files.exists(modsPath)) {
                throw new IOException("Mods directory does not exist: " + modsDir);
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath, "*.jar")) {
                for (Path jar : stream) {
                    all++;
                    String fname = jar.getFileName().toString();
                    
                    if (allowed.contains(fname)) {
                        try {
                            Path target = Path.of(outputDir, fname);
                            Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
                            found++;
                            logger.info("Copied file: " + fname);
                        } catch (IOException e) {
                            logger.log(Level.SEVERE, "Failed to copy file: " + fname, e);
                            throw new IOException("Failed to copy mod file: " + fname + " to " + outputDir, e);
                        }
                    } else {
                        logger.fine("Skipping file (not in allowed list): " + fname);
                    }
                }
            }
        } catch (DirectoryIteratorException e) {
            logger.log(Level.SEVERE, "Error reading mods directory: " + modsDir, e);
            throw new IOException("Failed to read contents of mods directory: " + modsDir, e.getCause());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error accessing mods directory: " + modsDir, e);
            throw new IOException("Failed to access mods directory: " + modsDir, e);
        }
        
        logger.info(MessageFormat.format("Copied {0} of {1} mods to {2}", found, all, outputDir));
    }

    /**
     * Backs up and prepares a directory for mod files, preserving special directories like .connector.
     * @param targetDir Target directory to prepare
     * @param backupDir Backup directory for existing files
     * @throws IOException If an I/O error occurs
     */
    public static void backupAndPrepareDirectory(String targetDir, String backupDir) throws IOException {
        Path targetPath = Path.of(targetDir);
        Path backupPath = Path.of(backupDir);

        // Check if .connector directory exists in target directory
        Path connectorDir = targetPath.resolve(CONNECTOR_DIR);
        boolean connectorExists = Files.exists(connectorDir);

        if (Files.exists(targetPath)) {
            logger.info("Target directory exists: " + targetDir);

            // Backup existing files
            if (Files.exists(backupPath)) {
                logger.info("Deleting existing backup directory: " + backupDir);
                deleteDirectory(backupPath);
            }

            logger.info("Creating backup directory: " + backupDir);
            Files.createDirectories(backupPath);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetPath)) {
                for (Path file : stream) {
                    // Skip .connector directory during backup
                    if (file.getFileName().toString().equals(CONNECTOR_DIR)) {
                        logger.info("Skipping .connector directory during backup: " + file);
                        continue;
                    }
                    logger.info("Backing up file: " + file.getFileName());
                    Files.copy(file, backupPath.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // If .connector exists, move it to a temporary location
            Path tempConnectorDir = null;
            if (connectorExists) {
                logger.info(".connector directory exists, preserving it: " + connectorDir);
                tempConnectorDir = Path.of(targetDir + "_connector_temp");
                if (Files.exists(tempConnectorDir)) {
                    deleteDirectory(tempConnectorDir);
                }
                Files.createDirectories(tempConnectorDir);
                Files.move(connectorDir, tempConnectorDir.resolve(CONNECTOR_DIR), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Moved .connector to temporary location: " + tempConnectorDir.resolve(CONNECTOR_DIR));
            }

            // Safe directory cleanup with preservation of .connector
            try {
                deleteDirectoryContentsExcept(targetPath, CONNECTOR_DIR);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to clean directory contents: " + targetPath, e);
            }

            // Recreate the target directory
            logger.info("Recreating target directory: " + targetDir);
            Files.createDirectories(targetPath);

            // Move .connector back if it existed and was moved
            if (connectorExists && tempConnectorDir != null) {
                restoreConnectorDirectory(tempConnectorDir, connectorDir);
            }
        } else {
            logger.info("Target directory does not exist, creating it: " + targetDir);
            Files.createDirectories(targetPath);
        }
    }
    
    /**
     * Restores a .connector directory from a temporary location.
     * @param tempConnectorDir Temporary directory containing .connector
     * @param connectorDir Target location for .connector
     */
    private static void restoreConnectorDirectory(Path tempConnectorDir, Path connectorDir) {
        // Check if .connector already exists in target directory
        if (Files.exists(connectorDir)) {
            logger.info(".connector already exists in target directory, not moving it back");
            // Just delete the temporary directory
            try {
                deleteDirectory(tempConnectorDir);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to delete temporary connector directory", e);
            }
        } else {
            logger.info("Moving .connector back to target directory");
            try {
                Files.move(tempConnectorDir.resolve(CONNECTOR_DIR), connectorDir, StandardCopyOption.REPLACE_EXISTING);
                logger.info(".connector restored to: " + connectorDir);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to move .connector back", e);
                // Don't rethrow, we want to continue even if this fails
            }
            // Clean up the temporary directory
            try {
                deleteDirectory(tempConnectorDir);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to delete temporary directory", e);
                // Don't rethrow, we want to continue even if this fails
            }
        }
    }
    
    /**
     * Deletes a directory and all its contents.
     * @param directory Directory to delete
     * @throws IOException If an I/O error occurs
     */
    public static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            logger.info("Directory does not exist, skipping deletion: " + directory);
            return;
        }

        logger.info("Deleting directory contents: " + directory);
        deleteDirectoryContentsExcept(directory, null);
        
        // Now try to delete the empty directory itself
        try {
            Files.delete(directory);
            logger.info("Deleted directory: " + directory);
        } catch (DirectoryNotEmptyException e) {
            logger.warning("Directory not empty, cannot delete: " + directory);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to delete directory: " + directory, e);
            throw e;
        }
    }
    
    /**
     * Deletes contents of a directory except for a specified file/directory name.
     * @param directory Directory to clean
     * @param exceptFileName Name of file/directory to preserve (can be null)
     * @throws IOException If an I/O error occurs
     */
    private static void deleteDirectoryContentsExcept(Path directory, String exceptFileName) throws IOException {
        if (!Files.exists(directory)) {
            logger.warning("Cannot delete contents - directory does not exist: " + directory);
            return;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                // Skip the excepted file/directory if specified
                if (exceptFileName != null && path.getFileName().toString().equals(exceptFileName)) {
                    logger.info("Preserving: " + path);
                    continue;
                }

                if (Files.isDirectory(path)) {
                    deleteDirectory(path);
                } else {
                    try {
                        Files.delete(path);
                        logger.fine("Deleted file: " + path);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Failed to delete file: " + path, e);
                        throw new IOException("Failed to delete file: " + path + " (check permissions or if file is in use)", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error accessing directory: " + directory, e);
            throw new IOException("Failed to access directory contents: " + directory, e);
        }
    }
}
