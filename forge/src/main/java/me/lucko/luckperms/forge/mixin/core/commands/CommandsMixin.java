/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.forge.mixin.core.commands;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import me.lucko.luckperms.forge.event.SuggestCommandsEvent;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Mixin into {@link Commands} for posting {@link SuggestCommandsEvent}
 */
@Mixin(value = Commands.class)
public abstract class CommandsMixin {

    @Shadow
    protected abstract void fillUsableCommands(CommandNode<CommandSource> p_197052_1_, CommandNode<ISuggestionProvider> p_197052_2_, CommandSource p_197052_3_, Map<CommandNode<CommandSource>, CommandNode<ISuggestionProvider>> p_197052_4_);

    @Redirect(
            method = "sendCommands",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/Commands;fillUsableCommands(Lcom/mojang/brigadier/tree/CommandNode;Lcom/mojang/brigadier/tree/CommandNode;Lnet/minecraft/command/CommandSource;Ljava/util/Map;)V"
            )
    )
    private void onFillUsableCommands(Commands commands, CommandNode<CommandSource> commandNode, CommandNode<ISuggestionProvider> suggestionNode, CommandSource source, Map<CommandNode<CommandSource>, CommandNode<ISuggestionProvider>> map) {
        SuggestCommandsEvent event = new SuggestCommandsEvent(source, (RootCommandNode<CommandSource>) commandNode);
        MinecraftForge.EVENT_BUS.post(event);

        // This map will be populated with the original root node, so we must clear it
        map.clear();

        // Insert the root node as it may have been replaced during the event
        map.put(event.getNode(), suggestionNode);

        fillUsableCommands(event.getNode(), suggestionNode, source.withPermission(4), map);
    }

}
