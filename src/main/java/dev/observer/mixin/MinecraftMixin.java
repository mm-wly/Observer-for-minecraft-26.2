package dev.observer.mixin;

import dev.observer.client.ObserverClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method="shouldEntityAppearGlowing",at=@At("RETURN"),cancellable=true)
    private void observer$glow(Entity entity,CallbackInfoReturnable<Boolean> cir) {
        if(ObserverClient.INSTANCE!=null&&ObserverClient.INSTANCE.isGlowing(entity))cir.setReturnValue(true);
    }
}
