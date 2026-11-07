package com.manga.agent.generation;

import com.manga.entity.GenerationJob;
import com.manga.agent.changeset.ChangeSetCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/** 无费用、可重启恢复的异步供应商模拟器，图片明确标识 FAKE。 */
@Component
@RequiredArgsConstructor
public class FakeImageProvider implements GenerationProvider {
    /** 无字体依赖的 FAKE 点阵，保证精简 headless 服务器也能完成免费流程验证。 */
    private static final String[] FAKE_MARKER = {
            "11111 01110 10001 11111", "10000 10001 10010 10000", "10000 10001 10100 10000",
            "11110 11111 11000 11110", "10000 10001 10100 10000", "10000 10001 10010 10000", "10000 10001 10001 11111"
    };
    private final GenerationProperties properties;
    private final ChangeSetCodec codec;
    /** 返回模拟器路由。 */
    @Override
    public GenerationProviderKey key() { return GenerationProviderKey.FAKE; }
    /** 平台 jobId 本身就是外部标识，重复提交不创建第二个作业。 */
    @Override
    public boolean idempotentSubmission() { return true; }
    /** 立即返回受理，由持久化 submittedAt 推导后续进度。 */
    @Override
    public GenerationSubmission submit(GenerationJob job) {
        return new GenerationSubmission(job.getJobId(), new GenerationSnapshot(GenerationJobStatus.SUBMITTED, 0, null, null));
    }
    /** 无内存会话，重启后仍从持久化时间恢复。 */
    @Override
    public GenerationSnapshot query(GenerationJob job) {
        if (Boolean.TRUE.equals(job.getCancelRequested())) return new GenerationSnapshot(GenerationJobStatus.CANCELLED, null, null, null);
        long elapsed = Math.max(0, Duration.between(job.getSubmittedAt(), LocalDateTime.now()).toMillis());
        int progress = (int) Math.min(100, elapsed * 100 / properties.fakeDuration().toMillis());
        if (progress < 100) return new GenerationSnapshot(GenerationJobStatus.RUNNING, progress, null, null);
        return new GenerationSnapshot(GenerationJobStatus.MATERIALIZING, 100,
                placeholder(codec.read(job.getRequestJson(), ImageGenerationChange.class)), null);
    }
    /** 模拟器下一次查询会确认取消。 */
    @Override
    public void cancel(GenerationJob job) { }
    /** 生成确定性的占位图，用于验证平台转存而非模型画质。 */
    private String placeholder(ImageGenerationChange request) {
        BufferedImage image = new BufferedImage(request.width(), request.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.DARK_GRAY); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.WHITE);
            for (int row = 0; row < FAKE_MARKER.length; row++) {
                for (int column = 0; column < FAKE_MARKER[row].length(); column++) {
                    if (FAKE_MARKER[row].charAt(column) == '1') graphics.fillRect(100 + column * 20, image.getHeight() / 2 + row * 20, 18, 18);
                }
            }
        } finally { graphics.dispose(); }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, GenerationConstants.FORMAT, output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) { throw new GenerationException(GenerationError.PROTOCOL); }
    }
}
