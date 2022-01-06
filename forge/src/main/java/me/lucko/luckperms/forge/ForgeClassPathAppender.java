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

package me.lucko.luckperms.forge;

import cpw.mods.modlauncher.TransformingClassLoader;
import me.lucko.luckperms.common.plugin.classpath.ClassPathAppender;
import me.lucko.luckperms.common.plugin.logging.PluginLogger;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Extends {@link URLClassLoader} as modlauncher lacks the ability to dynamically add jars to the classpath.
 */
public class ForgeClassPathAppender implements ClassPathAppender {
    private final PluginLogger logger;
    private final URLClassLoader delegatedClassLoader;

    public ForgeClassPathAppender(PluginLogger logger) {
        this.logger = logger;
        this.delegatedClassLoader = getDelegatedClassLoader((TransformingClassLoader) Thread.currentThread().getContextClassLoader());
    }

    @Override
    public void addJarToClasspath(Path file) {
        // For debugging purposes
        // this.logger.info("Adding " + file + " to the classpath");

        try {
            addURL(this.delegatedClassLoader, file.toUri().toURL());
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void close() {
        // no-op
    }

    private URLClassLoader getDelegatedClassLoader(TransformingClassLoader classLoader) {
        try {
            // sun.misc.Unsafe.theUnsafe
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Field delegatedClassLoaderField = TransformingClassLoader.class.getDeclaredField("delegatedClassLoader");
            long delegatedClassLoaderOffset = unsafe.objectFieldOffset(delegatedClassLoaderField);
            return (URLClassLoader) unsafe.getObject(classLoader, delegatedClassLoaderOffset);
        } catch (Throwable throwable) {
            this.logger.severe("Encountered an error getting delegated ClassLoader", throwable);
            return null;
        }
    }

    private void addURL(URLClassLoader classLoader, URL url) {
        try {
            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURLMethod.setAccessible(true);
            addURLMethod.invoke(classLoader, url);
        } catch (Throwable throwable) {
            this.logger.severe("Encountered an error adding url", throwable);
        }
    }

}
