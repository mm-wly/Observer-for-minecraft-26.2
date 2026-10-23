package dev.observer.mixin;

import dev.observer.client.ObserverChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method="logChatMessage",at=@At("HEAD"),cancellable=true)
    private void observer$localReportLog(GuiMessage message,CallbackInfo ci) {
        // Exact component identity: never suppress server or other mods' chat messages.
        if(ObserverChat.suppressLog(message.content()))ci.cancel();
    }
}
