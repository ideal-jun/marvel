package com.marvel.module.infra.service;

import com.marvel.common.exception.BusinessException;
import com.marvel.module.infra.entity.SysFile;
import com.marvel.module.infra.mapper.SysFileMapper;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储实现（marvel.storage.type=minio 时生效）。
 *
 * <p>存储定位为对象 key（{@code yyyy/MM/dd/uuid.ext}），不落临时预签名地址；
 * 桶开启匿名只读时 {@link #toUrl(String)} 返回稳定直链，否则退化为预签名 URL。
 * 上传校验与本地实现共用 {@link StorageSupport}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marvel.storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final SysFileMapper fileMapper;

    @Value("${marvel.storage.minio.bucket:marvel}")
    private String bucket;

    /** 对象存储服务地址，用于拼装稳定直链 */
    @Value("${marvel.storage.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    /** 对外访问基址（CDN/反向代理）；留空则回退到 endpoint */
    @Value("${marvel.storage.minio.public-url:}")
    private String publicUrl;

    /** 桶是否匿名只读：true 时用稳定直链（可长期存库），false 时退化为预签名 URL */
    @Value("${marvel.storage.minio.public-read:true}")
    private boolean publicRead;

    @Value("${marvel.storage.minio.presign-expiry-seconds:604800}")
    private int presignExpirySeconds;

    @Override
    public String upload(MultipartFile file) {
        String ext = StorageSupport.validate(file, StorageSupport.ALLOWED_EXTENSIONS);
        String objectKey = StorageSupport.datePath() + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败", e);
            throw new BusinessException("文件上传失败，请稍后重试");
        }

        SysFile record = new SysFile();
        record.setFileName(StorageSupport.displayName(file, objectKey));
        record.setFilePath(objectKey);
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        fileMapper.insert(record);
        return objectKey;
    }

    @Override
    public Resource loadAsResource(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new BusinessException("非法的文件路径");
        }
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(filePath).build());
            return new MinioObjectResource(filePath, stat.size());
        } catch (Exception e) {
            throw new BusinessException("文件不存在");
        }
    }

    @Override
    public void delete(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(filePath).build());
        } catch (Exception e) {
            log.error("MinIO 删除失败: {}", filePath, e);
            throw new BusinessException("文件删除失败");
        }
    }

    @Override
    public String toUrl(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        // 历史数据/外部地址已是完整 URL，直接返回
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return filePath;
        }
        try {
            // 桶匿名只读时返回稳定直链（不随时间失效，可安全落库）；否则退化为预签名 URL
            if (publicRead) {
                String base = StringUtils.hasText(publicUrl) ? publicUrl : endpoint;
                return trimTrailingSlash(base) + "/" + bucket + "/" + filePath;
            }
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(filePath)
                    .expiry(presignExpirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.warn("生成 MinIO 访问地址失败 object={}: {}", filePath, e.getMessage());
            return null;
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** MinIO 对象只读资源：contentLength 取自 stat，输入流按需建立 */
    private final class MinioObjectResource extends AbstractResource {

        private final String object;
        private final long size;

        private MinioObjectResource(String object, long size) {
            this.object = object;
            this.size = size;
        }

        @Override
        public String getDescription() {
            return "MinIO object [" + bucket + "/" + object + "]";
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(object).build());
            } catch (Exception e) {
                throw new IOException("读取 MinIO 对象失败: " + object, e);
            }
        }

        @Override
        public long contentLength() {
            return size;
        }

        @Override
        public String getFilename() {
            int slash = object.lastIndexOf('/');
            return slash < 0 ? object : object.substring(slash + 1);
        }
    }
}
