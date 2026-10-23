package dev.observer.mixin;

import dev.observer.client.ObserverClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method="sendBlockUpdated",at=@At("TAIL"))
    private void observer$changed(BlockPos p,BlockState oldState,BlockState newState,int flags,CallbackInfo ci) {
        if(ObserverClient.INSTANCE!=null)ObserverClient.INSTANCE.changed(p,oldState,newState);
    }
}
