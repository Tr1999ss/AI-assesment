package com.shk.generic.ingestion.intake.multipart;

import com.shk.generic.ingestion.intake.FormatDetector;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeAdapter;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.IntakeRejectedException;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** File uploads: PDF, Markdown, or any other format the detector recognizes. */
@Component
public class MultipartIntakeAdapter implements IntakeAdapter<MultipartUpload> {

    private static final String DEFAULT_FILENAME = "upload";

    private final FormatDetector formatDetector;

    public MultipartIntakeAdapter(FormatDetector formatDetector) {
        this.formatDetector = formatDetector;
    }

    @Override
    public IngestionRequest adapt(MultipartUpload source) {
        MultipartFile file = source.file();
        String filename = StringUtils.hasText(file.getOriginalFilename())
                ? StringUtils.getFilename(file.getOriginalFilename())
                : DEFAULT_FILENAME;
        NamedByteArrayResource content = new NamedByteArrayResource(bytes(file), filename);
        SourceFormat format = formatDetector.detect(null, filename, file.getContentType(), content);

        return IngestionRequest.of(IntakeChannel.MULTIPART, filename, format, content,
                source.categories(), Map.of());
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IntakeRejectedException("Could not read uploaded file", e);
        }
    }
}
