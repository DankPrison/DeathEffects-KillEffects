package com.dank420girl.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds a plugin jar from compiled class directories and jars, moving the shared code into the
 * plugin's own package so that DeathEffects and KillEffects never share classes at runtime.
 */
public abstract class RelocatedJar extends DefaultTask {
    /** Fixed timestamp so that two builds of the same sources produce identical jars. */
    private static final long ENTRY_TIME = new GregorianCalendar(1980, 1, 1, 0, 0, 0).getTimeInMillis();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @Input
    public abstract Property<String> getFromPackage();

    @Input
    public abstract Property<String> getToPackage();

    @Input
    public abstract MapProperty<String, String> getManifestAttributes();

    @OutputFile
    public abstract RegularFileProperty getArchiveFile();

    @TaskAction
    public void build() throws IOException {
        ClassRelocator relocator = new ClassRelocator(getFromPackage().get(), getToPackage().get());
        Map<String, byte[]> entries = new LinkedHashMap<>();

        for (File source : getSources().getFiles()) {
            if (!source.exists()) {
                continue;
            }
            if (source.isDirectory()) {
                collectDirectory(source, source, entries);
            } else if (source.getName().endsWith(".jar")) {
                collectJar(source, entries);
            } else {
                throw new GradleException("Unsupported input " + source);
            }
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        for (Map.Entry<String, String> attribute : getManifestAttributes().get().entrySet()) {
            manifest.getMainAttributes().putValue(attribute.getKey(), attribute.getValue());
        }

        File output = getArchiveFile().get().getAsFile();
        Files.createDirectories(output.getParentFile().toPath());
        List<String> names = new ArrayList<>(entries.keySet());
        Collections.sort(names);

        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(output))) {
            ZipEntry manifestEntry = new ZipEntry(JarFile.MANIFEST_NAME);
            manifestEntry.setTime(ENTRY_TIME);
            jar.putNextEntry(manifestEntry);
            manifest.write(jar);
            jar.closeEntry();

            List<String> directories = new ArrayList<>();
            for (String name : names) {
                String path = relocator.relocatePath(name);
                addParentDirectories(jar, path, directories);
                byte[] data = entries.get(name);
                if (name.endsWith(".class")) {
                    try {
                        data = relocator.relocateClass(data);
                    } catch (IOException e) {
                        throw new GradleException("Could not relocate " + name, e);
                    }
                }
                ZipEntry entry = new ZipEntry(path);
                entry.setTime(ENTRY_TIME);
                jar.putNextEntry(entry);
                jar.write(data);
                jar.closeEntry();
            }
        }
    }

    private static void addParentDirectories(JarOutputStream jar, String path, List<String> written) throws IOException {
        int slash = path.indexOf('/');
        while (slash > 0) {
            String dir = path.substring(0, slash + 1);
            if (!written.contains(dir)) {
                written.add(dir);
                ZipEntry entry = new ZipEntry(dir);
                entry.setTime(ENTRY_TIME);
                jar.putNextEntry(entry);
                jar.closeEntry();
            }
            slash = path.indexOf('/', slash + 1);
        }
    }

    private void collectDirectory(File root, File dir, Map<String, byte[]> entries) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectDirectory(root, child, entries);
            } else {
                String name = root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar, '/');
                try (InputStream in = new FileInputStream(child)) {
                    put(entries, name, readFully(in));
                }
            }
        }
    }

    private void collectJar(File file, Map<String, byte[]> entries) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            java.util.Enumeration<? extends ZipEntry> it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry entry = it.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    put(entries, entry.getName(), readFully(in));
                }
            }
        }
    }

    private static void put(Map<String, byte[]> entries, String name, byte[] data) {
        if (name.equals(JarFile.MANIFEST_NAME)) {
            return;
        }
        if (entries.containsKey(name)) {
            throw new GradleException("Duplicate jar entry " + name);
        }
        entries.put(name, data);
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
