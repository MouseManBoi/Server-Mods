package net.baconeater.features.commands.shader.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryStack;

import java.util.Map;

public final class ShaderTimeUniforms {
    private static final String[] TIME_UNIFORM_NAMES = {"time", "Time"};
    private static volatile long shaderStartNanos = System.nanoTime();

    private ShaderTimeUniforms() {
    }

    public static void onShaderEnabled() {
        shaderStartNanos = System.nanoTime();
    }

    public static void onShaderDisabled() {
        shaderStartNanos = System.nanoTime();
    }

    public static void updateTimeUniforms(Map<String, GpuBuffer> uniformBuffers, String passId) {
        if (!shouldInjectTime(passId) && uniformBuffers.isEmpty()) {
            return;
        }

        float elapsedSeconds = (System.nanoTime() - shaderStartNanos) / 1_000_000_000.0F;
        elapsedSeconds = ShaderContextManager.getDisplayElapsedSeconds(passId, elapsedSeconds);
        for (String uniformName : TIME_UNIFORM_NAMES) {
            if (uniformBuffers.containsKey(uniformName) || shouldInjectTime(passId)) {
                replaceFloatUniform(uniformBuffers, passId, uniformName, elapsedSeconds);
            }
        }
    }

    private static boolean shouldInjectTime(String passId) {
        return passId.contains("cinematic_bars") || passId.contains("flashbang");
    }

    private static void replaceFloatUniform(Map<String, GpuBuffer> uniformBuffers, String passId, String uniformName, float value) {
        GpuBuffer oldBuffer = uniformBuffers.remove(uniformName);
        if (oldBuffer != null) {
            oldBuffer.close();
        }

        Std140SizeCalculator calculator = new Std140SizeCalculator();
        calculator.putFloat();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, calculator.get());
            builder.putFloat(value);
            uniformBuffers.put(
                    uniformName,
                    RenderSystem.getDevice().createBuffer(
                            () -> passId + " / " + uniformName,
                            GpuBuffer.USAGE_UNIFORM,
                            builder.get()
                    )
            );
        }
    }
}
