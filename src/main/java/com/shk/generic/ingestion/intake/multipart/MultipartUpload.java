package com.shk.generic.ingestion.intake.multipart;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public record MultipartUpload(MultipartFile file, Set<String> categories) {
}
