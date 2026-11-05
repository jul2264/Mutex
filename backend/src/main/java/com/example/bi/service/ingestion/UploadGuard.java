package com.example.bi.service.ingestion;

import com.example.bi.config.ImportProperties;
import com.example.bi.domain.FileFormat;
import com.example.bi.exception.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class UploadGuard {

    private final ImportProperties importProperties;

    public UploadGuard(ImportProperties importProperties) {
        this.importProperties = importProperties;
    }

    public record GuardResult(
            File tempFile,
            String sanitizedFilename,
            String sha256Hex,
            long sizeBytes,
            FileFormat format
    ) {}

    public GuardResult inspectAndSpool(MultipartFile file, String datasetName) throws IOException {
        // 1. Dataset name validation
        if (datasetName == null || datasetName.strip().isEmpty()) {
            throw new IllegalArgumentException("Dataset name is required");
        }
        String trimmedName = datasetName.strip();
        if (trimmedName.length() > 100) {
            throw new IllegalArgumentException("Dataset name must not exceed 100 characters");
        }

        // 2. File presence
        if (file == null || file.isEmpty()) {
            throw new FileEmptyException("Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.strip().isEmpty()) {
            throw new UnsupportedFileTypeException("Filename is missing");
        }

        String lowerFilename = originalFilename.toLowerCase(Locale.ROOT);
        FileFormat format;
        if (lowerFilename.endsWith(".csv")) {
            format = FileFormat.CSV;
        } else if (lowerFilename.endsWith(".xlsx")) {
            format = FileFormat.XLSX;
        } else {
            throw new UnsupportedFileTypeException("Unsupported file type. Only CSV and XLSX are permitted.");
        }

        // 3. Prepare private temp directory
        File tempDir = new File(importProperties.getTempDir());
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            // Fallback to java.io.tmpdir directly if mkdirs fails
            tempDir = new File(System.getProperty("java.io.tmpdir"));
        }

        File tempFile = new File(tempDir, "import-spool-" + UUID.randomUUID() + ".tmp");

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }

        long totalBytes = 0;
        byte[] firstChunk = null;

        try (InputStream in = file.getInputStream();
             OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > importProperties.getMaxFileSizeBytes()) {
                    throw new FileTooLargeException(
                            String.format("File size exceeds limit of %d MB", importProperties.getMaxFileSizeBytes() / (1024 * 1024))
                    );
                }

                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);

                if (firstChunk == null) {
                    firstChunk = new byte[read];
                    System.arraycopy(buffer, 0, firstChunk, 0, read);
                }
            }
        } catch (Exception ex) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw ex;
        }

        if (totalBytes == 0 || firstChunk == null || firstChunk.length == 0) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw new FileEmptyException("Uploaded file is empty");
        }

        // 4. Content sniffing
        if (format == FileFormat.CSV) {
            // Check for NUL bytes in the first 8 KB
            for (byte b : firstChunk) {
                if (b == 0) {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                    throw new FileContentMismatchException("File content does not match CSV format (binary data detected)");
                }
            }

            // Verify UTF-8 encoding
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                decoder.decode(ByteBuffer.wrap(firstChunk));
            } catch (CharacterCodingException e) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                throw new EncodingUnsupportedException("File encoding is not valid UTF-8");
            }
        } else if (format == FileFormat.XLSX) {
            // Check ZIP magic: 0x50, 0x4B, 0x03, 0x04
            if (firstChunk.length < 4 || firstChunk[0] != 0x50 || firstChunk[1] != 0x4B || firstChunk[2] != 0x03 || firstChunk[3] != 0x04) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                throw new FileContentMismatchException("File content does not match XLSX format (ZIP signature missing)");
            }

            boolean hasContentTypes = false;
            boolean hasWorkbook = false;

            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(tempFile))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.equalsIgnoreCase("[Content_Types].xml")) {
                        hasContentTypes = true;
                    } else if (name.equalsIgnoreCase("xl/workbook.xml")) {
                        hasWorkbook = true;
                    } else if (name.toLowerCase(Locale.ROOT).contains("vbaproject.bin")) {
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        throw new FileContentMismatchException("Macro-enabled workbooks (VBA) are not permitted");
                    }
                }
            } catch (FileContentMismatchException e) {
                throw e;
            } catch (Exception e) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                throw new FileContentMismatchException("Corrupted or invalid XLSX ZIP archive");
            }

            if (!hasContentTypes || !hasWorkbook) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                throw new FileContentMismatchException("File content is missing required Excel workbook structures");
            }
        }

        String sha256Hex = HexFormat.of().formatHex(digest.digest());
        String sanitizedFilename = sanitizeFilename(originalFilename);

        return new GuardResult(tempFile, sanitizedFilename, sha256Hex, totalBytes, format);
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "upload.csv";
        }
        // Strip directory path
        String clean = new File(filename).getName();
        // Replace non-printable or control characters
        clean = clean.replaceAll("[\\p{Cntrl}]", "_").strip();
        if (clean.length() > 255) {
            clean = clean.substring(0, 255);
        }
        return clean.isEmpty() ? "upload.csv" : clean;
    }
}
