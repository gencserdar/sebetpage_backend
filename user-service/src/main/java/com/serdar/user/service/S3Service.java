package com.serdar.user.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * Thin S3 uploader for profile photos. If AWS credentials aren't provided we
 * no-op and return a local placeholder URL so the service still boots in dev.
 */
@Service
@RequiredArgsConstructor
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    @Value("${aws.accessKey:}") private String accessKey;
    @Value("${aws.secretKey:}") private String secretKey;
    @Value("${aws.region:us-east-1}") private String region;
    @Value("${aws.s3.bucketName:}") private String bucket;
    @Value("${aws.s3.publicBaseUrl:}") private String publicBaseUrl;

    public String upload(byte[] content, String contentType, String originalName) {
        if (accessKey.isBlank() || bucket.isBlank()) {
            log.warn("S3 not configured — returning placeholder URL instead");
            return "https://example.invalid/profile/" + UUID.randomUUID() + ".png";
        }
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String key = "profiles/" + UUID.randomUUID() + ext;
        try (S3Client s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build()) {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType == null ? "application/octet-stream" : contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        }
        String base = publicBaseUrl.isBlank()
                ? "https://" + bucket + ".s3." + region + ".amazonaws.com"
                : publicBaseUrl;
        return base + "/" + key;
    }
}
