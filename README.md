# Modrinth Mod Sorter

A JavaFX application for managing Minecraft mods between client and server instances, with Modrinth API integration.

## Features

- **Mod File Management**: Organize and track Minecraft mods
- **Modrinth Integration**: Fetch mod compatibility data from Modrinth
- **Client/Server Separation**: Split mods between client and server based on compatibility
- **Automatic Version Detection**: Detects version updates of installed mods
- **Modern UI**: Clean, responsive interface with error highlighting

## Requirements

- Java 21 or higher
- Minecraft mods organized in a directory

## Installation

1. Download the latest release JAR from the [Releases](https://github.com/yourusername/modrinth-mod-sorter/releases) page
2. Run the JAR: `java -jar modrinth-mod-sorter-1.2.jar`

## Configuration

The application uses a properties file for configuration, editable in the Settings dialog:

- `mods.dir`: Directory containing mod files
- `client.output.dir`: Directory for client-compatible mods
- `server.output.dir`: Directory for server-compatible mods
- `client.backup.dir`: Backup directory for client mods
- `server.backup.dir`: Backup directory for server mods
- `mod.csv`: CSV file storing mod information
- `mod.csv.backup`: Backup for the CSV file

## Usage

1. **Reload CSV**: Load mod data from CSV file
2. **Enrich Mod List**: Fetch mod data from Modrinth API
3. **Save Changes**: Save changes to the CSV file
4. **Prepare Server/Client Mods**: Copy mods to server and client directories based on compatibility

### Notes

- **Mod Slugs**: Enter the Modrinth "slug" (project identifier) for each mod in the table. Automatic slug detection may be added in a future version.
- **Highlighted Issues**: Mods with missing or invalid slugs are highlighted in red.
- **Editable Table**: Edit the "Slug" column directly in the table.

## Building from Source

```bash
git clone https://github.com/no-felix/modrinth-mod-sorter/tree/main
cd modrinth-mod-sorter
mvn clean package
java -jar target/modrinth-mod-sorter-1.2.jar
```

## Changelog

### Version 1.2
- Modernized UI with custom styling
- Improved error handling and reporting
- Added status updates during operations
- Improved management of .connector directories
- Enhanced directory handling and backup procedures

### Version 1.1
- Added JavaFX graphical user interface
- Editable table for mod management
- Highlighting of mods with missing/invalid slugs
- Improved mod version detection
- Save changes from the UI

### Version 1.0
- Initial release with basic functionality (CLI only)

## License

[MIT License](LICENSE)

## Acknowledgments

- [Modrinth](https://modrinth.com/) for the API
- [OpenCSV](http://opencsv.sourceforge.net/) for CSV handling
- [JavaFX](https://openjfx.io/) for the UI