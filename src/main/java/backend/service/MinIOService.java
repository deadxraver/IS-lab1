package backend.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class MinIOService {

    private static final Logger logger = Logger.getLogger(MinIOService.class.getName());
    
    private MinioClient minioClient;
    private String bucketName;
    private volatile boolean initialized = false;

    private void initialize() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            try {
                String endpoint = System.getenv("MINIO_ENDPOINT");
                String accessKey = System.getenv("MINIO_ACCESS_KEY");
                String secretKey = System.getenv("MINIO_SECRET_KEY");
                bucketName = System.getenv("MINIO_BUCKET");
                
                if (endpoint == null || endpoint.trim().isEmpty()) {
                    endpoint = "http://localhost:9000";
                }
                if (accessKey == null || accessKey.trim().isEmpty()) {
                    accessKey = "minioadmin";
                }
                if (secretKey == null || secretKey.trim().isEmpty()) {
                    secretKey = "minioadmin";
                }
                if (bucketName == null || bucketName.trim().isEmpty()) {
                    bucketName = "import-files";
                }

                minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

                // Проверяем существование bucket, создаем если не существует
                try {
                    if (!minioClient.bucketExists(io.minio.BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build())) {
                        minioClient.makeBucket(io.minio.MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build());
                        logger.info("Created MinIO bucket: " + bucketName);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to check/create bucket: " + e.getMessage());
                }

                initialized = true;
                logger.info("MinIO client initialized: endpoint=" + endpoint + ", bucket=" + bucketName);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize MinIO client: " + e.getMessage(), e);
            }
        }
    }

    public String uploadFile(InputStream inputStream, String contentType, long size) throws Exception {
        initialize();
        String objectName = UUID.randomUUID().toString() + ".xml";
        
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build()
            );
            logger.info("File uploaded to MinIO: " + objectName);
            return objectName;
        } catch (MinioException e) {
            logger.severe("MinIO error: " + e.getMessage());
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }
    }

    public InputStream downloadFile(String objectName) throws Exception {
        initialize();
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );
        } catch (MinioException e) {
            logger.severe("MinIO error: " + e.getMessage());
            throw new RuntimeException("Failed to download file from MinIO: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String objectName) throws Exception {
        initialize();
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build()
            );
            logger.info("File deleted from MinIO: " + objectName);
        } catch (MinioException e) {
            logger.severe("MinIO error: " + e.getMessage());
            throw new RuntimeException("Failed to delete file from MinIO: " + e.getMessage(), e);
        }
    }

    public boolean isAvailable() {
        try {
            initialize();
            return minioClient != null;
        } catch (Exception e) {
            return false;
        }
    }
}

