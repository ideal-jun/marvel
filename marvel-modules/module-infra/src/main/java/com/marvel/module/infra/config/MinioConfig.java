package com.marvel.module.infra.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端装配（marvel.storage.type=minio 时生效）。
 *
 * <p>启动时确保 bucket 存在；public-read=true 时开放匿名只读，
 * 使上传后的图片可通过稳定直链直接被浏览器展示。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "marvel.storage.type", havingValue = "minio")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${marvel.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${marvel.storage.minio.access-key:minioadmin}") String accessKey,
            @Value("${marvel.storage.minio.secret-key:minioadmin}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public ApplicationRunner minioBucketInitializer(
            MinioClient minioClient,
            @Value("${marvel.storage.minio.bucket:marvel}") String bucket,
            @Value("${marvel.storage.minio.public-read:true}") boolean publicRead) {
        return args -> {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 已创建 bucket: {}", bucket);
            }
            if (publicRead) {
                String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                        + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
                        + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
                log.info("MinIO bucket {} 已开放匿名只读", bucket);
            }
        };
    }
}
