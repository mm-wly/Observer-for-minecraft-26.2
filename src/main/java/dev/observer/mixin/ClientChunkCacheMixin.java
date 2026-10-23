package dev.observer.mixin;

import dev.observer.client.ObserverClient;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Inject(method="replaceBiomes",at=@At("TAIL"))
    private void observer$biomes(int x,int z,FriendlyByteBuf buffer,CallbackInfo ci) {
        if(ObserverClient.INSTANCE!=null)ObserverClient.INSTANCE.index.biomesChanged(x,z);
    }
}
