package com.netgrif.maven.plugin.module;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * PetriflowPackageMojo is a Maven Mojo responsible for packaging Petriflow
 * applications into ZIP archives. It processes the input directories, creates
 * a manifest file, excludes files based on patterns, and generates the resulting
 * ZIP file with the specified attributes.
 * <p>
 * This class extends the AbstractMojo class to provide Maven plugin functionality.
 * <p>
 * Fields:
 * - project: The Maven project object representing the current project.
 * - inputDirectories: A list of directories to be processed for packaging.
 * - inputDirectory: The base directory for input files.
 * - outputDirectory: The directory where the output ZIP files will be stored.
 * - multiApplication: A flag indicating whether multiple applications are packaged.
 * - appId: The application ID to be included in the manifest.
 * - appName: The name of the application.
 * - appDescription: The description of the application.
 * - appVersion: The version of the application.
 * - appAuthor: The author of the application.
 * - exclude: A list of patterns to exclude files from packaging.
 * - zipPrefix: A prefix to be added to the generated ZIP file names.
 * <p>
 * Methods:
 * - execute(): Main entry point for the Mojo, responsible for executing the packaging logic.
 * - makeFlatName(Path appDir, Path file): Generates a flat file name from a given path,
 * replacing directory separators with underscores.
 * - makeFlatNetName(Path appDir, Path file): Generates a flat process name by stripping
 * XML extensions from file names.
 * - zipAppCustomFiles(Path appDir, List<Path> processFiles, File outputZip, String appId,
 * String appName, String appDescription, String appVersion, String appAuthor,
 * List<Pattern> excludePatterns): Packages selected files from the process directory
 * into a ZIP file.
 * - zipApp(Path appDir, File outputZip, String appId, String appName, String appDescription,
 * String appVersion, String appAuthor, List<Pattern> excludePatterns): Packages the
 * application folder, including the generated manifest and its processes, into a ZIP file.
 * - isExcluded(String netName, List<Pattern> patterns): Checks if a given process name
 * matches any of the exclusion patterns.
 * - getExclusionPatterns(): Retrieves the list of compiled patterns used for file exclusions.
 * - generateManifestXml(List<String> processNames, String appId, String appName,
 * String description, String version, String author): Generates the XML content for the
 * manifest file.
 * - getOrDefault(String userInput, String defaultValue): Returns the user-provided value if
 * it is not null or empty, otherwise returns the default value.
 * - getDefaultAuthor(): Attempts to determine a default author based on the Maven developers
 * configuration, falling back to "unknown" if unavailable.
 * - escapeXml(String input): Escapes special XML characters in the provided string.
 */
@Mojo(name = "package-petriflow", defaultPhase = LifecyclePhase.PACKAGE)
public class PetriflowPackageMojo extends AbstractMojo {


    @Component
    private MavenProject project;

    /**
     * A list of directories that contain source files to be processed for generating artifacts.
     * Each directory is expected to hold process definitions or related resources in XML format.
     * These directories are used as input locations for creating the output package.
     */
    @Parameter
    private List<File> inputDirectories;

    /**
     * Specifies the input directory containing the Petri net files.
     * The default value for this directory is set to "${project.basedir}/src/main/resources/petriNets".
     * This directory is required and must exist before the execution of the plugin.
     * <p>
     * Property: inputDirectory
     * Default Value: ${project.basedir}/src/main/resources/petriNets
     * Required: true
     */
    @Parameter(property = "inputDirectory", defaultValue = "${project.basedir}/src/main/resources/petriNets", required = true)
    private File inputDirectory;

    /**
     * Specifies the output directory where the generated ZIP files will be stored.
     * This directory is used as the destination for application packages created during the build process.
     * The default value is `${project.build.directory}/petriflow-zips`, but can be customized via the
     * `outputDirectory` property.
     * <p>
     * The parameter is marked as required, ensuring users provide or use the default value during execution.
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/petriflow-zips", required = true)
    private File outputDirectory;

    /**
     * Specifies whether the plugin should process multiple applications within the input directory.
     * If set to {@code true}, the input directory is expected to contain subdirectories, each representing
     * an individual application to be processed. If set to {@code false}, the entire input directory is
     * treated as a single application. The default value is {@code false}.
     */
    @Parameter(property = "multiApplication", defaultValue = "false")
    private boolean multiApplication;

    /**
     * Identifier of the application being processed.
     * <p>
     * This variable is used as the unique application ID during the packaging process and is included
     * in the application's manifest file. The default value is derived from the project's artifact ID.
     */
    @Parameter(property = "appId", defaultValue = "${project.artifactId}")
    private String appId;

