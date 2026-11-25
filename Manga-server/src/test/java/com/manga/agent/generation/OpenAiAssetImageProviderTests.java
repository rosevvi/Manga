package com.manga.agent.generation;

import com.manga.common.enums.AiProviderType;
import com.manga.entity.AssetVisual;
import com.manga.entity.AssetVisualReference;
import com.manga.entity.CreativeArtifact;
import com.manga.entity.GenerationJob;
import com.manga.entity.GenerationMediaObject;
import com.manga.entity.ProjectAsset;
import com.manga.repository.GenerationMediaObjectRepository;
import com.manga.repository.ProjectAssetRepository;
import com.manga.service.MediaStorageService;
import com.manga.service.ProjectAssetService;
import com.manga.service.storage.GeneratedMediaFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 参考关联指向图片项，替换后 OpenAI 收到当前登记对象的真实文件。 */
class OpenAiAssetImageProviderTests {
    @Test
    void submitsLatestReferenceBytesAsMultipart() {
        long projectId = 7;
        long ownerId = 14;
        long targetId = 22;
        long referenceId = 31;
        long previousArtifact = 40;
        long currentArtifact = 41;
        long currentObjectId = 51;
        int byteLimit = 4096;
        byte[] currentBytes = {1, 5, 9};
        var repository = mock(ProjectAssetRepository.class);
        var storage = mock(MediaStorageService.class);
        var objects = mock(GenerationMediaObjectRepository.class);
        var properties = mock(GenerationProperties.class);
        var configs = new ImageGenerationConfigService(null, null);
        var assets = new ProjectAssetService(repository, null, null, configs, storage, objects, null, null);
        var provider = new OpenAiImageProvider(configs, null, properties, null, null, assets, storage);

        var target = new AssetVisual();
        target.setId(targetId);
        target.setProjectAssetId(ownerId);
        var reference = new AssetVisual();
        reference.setId(referenceId);
        reference.setProjectAssetId(ownerId);
        reference.setCurrentArtifactId(previousArtifact);
        var edge = new AssetVisualReference();
        edge.setVisualId(targetId);
        edge.setReferenceVisualId(referenceId);
        var owner = new ProjectAsset();
        owner.setId(ownerId);
        when(repository.findVisual(targetId)).thenReturn(Optional.of(target));
        when(repository.findAsset(projectId, ownerId)).thenReturn(Optional.of(owner));
        when(repository.findReferences(List.of(targetId))).thenReturn(List.of(edge));
        when(repository.findVisualIds(List.of(referenceId))).thenReturn(List.of(reference));

        // 参考关系保持不变，只替换图片项的当前产物。
        reference.setCurrentArtifactId(currentArtifact);
        var artifact = CreativeArtifact.builder().id(currentArtifact).projectId(projectId)
                .mediaObjectId(currentObjectId).mediaType(GeneratedMediaFormat.PNG.mimeType()).build();
        var media = GenerationMediaObject.builder().id(currentObjectId).objectName("current")
                .mediaFormat(GeneratedMediaFormat.PNG).build();
        when(repository.findArtifacts(List.of(currentArtifact))).thenReturn(List.of(artifact));
        when(objects.findAll(List.of(currentObjectId))).thenReturn(List.of(media));
        when(properties.maxMediaBytes()).thenReturn(byteLimit);
        when(storage.readGeneratedBytes(media.reference(), byteLimit)).thenReturn(currentBytes);
        var job = GenerationJob.builder().projectId(projectId).targetVisualId(targetId).build();
        var endpoint = new GenerationConfigSnapshot(2L, AiProviderType.OPENAI, "https://api.openai.com/v1", null, null, null);
        var input = new ImageGenerationChange(null, GenerationProviderKey.OPENAI, endpoint.configId(),
                GenerationConstants.OPENAI_MODEL, "绘制三视图", 1024, 1024, endpoint);

        var request = provider.prepareRequest(job, input);

        assertThat(request.path()).isEqualTo(GenerationConstants.IMAGE_EDIT_PATH);
        assertThat(request.contentType()).isEqualTo(MediaType.MULTIPART_FORM_DATA);
        var parts = (MultiValueMap<?, ?>) request.body();
        var images = parts.get("image[]");
        assertThat(images).hasSize(1);
        var file = (ByteArrayResource) ((HttpEntity<?>) images.getFirst()).getBody();
        assertThat(file.getByteArray()).containsExactly(currentBytes);
    }
}
