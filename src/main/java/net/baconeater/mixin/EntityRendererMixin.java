package net.baconeater.mixin;

import net.baconeater.features.commands.visibility.client.ClientVisibilityManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    private static final Field server$entityIdField = server$findEntityIdField();

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void server$hideHiddenEntities(
            EntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            CameraRenderState cameraState,
            CallbackInfo ci) {
        int entityId = server$getEntityId(state);
        if (entityId != Integer.MIN_VALUE && ClientVisibilityManager.isHidden(entityId)) {
            ci.cancel();
        }
    }

    private static int server$getEntityId(EntityRenderState state) {
        if (server$entityIdField == null) {
            return Integer.MIN_VALUE;
        }

        try {
            return server$entityIdField.getInt(state);
        } catch (IllegalAccessException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static Field server$findEntityIdField() {
        for (String fieldName : new String[]{"entityId", "id"}) {
            try {
                Field field = EntityRenderState.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }

        return null;
    }
}