    /**
     * The name of the application to be built and included in the generated manifest file.
     * If not specified, this variable defaults to the name of the Maven project.
     * <p>
     * This value is utilized during the creation of the manifest.xml file to specify the
     * application title and is included in logging or debugging details for easier identification.
     * <p>
     * Configuration:
     * - The value can be customized using the `appName` Maven property.
     * - Defaults to the Maven project's name if not provided.
     * <p>
     * Maven Property: appName
     * Default Value: The name of the current Maven project (${project.name})
     */
    @Parameter(property = "appName", defaultValue = "${project.name}")
    private String appName;

    /**
     * Represents the description of the application being processed or packaged.
     * This value is used in manifest generation and other application metadata operations.
     * It can be specified through a Maven parameter or defaults to the project's description.
     */
    @Parameter(property = "appDescription", defaultValue = "${project.description}")
    private String appDescription;

    /**
     * Represents the version of the application to be packaged or processed.
     * This value is used in the generation of the manifest file and related operations.
     * <p>
     * The default value is derived from the Maven project's version property
     * if not explicitly provided by the user.
     *
     * @property appVersion
     * @defaultValue ${project.version}
     */
    @Parameter(property = "appVersion", defaultValue = "${project.version}")
    private String appVersion;

    /**
     * The `appAuthor` variable represents the author of the application being processed.
     * The value is typically retrieved from the developers defined in the Maven project's configuration.
     * The default value is set to the name of the first developer listed in the project's developers section.
     * If this information is not available, a fallback value is provided by other methods in the implementation.
     */
    @Parameter(property = "appAuthor", defaultValue = "${project.developers[0].name}")
    private String appAuthor;

    /**
     * A list of patterns used to exclude specific process files from being included
     * in the generated application archive. Each entry in the list is treated as
     * a regular expression that is matched against the names of the processes.
     * If a process name matches any of the patterns in this list, it will be excluded.
     * <p>
     * This parameter allows users to control which processes are omitted from the final
     * output, providing flexibility to exclude unwanted or unnecessary processes during the
     * packaging phase.
     */
    @Parameter
    private List<String> exclude;

    /**
     * A prefix that is prepended to the names of the generated ZIP files during the packaging process.
     * This variable allows customization of the naming convention for the output ZIP files.
     * If no value is provided, the default value is an empty string, meaning no prefix is added.
     * <p>
     * The prefix is generally used to distinguish or organize ZIP files when multiple packages
     * are being created or when a specific naming convention is required for the generated artifacts.
     * <p>
     * This variable is configurable through the Maven `zipPrefix` property.
     */
    @Parameter(property = "zipPrefix", defaultValue = "")
    private String zipPrefix;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("=======================================");
        getLog().info("    Petriflow Packaging Maven Plugin   ");
        getLog().info("=======================================");

        List<File> dirsToProcess = (inputDirectories != null && !inputDirectories.isEmpty())
                ? inputDirectories
                : Collections.singletonList(inputDirectory);

