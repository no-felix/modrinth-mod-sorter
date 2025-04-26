package de.nofelix.ModManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main JavaFX application for the Modrinth Mod Sorter.
 * Version 1.2
 */
public class ModManagerApp extends Application {
    private static final Logger logger = Logger.getLogger(ModManagerApp.class.getName());
    private final ObservableList<ModInfo> modData = FXCollections.observableArrayList();
    private TableView<ModInfo> tableView;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Properties config;
    
    // Use only the filename for configuration, not the full path
    private static final String CONFIG_FILE_NAME = "application.properties";
    private static final String USER_CONFIG_FILE = CONFIG_FILE_NAME; // For local user settings
    
    private static final String APP_TITLE = "Modrinth Mod Sorter v1.2";
    private static final String DEFAULT_STATUS = "Ready";
    private static final String APP_STYLE_SHEET = "modmanager-style.css";

    @Override
    public void start(Stage primaryStage) {
        loadConfig();
        
        // Setup main stage
        primaryStage.setTitle(APP_TITLE);
        primaryStage.getIcons().add(
                new Image(getClass().getResourceAsStream("/icon/icon.png"))
        );
        
        // Create components
        tableView = createTableView();
        progressBar = createProgressBar();
        statusLabel = createStatusLabel();
        
        // Create UI layout
        BorderPane layout = createLayout(tableView, statusLabel, progressBar);
        
        Scene scene = new Scene(layout, 1200, 700);
        
        // Apply styles using the constant
        URL cssResource = getClass().getResource("/" + APP_STYLE_SHEET);
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
            System.out.println("CSS loaded from: " + cssResource);
        } else {
            System.err.println("Warning: Could not load CSS from " + APP_STYLE_SHEET);
        }
                                    
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Initial data load
        onReload();
    }

    private BorderPane createLayout(TableView<ModInfo> tableView, Label statusLabel, ProgressBar progressBar) {
        BorderPane border = new BorderPane();
        border.setPadding(new Insets(10));
        
        // Top area - Header
        Label headerLabel = new Label("Modrinth Mod Sorter");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        headerLabel.setPadding(new Insets(0, 0, 10, 0));
        
        // Center area - Table with mod data
        border.setTop(headerLabel);
        border.setCenter(tableView);
        
        // Bottom area - Status, buttons, progress
        HBox buttonBar = createButtonBar();
        
        VBox bottomBar = new VBox(5);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        
        HBox statusBar = new HBox(10);
        statusBar.getChildren().addAll(statusLabel, progressBar);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        
        bottomBar.getChildren().addAll(buttonBar, statusBar);
        border.setBottom(bottomBar);
        
        return border;
    }
    
    private HBox createButtonBar() {
        Button enrichBtn = new Button("Enrich Mod List");
        Button prepareBtn = new Button("Prepare Server/Client Mods");
        Button reloadBtn = new Button("Reload CSV");
        Button saveBtn = new Button("Save Changes");
        Button configBtn = new Button("Settings");
        
        // Set button styles
        List<Button> buttons = List.of(enrichBtn, prepareBtn, reloadBtn, saveBtn, configBtn);
        for (Button btn : buttons) {
            btn.getStyleClass().add("action-button");
        }
        
        // Set button actions
        enrichBtn.setOnAction(e -> onEnrich());
        prepareBtn.setOnAction(e -> onPrepare());
        reloadBtn.setOnAction(e -> onReload());
        saveBtn.setOnAction(e -> onSave());
        configBtn.setOnAction(e -> showConfigDialog());
        
        HBox buttonBar = new HBox(10, enrichBtn, prepareBtn, reloadBtn, saveBtn, configBtn);
        buttonBar.setPadding(new Insets(10));
        
        return buttonBar;
    }
    
    private Label createStatusLabel() {
        Label label = new Label(DEFAULT_STATUS);
        label.setPrefHeight(20);
        return label;
    }
    
    private ProgressBar createProgressBar() {
        ProgressBar bar = new ProgressBar();
        bar.setVisible(false);
        bar.setPrefWidth(200);
        return bar;
    }

    private TableView<ModInfo> createTableView() {
        TableView<ModInfo> table = new TableView<>(modData);
        table.setEditable(true);
        
        // Use modern resize policy (not deprecated)
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        
        // Create columns explicitly to avoid type warnings
        TableColumn<ModInfo, String> filenameCol = createCol("Filename", ModInfo::filename);
        TableColumn<ModInfo, String> slugCol = createEditableCol("Slug", ModInfo::slug);
        TableColumn<ModInfo, String> titleCol = createCol("Title", ModInfo::title);
        TableColumn<ModInfo, String> urlCol = createCol("URL", ModInfo::url);
        TableColumn<ModInfo, String> clientSideCol = createCol("Client Side", ModInfo::clientSide);
        TableColumn<ModInfo, String> serverSideCol = createCol("Server Side", ModInfo::serverSide);
        
        // Add columns to table
        table.getColumns().addAll(
            Arrays.asList(filenameCol, slugCol, titleCol, urlCol, clientSideCol, serverSideCol)
        );
        
        // Set column widths as percentages
        filenameCol.prefWidthProperty().bind(table.widthProperty().multiply(0.25));
        slugCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15));
        titleCol.prefWidthProperty().bind(table.widthProperty().multiply(0.25));
        urlCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15));
        clientSideCol.prefWidthProperty().bind(table.widthProperty().multiply(0.10));
        serverSideCol.prefWidthProperty().bind(table.widthProperty().multiply(0.10));

        // Add row styling for rows with missing or unresolved slugs
        table.setRowFactory(tv -> new TableRow<>() {
	        @Override
	        protected void updateItem(ModInfo item, boolean empty) {
		        super.updateItem(item, empty);
		        if (item == null || empty) {
			        setStyle("");
		        } else {
			        // Check if slug is missing or couldn't be resolved
			        boolean hasIssue = item.slug() == null || item.slug().isBlank() ||
					        "not_found".equals(item.clientSide()) ||
					        "not_found".equals(item.serverSide()) ||
					        "error".equals(item.clientSide()) ||
					        "error".equals(item.serverSide());

			        if (hasIssue) {
				        setStyle("-fx-background-color: #ffcccc;"); // Light red background
				        getStyleClass().add("issue-row");
			        } else {
				        setStyle("");
				        getStyleClass().remove("issue-row");
			        }
		        }
	        }
        });

        return table;
    }

    private TableColumn<ModInfo, String> createCol(String title, java.util.function.Function<ModInfo, String> getter) {
        TableColumn<ModInfo, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleStringProperty(getter.apply(data.getValue())));
        return col;
    }

    private TableColumn<ModInfo, String> createEditableCol(String title, java.util.function.Function<ModInfo, String> getter) {
        TableColumn<ModInfo, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleStringProperty(getter.apply(data.getValue())));
        col.setEditable(true);

        // Make the column editable
        col.setCellFactory(column -> new TableCell<>() {
	        private TextField textField;

	        @Override
	        public void startEdit() {
		        super.startEdit();

		        if (textField == null) {
			        createTextField();
		        }

		        setText(null);
		        setGraphic(textField);
		        textField.selectAll();
	        }

	        @Override
	        public void cancelEdit() {
		        super.cancelEdit();
		        setText(getItem());
		        setGraphic(null);
	        }

	        @Override
	        protected void updateItem(String item, boolean empty) {
		        super.updateItem(item, empty);

		        if (empty) {
			        setText(null);
			        setGraphic(null);
		        } else {
			        if (isEditing()) {
				        if (textField != null) {
					        textField.setText(item);
				        }
				        setText(null);
				        setGraphic(textField);
			        } else {
				        setText(item);
				        setGraphic(null);
			        }
		        }
	        }

	        private void createTextField() {
		        textField = new TextField(getItem());
		        textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
		        textField.setOnAction(e -> commitEdit(textField.getText()));
		        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
			        if (!isNowFocused) {
				        commitEdit(textField.getText());
			        }
		        });
	        }
        });

        // Handle edit commit
        col.setOnEditCommit(event -> {
            ModInfo oldItem = event.getRowValue();
            int index = event.getTablePosition().getRow();

            // Create a new ModInfo with the updated slug
            ModInfo newItem = new ModInfo(
                oldItem.filename(),
                event.getNewValue(),
                oldItem.title(),
                oldItem.url(),
                oldItem.clientSide(),
                oldItem.serverSide()
            );

            // Update the observable list
            modData.set(index, newItem);
            updateStatus("Modified entry: " + oldItem.filename());
        });

        return col;
    }

    private void onReload() {
        Task<List<ModInfo>> task = new Task<>() {
            @Override
            protected List<ModInfo> call() throws Exception {
                updateMessage("Loading mod data...");
                String modCsv = PathUtils.normalize(config.getProperty("mod.csv"));
                String modsDir = PathUtils.normalize(config.getProperty("mods.dir"));
                String sep = ModFileManager.detectSeparator(modCsv);

                // Load existing CSV rows
                List<String[]> rows = ModFileManager.readCsv(modCsv, sep);

                // Build a map from baseName -> CSV row
                Map<String, String[]> baseMap = new HashMap<>();
                boolean isHeader = true;
                for (String[] row : rows) {
                    if (isHeader) { isHeader = false; continue; }
                    String fn = row.length > 0 ? row[0] : "";
                    String base = fn.replaceFirst("(?i)(-\\d.*)?\\.jar$", "");
                    baseMap.put(base, row);
                }

                AtomicInteger updated = new AtomicInteger(0);
                AtomicInteger added = new AtomicInteger(0);
                
                boolean changed = false;
                // Scan directory for .jar files
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(modsDir), "*.jar")) {
                    for (Path jar : stream) {
                        String filename = jar.getFileName().toString();
                        String base = filename.replaceFirst("(?i)(-\\d.*)?\\.jar$", "");

                        if (baseMap.containsKey(base)) {
                            // version bump: update existing row's filename
                            String[] existing = baseMap.get(base);
                            if (!existing[0].equals(filename)) {
                                existing[0] = filename;
                                changed = true;
                                updated.incrementAndGet();
                                updateMessage("Updated mod filename: " + filename);
                            }
                        } else {
                            // brand new mod
                            rows.add(new String[]{filename, "", "", "", "", ""});
                            changed = true;
                            added.incrementAndGet();
                            updateMessage("Added new mod: " + filename);
                        }
                    }
                }

                ModListManager manager = new ModListManager();
                if (changed) {
                    // write back updated CSV (with bumped filenames or new entries)
                    updateMessage("Saving changes to CSV...");
                    List<ModInfo> enriched = manager.enrichModList(rows);
                    ModFileManager.writeCsv(modCsv, enriched);
                    updateMessage(String.format("Updated %d mods, added %d new mods", updated.get(), added.get()));
                    return enriched;
                } else {
                    // no structural changes, just enrich in-memory
                    updateMessage("Loading existing mod data...");
                    return manager.enrichModList(rows);
                }
            }
        };

        runTask(task, "Mod data loaded successfully", progress -> {
            modData.setAll(progress);
            updateStatus(String.format("Loaded %d mods", progress.size()));
        });
    }

    private void onEnrich() {
        Task<List<ModInfo>> task = new Task<>() {
            @Override
            protected List<ModInfo> call() throws Exception {
                updateMessage("Fetching data from Modrinth API...");
                String modCsv = PathUtils.normalize(config.getProperty("mod.csv"));
                String sep = ModFileManager.detectSeparator(modCsv);
                List<String[]> rows = ModFileManager.readCsv(modCsv, sep);
                ModListManager manager = new ModListManager();
                List<ModInfo> enriched = manager.enrichModList(rows);
                updateMessage("Saving enriched mod data...");
                ModFileManager.writeCsv(modCsv, enriched);
                return enriched;
            }
        };

        runTask(task, "Mod list enriched and saved", progress -> {
            modData.setAll(progress);
            updateStatus(String.format("Enriched %d mods with Modrinth data", progress.size()));
        });
    }

    private void onPrepare() {
        Task<Map<String, Integer>> task = new Task<>() {
            @Override
            protected Map<String, Integer> call() throws Exception {
                updateMessage("Reading mod data...");
                String modCsv = PathUtils.normalize(config.getProperty("mod.csv"));
                String sep = ModFileManager.detectSeparator(modCsv);
                List<String[]> rows = ModFileManager.readCsv(modCsv, sep);
                ModListManager manager = new ModListManager();
                
                updateMessage("Enriching mod data...");
                List<ModInfo> mods = manager.enrichModList(rows);
                
                updateMessage("Preparing directories...");
                Map<String, Integer> results = new HashMap<>();
                
                // Count mods by side
                int clientMods = 0;
                int serverMods = 0;
                
                for (ModInfo mod : mods) {
                    String clientSide = mod.clientSide().toLowerCase();
                    String serverSide = mod.serverSide().toLowerCase();
                    
                    if ("required".equals(clientSide) || "optional".equals(clientSide) ||
                        "not_found".equals(clientSide)) {
                        clientMods++;
                    }
                    
                    if ("required".equals(serverSide) || "optional".equals(serverSide) ||
                        "not_found".equals(serverSide)) {
                        serverMods++;
                    }
                }
                
                updateMessage("Copying mod files...");
                manager.prepareServerAndClientMods(mods, config);
                
                results.put("clientMods", clientMods);
                results.put("serverMods", serverMods);
                
                return results;
            }
        };

        runTask(task, "Server and client mods prepared", progress -> {
            int clientMods = progress.get("clientMods");
            int serverMods = progress.get("serverMods");
            updateStatus(String.format("Prepared %d server mods and %d client mods", serverMods, clientMods));
        });
    }

    private void onSave() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Creating backup...");
                String modCsv = PathUtils.normalize(config.getProperty("mod.csv"));
                String backupFile = PathUtils.normalize(config.getProperty("mod.csv.backup"));

                // Create a backup of the CSV file
                try {
                    updateMessage("Backing up to " + backupFile);
                    ModFileManager.backupFile(modCsv, backupFile);
                } catch (IOException e) {
                    updateMessage("Failed to create backup: " + e.getMessage());
                }

                // Save the current state of modData to the CSV file
                updateMessage("Saving changes...");
                ModFileManager.writeCsv(modCsv, new ArrayList<>(modData));
                return null;
            }
        };

        runTask(task, "Changes saved successfully", progress -> {
            updateStatus("Saved mod data: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        });
    }

    /**
     * Generic method to run a task with proper UI updates
     * @param task The task to run
     * @param successMessage Message to show on success
     * @param successHandler Handler to process the result on success
     * @param <T> Type of the task result
     */
    private <T> void runTask(Task<T> task, String successMessage, java.util.function.Consumer<T> successHandler) {
        progressBar.setVisible(true);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        
        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            
            T result = task.getValue();
            successHandler.accept(result);
            showInfo(successMessage);
        });
        
        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            
            Throwable ex = task.getException();
            updateStatus("Error: " + ex.getMessage());
            showInfo("Operation failed: " + ex.getMessage());
        });
        
        new Thread(task).start();
    }

    private void showConfigDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Settings");
        dialog.getDialogPane().getStyleClass().add("config-dialog");
        
        // Create the form layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        // Get and sort config keys
        List<String> keys = new ArrayList<>(config.stringPropertyNames());
        keys.sort(String::compareTo);
        
        // Group settings by category
        List<String> pathSettings = keys.stream()
            .filter(k -> k.endsWith(".dir") || k.contains("path"))
            .toList();
        
        List<String> fileSettings = keys.stream()
            .filter(k -> k.endsWith(".csv") || k.endsWith(".bak"))
            .toList();
            
        List<String> otherSettings = keys.stream()
            .filter(k -> !pathSettings.contains(k) && !fileSettings.contains(k))
            .toList();
        
        Map<String, TextField> fields = new HashMap<>();
        
        int row = 0;
        
        // Add section headers and fields
        Label pathHeader = new Label("Directories");
        pathHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(pathHeader, 0, row++, 2, 1);
        
        for (String key : pathSettings) {
            addFormField(grid, key, fields, row++);
        }
        
        row++; // Add space
        Label fileHeader = new Label("Files");
        fileHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
        grid.add(fileHeader, 0, row++, 2, 1);
        
        for (String key : fileSettings) {
            addFormField(grid, key, fields, row++);
        }
        
        if (!otherSettings.isEmpty()) {
            row++; // Add space
            Label otherHeader = new Label("Other Settings");
            otherHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
            grid.add(otherHeader, 0, row++, 2, 1);
            
            for (String key : otherSettings) {
                addFormField(grid, key, fields, row++);
            }
        }
        
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType resetBtn = new ButtonType("Reset to Default", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, resetBtn, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                for (String key : keys) {
                    config.setProperty(key, fields.get(key).getText());
                }
                saveConfig();
            } else if (btn == resetBtn) {
                loadConfig(true);
                for (String key : keys) {
                    fields.get(key).setText(config.getProperty(key));
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    private void addFormField(GridPane grid, String key, Map<String, TextField> fields, int row) {
        Label label = new Label(key);
        TextField field = new TextField(config.getProperty(key));
        fields.put(key, field);
        
        // Make text fields expand to fill available space
        field.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(field, Priority.ALWAYS);
        
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    private void showInfo(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }

    private void loadConfig() { loadConfig(false); }
    
    private void loadConfig(boolean forceDefault) {
        config = new Properties();
        boolean loadedFromClasspath = false;
        
        // First try to load the local configuration file (for user settings)
        File localConfigFile = new File(USER_CONFIG_FILE);
        if (localConfigFile.exists() && !forceDefault) {
            try (InputStream in = new FileInputStream(localConfigFile)) {
                config.load(in);
                logger.info("Loaded configuration from local file: " + localConfigFile.getAbsolutePath());
                loadedFromClasspath = true;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load local config file: " + e.getMessage());
            }
        }
        
        // If no local configuration is found or forceDefault is true, load from classpath
        if (!loadedFromClasspath || forceDefault) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
                if (in != null) {
                    config.load(in);
                    logger.info("Loaded configuration from classpath: " + CONFIG_FILE_NAME);
                    loadedFromClasspath = true;
                } else {
                    logger.warning("Could not find configuration in classpath: " + CONFIG_FILE_NAME);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to load config from classpath: " + e.getMessage(), e);
                if (!forceDefault) showInfo("Failed to load config: " + e.getMessage());
            }
        }
        
        // If no configuration was found, set default values
        if (!loadedFromClasspath && !forceDefault) {
            setDefaultConfig();
            showInfo("Using default configuration. Please configure the application in the Settings dialog.");
        }
    }
    
    private void setDefaultConfig() {
        // Set default values for configuration
        config.setProperty("mods.dir", "./mods");
        config.setProperty("client.output.dir", "./client_mods");
        config.setProperty("server.output.dir", "./server_mods");
        config.setProperty("client.backup.dir", "./client_mods_backup");
        config.setProperty("server.backup.dir", "./server_mods_backup");
        config.setProperty("mod.csv", "modlist.csv");
        config.setProperty("mod.csv.backup", "modlist.csv.bak");
    }
    
    // When saving config, normalize all path values before storing
    private void saveConfig() {
        try (OutputStream out = new FileOutputStream(USER_CONFIG_FILE)) {
            // Normalize all path properties before saving
            for (String key : config.stringPropertyNames()) {
                if (key.endsWith(".dir") || key.endsWith(".csv") || key.endsWith(".backup") || key.contains("path")) {
                    config.setProperty(key, PathUtils.normalize(config.getProperty(key)));
                }
            }
            config.store(out, "Mod Manager Config");
            logger.info("Saved configuration to: " + USER_CONFIG_FILE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save config: " + e.getMessage(), e);
            showInfo("Failed to save config: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
