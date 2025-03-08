package com.example.pdfprocessorservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseMigrationService {

    private final JdbcTemplate jdbcTemplate;

    public void updateContentColumnType() {
        String sql = "ALTER TABLE pdf_files ALTER COLUMN content TYPE TEXT ";
        jdbcTemplate.execute(sql);
    }

    public void addKeywordsColumn() {
        String sql = "ALTER TABLE pdf_files ADD COLUMN keywords TEXT";
        jdbcTemplate.execute(sql);
    }

}
