package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Singleton camera instance that tracks the current camera position and orientation.
 */
@Getter
public class Camera {
    public static final Camera INSTANCE = new Camera();

    private final CameraCoordinates coordinates = new CameraCoordinates();
    private final Vector3d pos = coordinates.getPosition();
    private final Vector3d entityPos = coordinates.getEyePosition();
    private BlockPos blockPos = BlockPos.ORIGIN;
    private final Vector3f offset = coordinates.getOffset();

    private float pitch;
    private float yaw;
    private boolean isSleeping;
    private boolean thirdPerson;
    private float partialTicks;

    private Camera() {}

    /** Returns the captured main-view matrix, independent of temporary shadow render state. */
    public Matrix4fc getModelViewMatrix() {
        return coordinates.getModelView();
    }

    public void update(EntityLivingBase entity, float partialTicks) {
        this.partialTicks = partialTicks;
        this.isSleeping = entity.isPlayerSleeping();

        final double entityX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        final double entityY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        final double entityZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        coordinates.update(RenderingState.INSTANCE.getModelViewMatrix(), entityX, entityY, entityZ, entity.getEyeHeight());
        thirdPerson = Minecraft.getMinecraft().gameSettings.thirdPersonView > 0;
        blockPos = new BlockPos(MathHelper.floor(pos.x), MathHelper.floor(pos.y), MathHelper.floor(pos.z));
        pitch = entity.cameraPitch;
        yaw = entity.rotationYaw;
    }
}
