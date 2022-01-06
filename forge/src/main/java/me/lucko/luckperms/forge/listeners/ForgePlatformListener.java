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

package me.lucko.luckperms.forge.listeners;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.locale.Message;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.event.SuggestCommandsEvent;
import me.lucko.luckperms.forge.model.ForgeUser;
import net.luckperms.api.util.Tristate;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.OpList;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ForgePlatformListener {
    private final LPForgePlugin plugin;
    private final Map<CommandNode<CommandSource>, String> permissions;

    public ForgePlatformListener(LPForgePlugin plugin) {
        this.plugin = plugin;
        this.permissions = new HashMap<>();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        this.permissions.clear();
        getPermissions(event.getDispatcher().getRoot()).forEach((key, value) -> this.permissions.put(key, "command." + value));
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        CommandContextBuilder<CommandSource> context = event.getParseResults().getContext();
        CommandSource source = context.getSource();

        if (!this.plugin.getConfiguration().get(ConfigKeys.OPS_ENABLED)) {
            for (ParsedCommandNode<CommandSource> node : context.getNodes()) {
                if (!(node.getNode() instanceof LiteralCommandNode)) {
                    continue;
                }

                String name = node.getNode().getName().toLowerCase(Locale.ROOT);
                if (name.equals("op") || name.equals("deop")) {
                    Message.OP_DISABLED.send(this.plugin.getSenderFactory().wrap(source));
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            return;
        }

        StringReader stringReader = new StringReader(event.getParseResults().getReader().getString());
        if (stringReader.canRead() && stringReader.peek() == '/') {
            stringReader.skip();
        }

        ParseResults<CommandSource> parseResults = context.getDispatcher().parse(stringReader, source.withPermission(4));
        for (ParsedCommandNode<CommandSource> parsedNode : parseResults.getContext().getNodes()) {
            if (hasPermission(source, parsedNode.getNode())) {
                continue;
            }

            event.setException(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.getReader()));
            break;
        }

        event.setParseResults(parseResults);
    }

    @SubscribeEvent
    public void onSuggestCommands(SuggestCommandsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayerEntity)) {
            return;
        }

        RootCommandNode<CommandSource> node = new RootCommandNode<>();
        filterCommands(event.getSource(), event.getNode(), node);
        event.setNode(node);
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        if (!this.plugin.getConfiguration().get(ConfigKeys.OPS_ENABLED)) {
            OpList ops = event.getServer().getPlayerList().getOps();
            ops.getEntries().clear();
            try {
                ops.save();
            } catch (IOException ex) {
                this.plugin.getLogger().severe("Encountered an error while saving ops", ex);
            }
        }
    }

    private void filterCommands(CommandSource source, CommandNode<CommandSource> fromNode, CommandNode<CommandSource> toNode) {
        for (CommandNode<CommandSource> fromChildNode : fromNode.getChildren()) {
            if (!hasPermission(source, fromChildNode)) {
                continue;
            }

            CommandNode<CommandSource> toChildNode = fromChildNode.createBuilder().build();
            toNode.addChild(toChildNode);

            if (!fromChildNode.getChildren().isEmpty()) {
                filterCommands(source, fromChildNode, toChildNode);
            }
        }
    }

    private boolean hasPermission(CommandSource source, CommandNode<CommandSource> node) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            return node.canUse(source);
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        String permission = this.permissions.get(node);
        if (permission != null) {
            ForgeUser user = this.plugin.getContextManager().getUser(player);
            Tristate state = user.checkPermission(permission);
            if (state != Tristate.UNDEFINED) {
                return state.asBoolean();
            }
        }

        return node.canUse(source);
    }

    private <T> Map<CommandNode<T>, String> getPermissions(CommandNode<T> node) {
        Map<CommandNode<T>, String> permissions = new HashMap<>();
        for (CommandNode<T> childNode : node.getChildren()) {
            String name = childNode.getName().toLowerCase(Locale.ROOT)
                    .replace("=", "eq")
                    .replace("<", "lt")
                    .replace("<=", "le")
                    .replace(">", "gt")
                    .replace(">=", "ge")
                    .replace("*", "all");
            permissions.putIfAbsent(childNode, name);

            if (!childNode.getChildren().isEmpty()) {
                getPermissions(childNode).forEach((key, value) -> permissions.putIfAbsent(key, name + "." + value));
            }
        }

        return permissions;
    }

}
