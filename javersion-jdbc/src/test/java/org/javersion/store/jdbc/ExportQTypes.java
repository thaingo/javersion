package org.javersion.store.jdbc;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.SQLException;

import javax.inject.Inject;
import javax.sql.DataSource;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.querydsl.sql.Configuration;
import com.querydsl.sql.codegen.DefaultNamingStrategy;
import com.querydsl.sql.codegen.MetaDataExporter;

@org.springframework.context.annotation.Configuration
@EnableAutoConfiguration
@Import(PersistenceTestConfiguration.class)
public class ExportQTypes {

    public static final String NAME_PREFIX = "Q";
    public static final String TARGET_FOLDER = "src/generated/java";
    public static final String PACKAGE_NAME = "org.javersion.store.sql";

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ExportQTypes.class);
        application.setWebEnvironment(false);
        application.run(args);
    }

    @Inject
    DataSource dataSource;

    @Inject
    Configuration configuration;

    @Bean
    public CommandLineRunner runner() {
        return (args) -> {
            MetaDataExporter exporter = new MetaDataExporter();
            exporter.setPackageName(PACKAGE_NAME);
            exporter.setInnerClassesForKeys(false);
            exporter.setSpatial(false);
            exporter.setNamePrefix(NAME_PREFIX);
            exporter.setNamingStrategy(new DefaultNamingStrategy());
            exporter.setTargetFolder(new File(TARGET_FOLDER));
            exporter.setConfiguration(configuration);
            Connection conn = null;
            try {
                conn = dataSource.getConnection();
                deleteOldQTypes(TARGET_FOLDER, PACKAGE_NAME);
                exporter.export(conn.getMetaData());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {}
                }
            }
        };
    }
    private static void deleteOldQTypes(final String target, final String pack)
            throws IOException {
        Path targetDir = FileSystems.getDefault().getPath(target, pack.replace(".", "/"));
        Files.walkFileTree(targetDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (path.getFileName().toString().startsWith(NAME_PREFIX)) {
                    System.out.println("Delete " + path + ": " + path.toFile().delete());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
