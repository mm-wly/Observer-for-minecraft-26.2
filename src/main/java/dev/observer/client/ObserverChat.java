package dev.observer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Main-thread local report scope. Visible chat history is unchanged; avoid synchronous console I/O. */
public final class ObserverChat {
    private ObserverChat() {}
    private static Component activeReport;
    public static long suppressedLogWrites;
    public static boolean suppressLog(Component text) {
        if(activeReport==null||text!=activeReport)return false;
        suppressedLogWrites++;return true;
    }
    public static void report(Component text) {
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        var previous=activeReport;activeReport=text;
        try{mc.gui.chatListener().handleSystemMessage(text,false);}
        finally{activeReport=previous;}
    }
}
