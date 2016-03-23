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
    private final TaskExecuter executer;

    public SkipCachedTaskExecuter(TaskResultCache taskResultCache, TaskResultPacker taskResultPacker, TaskExecuter executer) {
        this.taskResultCache = taskResultCache;
        this.taskResultPacker = taskResultPacker;
        this.executer = executer;
        LOGGER.info("Using {}", taskResultCache.getDescription());
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        LOGGER.debug("Determining if {} is cached already", task);
        Clock clock = new Clock();

        File cacheRootDir = task.getProject().getProjectDir();

        CacheKeyBuilder cacheKeyBuilder = CacheKeyBuilder.builder(cacheRootDir);
        cacheKeyBuilder.put(task.getInputs().getProperties());
        cacheKeyBuilder.put(task.getInputs().getFiles());
        cacheKeyBuilder.withoutFileContents().put(task.getOutputs().getFiles());
        HashCode cacheKey = cacheKeyBuilder.build();
        LOGGER.debug("Cache key for {} is {}", task, cacheKey);

        try {
            TaskResultInput cachedResult = taskResultCache.get(cacheKey);
            if (cachedResult != null) {
                taskResultPacker.unpack(cacheRootDir, cachedResult);
                LOGGER.info("Unpacked result for {} from cache (took {}).", task, clock.getTime());
                state.upToDate("CACHED");
                return;
            }
        } catch (IOException e) {
            LOGGER.info("Could not lode cached results for {}", task, e);
        }

        executer.execute(task, state, context);
        if (state.getFailure() == null) {
            try {
                TaskResultOutput cachedResult = taskResultPacker.pack(cacheRootDir, task.getOutputs().getFiles());
                taskResultCache.put(cacheKey, cachedResult);
            } catch (IOException e) {
                LOGGER.info("Could not cache results for {}", task, e);
            }
        }
    }
}
