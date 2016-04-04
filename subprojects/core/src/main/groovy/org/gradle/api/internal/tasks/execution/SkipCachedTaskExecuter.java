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

package org.gradle.api.internal.tasks.execution;

import com.google.common.hash.HashCode;
import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.taskcache.*;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class SkipCachedTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipCachedTaskExecuter.class);

    private final TaskResultCache taskResultCache;
    private final TaskResultPacker taskResultPacker;
    private final TaskInputHasher taskInputHasher;
    private final TaskExecuter delegate;

    public SkipCachedTaskExecuter(TaskResultCache taskResultCache, TaskResultPacker taskResultPacker, TaskInputHasher taskInputHasher, TaskExecuter delegate) {
        this.taskResultCache = taskResultCache;
        this.taskResultPacker = taskResultPacker;
        this.taskInputHasher = taskInputHasher;
        this.delegate = delegate;
        LOGGER.info("Using {}", taskResultCache.getDescription());
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        Clock clock = new Clock();

        // Do not try to cache tasks that produce no output
        boolean hasOutputs = !task.getOutputs().getFiles().isEmpty();

        // Do not cache tasks that explicitly state that they shouldn't be
        boolean shouldCache;
        try {
            shouldCache = hasOutputs && task.getCacheIf().isSatisfiedBy(task);
        } catch (Throwable t) {
            state.executed(new GradleException(String.format("Could not evaluate cacheIf predicate for %s.", task), t));
            return;
        }

        LOGGER.debug("Determining if {} is cached already", task);

        File cacheRootDir = task.getProject().getProjectDir();
        HashCode cacheKey = null;
        if (shouldCache) {
            try {
                cacheKey = taskInputHasher.createHash(task, cacheRootDir);
                LOGGER.debug("Cache key for {} is {}", task, cacheKey);
            } catch (CacheKeyException e) {
                LOGGER.info(String.format("Could not build cache key for task %s", task), e);
            }
        } else {
            if (!hasOutputs) {
                LOGGER.debug("Not caching {} as task declares no outputs", task);
            } else {
                LOGGER.debug("Not caching {} as task cacheIf is false.", task);
            }
        }

        if (cacheKey != null) {
            try {
                TaskResultInput cachedResult = taskResultCache.get(cacheKey);
                if (cachedResult != null) {
                    taskResultPacker.unpack(cacheRootDir, cachedResult);
                    LOGGER.info("Unpacked result for {} from cache (took {}).", task, clock.getTime());
                    state.upToDate("CACHED");
                    return;
                }
            } catch (IOException e) {
                LOGGER.info("Could not load cached results for " + task + " with cache key " + cacheKey, e);
            }
        }

        executeDelegate(task, state, context);

        if (cacheKey != null && state.getFailure() == null) {
            try {
                TaskResultOutput cachedResult = taskResultPacker.pack(cacheRootDir, task.getOutputs().getFiles());
                taskResultCache.put(cacheKey, cachedResult);
            } catch (IOException e) {
                LOGGER.info("Could not cache results for " + task + " for cache key " + cacheKey, e);
            }
        }
    }

    private void executeDelegate(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        delegate.execute(task, state, context);
    }
}
