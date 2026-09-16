package com.marvel.module.infra.service;

import com.marvel.common.exception.BusinessException;
import com.marvel.module.infra.entity.SysFile;
import com.marvel.module.infra.mapper.SysFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private SysFileMapper fileMapper;
    private LocalStorageService service;

    @BeforeEach
    void setUp() {
        fileMapper = mock(SysFileMapper.class);
        service = new LocalStorageService(fileMapper);
        ReflectionTestUtils.setField(service, "basePath", tempDir.toString());
    }

    @Test
    void svgExtensionRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.svg", "image/png", "<svg/>".getBytes());
        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("svg");
    }

    @Test
    void svgContentTypeRejectedEvenWithImageExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "look.png", "image/svg+xml", "<svg/>".getBytes());
        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("svg");
    }

    @Test
    void emptyFileRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void missingExtensionRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "noext", "image/png", "x".getBytes());
        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("扩展名");
    }

    @Test
    void executableExtensionRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "shell.jsp", "text/plain", "x".getBytes());
        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持");
    }

    @Test
    void storedNameIsUuidAndFileMetadataPersisted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../evil name.png", "image/png", "PNGDATA".getBytes());

        String url = service.upload(file);

        assertThat(url).startsWith("/uploads/").endsWith(".png");
        assertThat(url).doesNotContain("..").doesNotContain("evil name");
        Path stored = tempDir.resolve(url.replace("/uploads/", ""));
        assertThat(stored).exists();
        assertThat(Files.readString(stored)).isEqualTo("PNGDATA");
        verify(fileMapper).insert(any(SysFile.class));
    }

    @Test
    void loadAndDeleteResolveStoredFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        String url = service.upload(file);

        assertThat(service.loadAsResource(url).exists()).isTrue();
        assertThat(service.loadAsResource(url).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello");

        service.delete(url);
        assertThatThrownBy(() -> service.loadAsResource(url)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsPathTraversalAndForeignPrefix() {
        assertThatThrownBy(() -> service.loadAsResource("/uploads/../../etc/passwd"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.loadAsResource("/etc/passwd"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.loadAsResource(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete("/uploads/../../../tmp/x"))
                .isInstanceOf(BusinessException.class);
    }
}
