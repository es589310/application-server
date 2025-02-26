package com.example.pdfprocessorservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pdf_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PdfEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String filePath;
    private String content;
    private LocalDateTime uploadDate;

}