        for (File inDir : dirsToProcess) {
            getLog().info("Input directory: " + inDir.getAbsolutePath());
            getLog().info("Output directory: " + outputDirectory.getAbsolutePath());
            getLog().info("multiApplication: " + multiApplication);
            getLog().info("Exclude regex patterns: " + (exclude != null ? exclude : "[]"));

            if (!inDir.exists() || !inDir.isDirectory()) {
                throw new MojoExecutionException("Input directory does not exist: " + inDir.getAbsolutePath());
            }
            if (!outputDirectory.exists()) {
                boolean created = outputDirectory.mkdirs();
                if (!created) {
                    throw new MojoExecutionException("Could not create output directory: " + outputDirectory.getAbsolutePath());
                }
            }

            try {
                if (multiApplication) {
                    getLog().info("Searching subdirectories for applications in: " + inDir.getAbsolutePath());
                    Set<String> subdirs = new HashSet<>();
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(inDir.toPath())) {
                        boolean foundAny = false;
                        for (Path appDir : stream) {
                            if (Files.isDirectory(appDir)) {
                                foundAny = true;
                                String baseName = appDir.getFileName().toString();
                                subdirs.add(baseName);
                                getLog().info("--------------------------------------------------------------------");
                                getLog().info("Processing app directory: " + baseName);
                                zipApp(
                                        appDir,
                                        new File(outputDirectory, baseName + ".zip"),
                                        getOrDefault(appId, baseName),
                                        getOrDefault(appName, baseName),
                                        getOrDefault(appDescription, "Petriflow application " + baseName),
                                        getOrDefault(appVersion, project.getVersion()),
                                        getOrDefault(appAuthor, getDefaultAuthor()),
                                        getExclusionPatterns()
                                );
                            }
                        }
                        try (Stream<Path> pathStream = Files.list(inDir.toPath())) {
                            List<Path> rootXmls = pathStream
                                    .filter(p -> !Files.isDirectory(p))
                                    .filter(p -> p.toString().endsWith(".xml"))
                                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("manifest.xml"))
                                    .toList();

                            if (!rootXmls.isEmpty()) {
                                getLog().info("--------------------------------------------------------------------");
                                getLog().info("Processing root directory XML files as extra application: root");
                                String baseName = "root";
                                zipAppCustomFiles(
                                        inDir.toPath(),
                                        rootXmls,
                                        new File(outputDirectory, baseName + ".zip"),
                                        getOrDefault(appId, baseName),
                                        getOrDefault(appName, baseName),
                                        getOrDefault(appDescription, "Petriflow application " + baseName),
                                        getOrDefault(appVersion, project.getVersion()),
                                        getOrDefault(appAuthor, getDefaultAuthor()),
                                        getExclusionPatterns()
                                );
                            }
                            if (!foundAny && rootXmls.isEmpty()) {
                                getLog().warn("No subdirectories or root XMLs found in " + inDir.getAbsolutePath());
                            }
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error while zipping petriflow apps", e);
                        }
                    }
                } else {
                    getLog().info("Processing single application (all XML files in input directory)...");
                    String baseName = inDir.getName();
                    String zipName = (zipPrefix != null && !zipPrefix.isEmpty())
                            ? zipPrefix + "_" + baseName + ".zip"
                            : baseName + ".zip";
                    zipApp(
                            inDir.toPath(),
                            new File(outputDirectory, zipName),
                            getOrDefault(appId, baseName),
                            getOrDefault(appName, baseName),
                            getOrDefault(appDescription, "Petriflow application " + baseName),
                            getOrDefault(appVersion, project.getVersion()),
                            getOrDefault(appAuthor, getDefaultAuthor()),
                            getExclusionPatterns()
                    );
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Error while zipping petriflow apps", e);
            }
        }

        getLog().info("=====================================");
        getLog().info("   Petriflow Packaging Maven Plugin  ");
        getLog().info("=====================================");
    }

    private String makeFlatName(Path appDir, Path file) {
        Path rel = appDir.relativize(file);
        if (rel.getNameCount() == 1) {
            return rel.getFileName().toString();
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rel.getNameCount() - 1; ++i) {
                sb.append(rel.getName(i).toString()).append("_");
            }
            sb.append(rel.getFileName().toString());
            return sb.toString();
        }
    }

    private String makeFlatNetName(Path appDir, Path file) {
        String name = makeFlatName(appDir, file);
        return name.replaceFirst("\\.xml$", "");
    }

    private void zipAppCustomFiles(Path appDir, List<Path> processFiles, File outputZip,
                                   String appId, String appName, String appDescription, String appVersion, String appAuthor, List<Pattern> excludePatterns) throws IOException {
        List<String> processNames = new ArrayList<>();
        List<Path> includedProcessFiles = new ArrayList<>();
        for (Path pf : processFiles) {
            String netName = pf.getFileName().toString().replaceFirst("\\.xml$", "");
            if (!isExcluded(netName, excludePatterns)) {
                includedProcessFiles.add(pf);
                processNames.add(makeFlatNetName(appDir, pf));
            } else {
                getLog().info("Excluding process by pattern: " + netName);
            }
        }
        getLog().info("Found " + processNames.size() + " INCLUDED process XML files (custom):");
        for (String process : processNames) {
            getLog().info("   - " + process);
        }

        getLog().info("Generating manifest.xml ...");
        String manifestXml = generateManifestXml(processNames, appId, appName, appDescription, appVersion, appAuthor);
        getLog().debug("Manifest XML:\n" + manifestXml);

        getLog().info("Creating ZIP...");
        try (FileOutputStream fos = new FileOutputStream(outputZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            zos.putNextEntry(new ZipEntry("manifest.xml"));
            zos.write(manifestXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            getLog().info("Added: manifest.xml");

            for (Path processFile : includedProcessFiles) {
                String entryName = "processes/" + makeFlatName(appDir, processFile);
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(processFile, zos);
                zos.closeEntry();
                getLog().info("Added: " + entryName);
            }
        }
        getLog().info("ZIP created: " + outputZip.getAbsolutePath());
    }

    private void zipApp(Path appDir, File outputZip, String appId, String appName, String appDescription, String appVersion, String appAuthor, List<Pattern> excludePatterns) throws MojoExecutionException {
        getLog().info("Zipping application:");
        getLog().info(" - Source directory: " + appDir.toAbsolutePath());
        getLog().info(" - Output zip: " + outputZip.getAbsolutePath());
        getLog().info(" - Manifest values:");
        getLog().info("      appId: " + appId);
        getLog().info("      appName: " + appName);
        getLog().info("      appDescription: " + appDescription);
        getLog().info("      appVersion: " + appVersion);
        getLog().info("      appAuthor: " + appAuthor);

        try(Stream<Path> processFilesPathsStream = Files.walk(appDir)) {
            List<Path> processFiles = processFilesPathsStream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.toString().endsWith(".xml"))
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("manifest.xml"))
                    .toList();

            List<String> processNames = new ArrayList<>();
            List<Path> includedProcessFiles = new ArrayList<>();
            for (Path pf : processFiles) {
                String netName = pf.getFileName().toString().replaceFirst("\\.xml$", "");
                if (!isExcluded(netName, excludePatterns)) {
                    includedProcessFiles.add(pf);
                    processNames.add(makeFlatNetName(appDir, pf));
                } else {
                    getLog().info("Excluding process by pattern: " + netName);
                }
            }

            getLog().info("Found " + processNames.size() + " INCLUDED process XML files:");
            for (String process : processNames) {
                getLog().info("   - " + process);
            }

            getLog().info("Generating manifest.xml ...");
            String manifestXml = generateManifestXml(processNames, appId, appName, appDescription, appVersion, appAuthor);
            getLog().debug("Manifest XML:\n" + manifestXml);

            getLog().info("Creating ZIP...");
            try (FileOutputStream fos = new FileOutputStream(outputZip);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                zos.putNextEntry(new ZipEntry("manifest.xml"));
                zos.write(manifestXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                getLog().info("Added: manifest.xml");

                for (Path processFile : includedProcessFiles) {
                    String entryName = "processes/" + makeFlatName(appDir, processFile);
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(processFile, zos);
                    zos.closeEntry();
                    getLog().info("Added: " + entryName);
                }
            }
            getLog().info("ZIP created: " + outputZip.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Error while zipping petriflow apps", e);
        }
    }

    private boolean isExcluded(String netName, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(netName).matches()) {
                return true;
            }
        }
        return false;
    }

    private List<Pattern> getExclusionPatterns() {
        if (exclude == null) {
            return Collections.emptyList();
        }
        return exclude.stream()
                .map(Pattern::compile)
                .collect(Collectors.toList());
    }

    private String generateManifestXml(List<String> processNames, String appId, String appName, String description, String version, String author) {
        String allowedNets = processNames.stream()
                .map(name -> "                <value>" + escapeXml(name) + "</value>")
                .collect(Collectors.joining("\n"));

        return """
                <cases xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:noNamespaceSchemaLocation="https://github.com/netgrif/petriflow/blob/PF-78/petriflow.schema.xsd">
                    <case>
                        <title>%s</title>
                        <dataField type="text">
                            <id>app_id</id>
                            <value>%s</value>
                            <version>1</version>
                        </dataField>
                        <dataField type="text">
                            <id>name</id>
                            <value>%s</value>
                            <version>1</version>
                        </dataField>
                        <dataField type="text">
                            <id>description</id>
                            <value>%s</value>
                            <version>1</version>
                        </dataField>
                        <dataField type="text">
                            <id>version</id>
                            <value>%s</value>
                            <version>1</version>
                        </dataField>
                        <dataField type="text">
                            <id>author</id>
                            <value>%s</value>
                            <version>1</version>
                        </dataField>
                        <dataField type="caseRef">
                            <id>processes</id>
                            <allowedNets>
                %s
                            </allowedNets>
                            <version>1</version>
                        </dataField>
                    </case>
                </cases>
                """.formatted(
                escapeXml(appName),
                escapeXml(appId),
                escapeXml(appName),
                escapeXml(description),
                escapeXml(version),
                escapeXml(author),
                allowedNets
        );
    }

    private String getOrDefault(String userInput, String defaultValue) {
        if (userInput != null && !userInput.isEmpty()) {
            return userInput;
        }
        return defaultValue != null ? defaultValue : "";
    }

    private String getDefaultAuthor() {
        List<?> devs = project.getDevelopers();
        if (devs != null && !devs.isEmpty()) {
            Object dev = devs.getFirst();
            try {
                Object name = dev.getClass().getMethod("getName").invoke(dev);
                if (name instanceof String authorName && !authorName.isBlank()) {
                    return authorName;
                }
            } catch (Exception ignored) {
            }
        }
        return "unknown";
    }

    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
