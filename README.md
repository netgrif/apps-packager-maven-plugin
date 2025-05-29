# Petriflow Packaging Maven Plugin

[![Java](https://img.shields.io/badge/Java-21-red)](https://openjdk.java.net/projects/jdk/21/)
[![Petriflow 1.0.1](https://img.shields.io/badge/Petriflow-1.0.1-0aa8ff)](https://petriflow.com)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/netgrif/apps-packager-maven-plugin?sort=semver&display_name=tag)](https://github.com/netgrif/apps-packager-maven-plugin/releases)
[![Publish package to Maven](https://github.com/netgrif/apps-packager-maven-plugin/actions/workflows/release-build.yaml/badge.svg)](https://github.com/netgrif/apps-packager-maven-plugin/actions/workflows/release-build.yaml)
---

A Maven plugin for packaging [Petriflow](https://github.com/netgrif/petriflow) application XML files into distributable ZIP archives, including automatic manifest generation and flexible configuration.

---

## What Does This Plugin Do?

* Packages all `.xml` Petri net files (except excluded) into a ZIP.
* Generates a `manifest.xml` with all configured metadata.
* Supports multi-app and multi-directory setups.
* Resulting ZIPs are in your configured output directory.

---

## Features

* Package one or more Petriflow applications into ZIP archives.
* Automatic `manifest.xml` generation with app metadata and process listing.
* Support for multi-app projects (each subdirectory packaged separately).
* Regex-based exclusion of processes.
* Flexible configuration via Maven properties.
* Verbose logging of included/excluded processes.

---

## Requirements

* Java 21 or newer
* Maven 3+

---

## Running the Plugin

The plugin runs automatically during the `package` phase:

```shell
mvn clean package
```

Or run manually:

```shell
mvn com.netgrif:apps-packager-maven-plugin:package-petriflow
```

---

## Plugin Configuration Options

| Parameter          | Default Value                                | Description                                                 |
| ------------------ | -------------------------------------------- | ----------------------------------------------------------- |
| `inputDirectory`   | `src/main/resources/petriNets`               | Path to the directory with Petri net XML files              |
| `inputDirectories` | *(none)*                                     | List of directories to process (overrides `inputDirectory`) |
| `outputDirectory`  | `${project.build.directory}/petriflow-zips`  | Output folder for ZIP files                                 |
| `multiApplication` | `false`                                      | If true, treats subfolders as separate applications         |
| `appId`            | `${project.artifactId}`                      | Application ID for manifest                                 |
| `appName`          | `${project.name}`                            | Application name                                            |
| `appDescription`   | `${project.description}`                     | Application description                                     |
| `appVersion`       | `${project.version}`                         | Application version                                         |
| `appAuthor`        | `${project.developers[0].name}` or `unknown` | Application author                                          |
| `exclude`          | *(none)*                                     | List of regex patterns to exclude processes (by file name)  |
| `zipPrefix`        | *(none)*                                     | Prefix for ZIP file names                                   |

Example only the `<configuration>` block:

```xml
<configuration>
    <multiApplication>true</multiApplication>
    <exclude>
        <pattern>.*Test.*</pattern>
        <pattern>SampleProcess</pattern>
    </exclude>
    <zipPrefix>release</zipPrefix>
</configuration>
```

---

## Example `pom.xml` Configuration

Below is a sample Maven module configuration using this plugin:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.example</groupId>
        <artifactId>ExampleModule</artifactId>
        <version>0.0.1</version>
    </parent>
    <artifactId>processes</artifactId>
    <name>Example Processes</name>
    <description>This module contains example Petriflow process definitions for demonstration, testing, and integration
        within the Netgrif Application Engine ecosystem.
    </description>
    <version>0.0.1</version>
    <url>https://example.com/processes</url>
    <developers>
        <developer>
            <name>Netgrif</name>
            <email>example@test.com</email>
        </developer>
    </developers>
    <build>
        <plugins>
            <plugin>
                <groupId>com.netgrif</groupId>
                <artifactId>apps-packager-maven-plugin</artifactId>
                <version>1.0.0</version>
                <configuration>
                    <outputDirectory>../Example-Apps</outputDirectory>
                    <multiApplication>true</multiApplication>
                    <exclude>
                        <pattern>ThisPetriNetExclude</pattern> <!-- REGEX pattern: exclude processes with this name -->
                    </exclude>
                    <zipPrefix>release</zipPrefix>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>package-petriflow</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Example Project Structure

```
project-root/
 └── src/
     └── main/
         └── resources/
             └── petriNets/
                 ├── app1/
                 │    ├── Invoice.xml
                 │    └── Payment.xml
                 └── app2/
                      └── Order.xml
```

With `multiApplication=true`, each subfolder (`app1`, `app2`) is packaged as a separate application ZIP.

---
