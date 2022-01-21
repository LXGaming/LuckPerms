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

package me.lucko.luckperms.forge.context;

import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.context.manager.ContextManager;
import me.lucko.luckperms.common.context.manager.QueryOptionsCache;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.model.ForgeUser;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.query.OptionKey;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ForgeContextManager extends ContextManager<ServerPlayerEntity, ServerPlayerEntity> {
    public static final OptionKey<Boolean> INTEGRATED_SERVER_OWNER = OptionKey.of("integrated_server_owner", Boolean.class);

    private final Map<UUID, ForgeUser> users;

    public ForgeContextManager(LPForgePlugin plugin) {
        super(plugin, ServerPlayerEntity.class, ServerPlayerEntity.class);
        this.users = new HashMap<>();
    }

    @Override
    public UUID getUniqueId(ServerPlayerEntity player) {
        return player.getUUID();
    }

    @Override
    public QueryOptionsCache<ServerPlayerEntity> getCacheFor(ServerPlayerEntity subject) {
        if (subject == null) {
            throw new NullPointerException("subject");
        }

        return getUser(subject).getQueryOptionsCache();
    }

    @Override
    public QueryOptions formQueryOptions(ServerPlayerEntity subject, ImmutableContextSet contextSet) {
        QueryOptions.Builder builder = this.plugin.getConfiguration().get(ConfigKeys.GLOBAL_QUERY_OPTIONS).toBuilder();
        if (subject.getServer() != null && subject.getServer().isSingleplayerOwner(subject.getGameProfile())) {
            builder.option(INTEGRATED_SERVER_OWNER, true);
        }

        return builder.context(contextSet).build();
    }

    @Override
    public void invalidateCache(ServerPlayerEntity subject) {
        ForgeUser user = this.users.get(subject.getUUID());
        if (user != null) {
            user.getQueryOptionsCache().invalidate();
        }
    }

    public void register(ServerPlayerEntity player) {
        User user = this.plugin.getUserManager().getIfLoaded(player.getUUID());
        this.users.put(player.getUUID(), new ForgeUser(user, new QueryOptionsCache<>(player, this)));
        signalContextUpdate(player);
    }

    public void unregister(ServerPlayerEntity player) {
        this.users.remove(player.getUUID());
    }

    public ForgeUser getUser(ServerPlayerEntity player) {
        return getUser(player.getUUID());
    }

    public ForgeUser getUser(UUID uniqueId) {
        ForgeUser user = this.users.get(uniqueId);
        if (user == null) {
            throw new IllegalStateException("User " + uniqueId + " is not registered");
        }

        return user;
    }

}