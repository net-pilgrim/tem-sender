package com.scaleway.mailsender.repository;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;

@Singleton
public class DatabaseInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final ConnectionOperations<Connection> connectionOperations;

    public DatabaseInitializer(ConnectionOperations<Connection> connectionOperations) {
        this.connectionOperations = connectionOperations;
    }

    @EventListener
    public void onStartup(StartupEvent event) {
        LOG.info("Initializing database schema...");
        try (InputStream is = getClass().getResourceAsStream("/schema.sql")) {
            if (is == null) {
                LOG.warn("schema.sql not found in classpath");
                return;
            }

            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            connectionOperations.execute(ConnectionDefinition.DEFAULT, status -> {
                try {
                    Connection conn = status.getConnection();
                    try (Statement stmt = conn.createStatement()) {
                        for (String query : sql.split(";")) {
                            String trimmed = query.trim();
                            if (!trimmed.isEmpty()) {
                                stmt.execute(trimmed);
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("SQL execution error", e);
                }
                return null;
            });

            LOG.info("Database schema initialized successfully.");
        } catch (Exception e) {
            LOG.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
