package dev.observer.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;
import static dev.observer.client.ObserverText.text;

final class ObserverCommands {
    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,ObserverClient app) {
        var root=literal("obs").executes(c->{ObserverClient.message(text("help"));return 1;});
        var clear=literal("clear").executes(c->{app.clear();ObserverClient.message(text("cleared_all"));return 1;});
        for(String kind:new String[]{"structure","feature","biome","block","entity"}) {
            root.then(literal(kind).then(nameArgument(app,kind).executes(c->app.start(kind,StringArgumentType.getString(c,"name")))));
            clear.then(literal(kind).then(nameArgument(app,kind).executes(c->app.clear(kind,StringArgumentType.getString(c,"name")))));
        }
        root.then(clear);
        root.then(literal("list").executes(c->{app.list();return 1;}));
        root.then(hidden(literal("page").then(argument("query_or_page",IntegerArgumentType.integer(1)).suggests((c,b)->{
            var options=new java.util.TreeSet<Integer>(app.sessions.keySet());
            var latest=app.sessions.values().stream().reduce((a,z)->z);
            if(latest.isPresent())for(int i=1;i<=dev.observer.core.Results.pages(latest.get().targets.size());i++)options.add(i);
            for(int value:options)if(Integer.toString(value).startsWith(b.getRemaining()))b.suggest(value);
            return b.buildFuture();
        })
            .executes(c->app.latestPage(IntegerArgumentType.getInteger(c,"query_or_page")))
            .then(argument("page",IntegerArgumentType.integer(1)).suggests((c,b)->{var s=app.sessions.get(IntegerArgumentType.getInteger(c,"query_or_page"));if(s!=null)for(int i=1;i<=dev.observer.core.Results.pages(s.targets.size());i++)if(Integer.toString(i).startsWith(b.getRemaining()))b.suggest(i);return b.buildFuture();})
                .executes(c->app.page(IntegerArgumentType.getInteger(c,"query_or_page"),IntegerArgumentType.getInteger(c,"page")))))));
        // Opaque per-session tokens permit our own clicks, never arbitrary server-provided coordinates.
        root.then(hidden(literal("look").then(argument("token",StringArgumentType.word())
            .executes(c->app.look(StringArgumentType.getString(c,"token"))))));
        dispatcher.register(root);
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource,String> nameArgument(ObserverClient app,String kind) {
        return argument("name",new NameArgument()).suggests((ctx,builder)->{
            String remaining=builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            for(String n:app.names(kind)){String shortName=n.startsWith("minecraft:")?n.substring(10):n;if(matchesName(n,remaining))builder.suggest(remaining.contains(":")?n:shortName);}
            return builder.buildFuture();
        });
    }
    static boolean matchesName(String name,String input) {
        int colon=name.indexOf(':');
        String path=name.substring(colon+1);
        int inputColon=input.indexOf(':');
        if(inputColon>=0) {
            if(!name.substring(0,colon).equals(input.substring(0,inputColon)))return false;
            input=input.substring(inputColon+1);
        } else if(name.startsWith(input))return true;
        if(path.startsWith(input))return true;
        for(int i=1;i<path.length();i++)if("_/.-".indexOf(path.charAt(i-1))>=0&&path.startsWith(input,i))return true;
        return false;
    }
    // Keep click commands executable without advertising them as user-facing commands.
    private static com.mojang.brigadier.tree.LiteralCommandNode<FabricClientCommandSource> hidden(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        return new HiddenCommand(builder.build());
    }
    private static final class HiddenCommand extends com.mojang.brigadier.tree.LiteralCommandNode<FabricClientCommandSource> {
        HiddenCommand(com.mojang.brigadier.tree.LiteralCommandNode<FabricClientCommandSource> node) {
            super(node.getLiteral(),node.getCommand(),node.getRequirement(),node.getRedirect(),node.getRedirectModifier(),node.isFork());
            node.getChildren().forEach(this::addChild);
        }
        @Override public java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> listSuggestions(
                    com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder suggestions) {
            return suggestions.buildFuture();
        }
        // Fabric copies nodes into Minecraft's suggestion dispatcher via createBuilder().
        @Override public com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> createBuilder() {
            return new com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>(getLiteral()) {
                @Override public com.mojang.brigadier.tree.LiteralCommandNode<FabricClientCommandSource> build() {
                    return new HiddenCommand(super.build());
                }
            }.requires(getRequirement()).forward(getRedirect(),getRedirectModifier(),isFork()).executes(getCommand());
        }
    }

}
