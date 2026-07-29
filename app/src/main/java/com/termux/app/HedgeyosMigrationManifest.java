package com.termux.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HedgeyosMigrationManifest {

    private static final String MARKER_DIRECTORY = "var/lib/hedgeyos/migrations";

    private final List<Entry> entries;
    private final Map<String, List<Entry>> byGeneration;

    private HedgeyosMigrationManifest(List<Entry> entries,
                                      Map<String, List<Entry>> byGeneration) {
        this.entries = Collections.unmodifiableList(entries);
        this.byGeneration = Collections.unmodifiableMap(byGeneration);
    }

    static HedgeyosMigrationManifest parse(InputStream input) throws IOException {
        List<Entry> entries = new ArrayList<>();
        Map<String, List<Entry>> byGeneration = new LinkedHashMap<>();
        Set<String> packages = new HashSet<>();
        Set<String> filenames = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", 7);
                if (fields.length != 7) {
                    throw new IOException("Migration manifest line " + lineNumber +
                        " must contain exactly 7 tab-separated fields.");
                }
                Entry entry = new Entry(
                    fields[0],
                    fields[1],
                    fields[2],
                    fields[3],
                    fields[4],
                    fields[5],
                    fields[6]);
                entry.validate(lineNumber);
                if (!packages.add(entry.packageName)) {
                    throw new IOException("Duplicate migration package: " + entry.packageName);
                }
                if (!filenames.add(entry.filename)) {
                    throw new IOException("Duplicate migration filename: " + entry.filename);
                }
                entries.add(entry);
                List<Entry> generationEntries = byGeneration.get(entry.generation);
                if (generationEntries == null) {
                    generationEntries = new ArrayList<>();
                    byGeneration.put(entry.generation, generationEntries);
                }
                generationEntries.add(entry);
            }
        }

        if (entries.isEmpty()) {
            throw new IOException("Migration manifest contains no package rows.");
        }
        for (Map.Entry<String, List<Entry>> generation : byGeneration.entrySet()) {
            generation.setValue(Collections.unmodifiableList(generation.getValue()));
        }
        return new HedgeyosMigrationManifest(entries, byGeneration);
    }

    List<Entry> entries() {
        return entries;
    }

    Set<String> generations() {
        return byGeneration.keySet();
    }

    List<Entry> entriesForGeneration(String generation) {
        List<Entry> result = byGeneration.get(generation);
        return result == null ? Collections.emptyList() : result;
    }

    static File marker(File rootfs, String generation) {
        return new File(new File(rootfs, MARKER_DIRECTORY), generation);
    }

    static boolean isGenerationComplete(File rootfs, String generation) {
        return marker(rootfs, generation).isFile();
    }

    static final class Entry {
        final String packageName;
        final String requiredVersion;
        final String architecture;
        final String generation;
        final String sha256;
        final String filename;
        final String reason;

        Entry(String packageName, String requiredVersion, String architecture,
              String generation, String sha256, String filename, String reason) {
            this.packageName = packageName;
            this.requiredVersion = requiredVersion;
            this.architecture = architecture;
            this.generation = generation;
            this.sha256 = sha256;
            this.filename = filename;
            this.reason = reason;
        }

        private void validate(int lineNumber) throws IOException {
            if (!packageName.matches("[a-z0-9][a-z0-9+.-]*")) {
                throw invalid(lineNumber, "package name");
            }
            if (requiredVersion.isEmpty()) {
                throw invalid(lineNumber, "required version");
            }
            if (!"arm64".equals(architecture) && !"all".equals(architecture)) {
                throw invalid(lineNumber, "architecture");
            }
            if (!generation.matches("[a-z0-9][a-z0-9.-]*")) {
                throw invalid(lineNumber, "migration generation");
            }
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw invalid(lineNumber, "SHA-256");
            }
            if (!filename.matches("[A-Za-z0-9][A-Za-z0-9+_.-]*\\.deb")) {
                throw invalid(lineNumber, "filename");
            }
            if (reason.trim().isEmpty()) {
                throw invalid(lineNumber, "reason");
            }
        }

        private IOException invalid(int lineNumber, String field) {
            return new IOException(
                "Invalid migration " + field + " on manifest line " + lineNumber + ".");
        }
    }
}
