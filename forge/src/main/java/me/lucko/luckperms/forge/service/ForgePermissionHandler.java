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

package me.lucko.luckperms.forge.service;

import com.mojang.authlib.GameProfile;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.model.ForgeUser;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.context.IContext;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ForgePermissionHandler implements IPermissionHandler {

    private final LPForgePlugin plugin;
    private final Map<String, DefaultPermissionLevel> permissions;
    private final Map<String, String> descriptions;

    public ForgePermissionHandler(LPForgePlugin plugin) {
        this.plugin = plugin;
        this.permissions = new HashMap<>();
        this.descriptions = new HashMap<>();
    }

    @Override
    public void registerNode(String node, DefaultPermissionLevel level, String desc) {
        permissions.put(node, level);

        if (!desc.isEmpty()) {
            descriptions.put(node, desc);
        }
    }

    @Override
    public Collection<String> getRegisteredNodes() {
        return Collections.unmodifiableSet(permissions.keySet());
    }

    @Override
    public boolean hasPermission(GameProfile profile, String node, @Nullable IContext context) {
        ForgeUser user = this.plugin.getContextManager().getUser(profile.getId());

        Tristate value = user.checkPermission(node);
        if (value != Tristate.UNDEFINED) {
            return value.asBoolean();
        }

        DefaultPermissionLevel level = permissions.getOrDefault(node, DefaultPermissionLevel.NONE);
        if (level == DefaultPermissionLevel.ALL) {
            return true;
        } else if (level == DefaultPermissionLevel.OP) {
            return this.plugin.getBootstrap().getServer()
                    .map(MinecraftServer::getPlayerList)
                    .map(playerList -> playerList.isOp(profile))
                    .orElse(false);
        } else {
            return false;
        }
    }

    @Override
    public String getNodeDescription(String node) {
        return descriptions.getOrDefault(node, "");
    }
}