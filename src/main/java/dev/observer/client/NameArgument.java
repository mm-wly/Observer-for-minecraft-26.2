package dev.observer.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.*;
import java.util.Collection;
import java.util.List;

/** Brigadier's word() excludes ':', so registry names need a dedicated argument. */
public final class NameArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType INVALID=new SimpleCommandExceptionType(ObserverText.text("invalid_name"));
    @Override public String parse(StringReader reader) throws CommandSyntaxException {
        int start=reader.getCursor();while(reader.canRead()&&!Character.isWhitespace(reader.peek()))reader.skip();
        String value=reader.getString().substring(start,reader.getCursor());
        if(!value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+")){reader.setCursor(start);throw INVALID.createWithContext(reader);}
        return value;
    }
    @Override public Collection<String> getExamples() {return List.of("buried_treasure","minecraft:diamond_ore");}
}
