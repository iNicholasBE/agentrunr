package io.agentrunr.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

/**
 * JobRunr configuration. Provides a SQLite DataSource for persistent job storage.
 * The jobrunr-spring-boot-3-starter auto-configures the StorageProvider from this DataSource.
 */
@Configuration
public class JobRunrConfig {

    private static final Logger log = LoggerFactory.getLogger(JobRunrConfig.class);

    @Bean
    public DataSource dataSource(
            @Value("${jobrunr.database.url:jdbc:sqlite:./data/jobrunr.db}") String url
    ) {
        var ds = new SQLiteDataSource();
        ds.setUrl(url);
        log.info("JobRunr SQLite DataSource configured: {}", url);
        return ds;
    }
}
