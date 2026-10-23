package dev.observer.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Translation happens on the client; core detection carries only evidence keys. */
final class ObserverText {
    private ObserverText() {}
    static MutableComponent text(String key,Object... args) {
        return Component.translatable("observer."+key,args);
    }
}
