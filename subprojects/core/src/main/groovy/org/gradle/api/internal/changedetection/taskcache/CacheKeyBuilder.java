/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.taskcache;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.gradle.api.file.FileCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

public class CacheKeyBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheKeyBuilder.class);

    private static final long NULL = 3260143849197285584L;
    private static final long DIRECTORY = 8037801855872460951L;
    private static final long FILE = 6261364397481836986L;
    private static final long MISSING_FILE = 7889364092196628927L;
    private static final long COLLECTION = 8625366162395921885L;
    private static final long MAP = 4507968025271448341L;

    private final String rootPath;
    private final Hasher hasher;
    private final OutputStream hasherStream;
    private final boolean includeFileContents;

    private CacheKeyBuilder(String rootPath, Hasher hasher, OutputStream outputStream, boolean includeFileContents) {
        this.rootPath = rootPath;
        this.hasher = hasher;
        this.hasherStream = outputStream;
        this.includeFileContents = includeFileContents;
    }

    public static CacheKeyBuilder builder(File rootDir) {
        Hasher hasher = Hashing.md5().newHasher();
        return new CacheKeyBuilder(rootDir.getAbsolutePath(), hasher, Funnels.asOutputStream(hasher), true);
    }

    public CacheKeyBuilder withoutFileContents() {
        return new CacheKeyBuilder(rootPath, hasher, hasherStream, false);
    }

    public void put(Object value) {
        LOGGER.debug("Digesting {} for cache key", value);
        if (value == null) {
            hasher.putLong(NULL);
        } else if (value instanceof String) {
            putString((String) value);
        } else if (value instanceof Boolean) {
            hasher.putBoolean((Boolean) value);
        } else if (value instanceof byte[]) {
            hasher.putBytes((byte[]) value);
        } else if (value instanceof Byte) {
            hasher.putShort((Byte) value);
        } else if (value instanceof Short) {
            hasher.putShort((Short) value);
        } else if (value instanceof Integer) {
            hasher.putInt((Integer) value);
        } else if (value instanceof Long) {
            hasher.putLong((Long) value);
        } else if (value instanceof Float) {
            hasher.putFloat((Float) value);
        } else if (value instanceof Double) {
            hasher.putDouble((Double) value);
        } else if (value instanceof File) {
            putFile((File) value);
        } else if (value instanceof FileCollection) {
            Set<File> files = ((FileCollection) value).getFiles();
            if (!(files instanceof SortedSet)) {
                files = ImmutableSortedSet.copyOf(files);
            }
            putCollection(files);
        } else if (value instanceof Collection) {
            putCollection((Collection) value);
        } else if (value instanceof Map) {
            putMap((Map<?, ?>) value);
        } else {
            hasher.putInt(value.hashCode());
        }
    }

    public HashCode build() {
        return hasher.hash();
    }

    private void putCollection(Collection<?> collection) {
        // TODO Make sure self-referencing collections don't create infinite loops
        hasher.putLong(COLLECTION);
        hasher.putInt(collection.size());
        for (Object item : collection) {
            put(item);
        }
    }

    private void putMap(Map<?, ?> map) {
        // TODO Make sure self-referencing maps don't create infinite loops
        hasher.putLong(MAP);
        hasher.putInt(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            put(entry.getKey());
            put(entry.getValue());
        }
    }

    private void putFile(File file) {
        if (!file.exists()) {
            hasher.putLong(MISSING_FILE);
        } else if (file.isDirectory()) {
            hasher.putLong(DIRECTORY);
        } else {
            hasher.putLong(FILE);
            if (includeFileContents) {
                try {
                    Files.asByteSource(file).copyTo(hasherStream);
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            }
        }
        putFilePath(file);
    }

    private void putFilePath(File file) {
        String path;
        if (rootPath != null) {
            String absolutePath = file.getAbsolutePath();
            if (!absolutePath.startsWith(rootPath + "/")) {
                throw new CacheKeyException("File " + file + " is not under root path " + rootPath);
            }
            path = absolutePath.substring(rootPath.length() + 1);
        } else {
            path = file.getAbsolutePath();
        }
        putString(path);
    }

    private void putString(String value) {
        // Use consistent encoding to ensure match between platforms
        hasher.putString(value, Charsets.UTF_16);
    }
}
