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

package org.gradle.api.internal.tasks.execution

import com.google.common.hash.HashCode
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.taskcache.*
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Specs
import spock.lang.Specification

public class SkipCachedTaskExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def task = Mock(TaskInternal)
    def project = Mock(Project)
    def projectDir = Mock(File)
    def outputs = Mock(TaskOutputsInternal)
    def outputFiles = Mock(FileCollection)
    def taskState = Mock(TaskStateInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskResultCache = Mock(TaskResultCache)
    def taskResultPacker = Mock(TaskResultPacker)
    def taskInputHasher = Mock(TaskInputHasher)
    def cacheKey = Mock(HashCode)

    def executer = new SkipCachedTaskExecuter(taskResultCache, taskResultPacker, taskInputHasher, delegate)

    def "skip task when cached results exist"() {
        def cachedResult = Mock(TaskResultInput)

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.getFiles() >> outputFiles
        1 * outputFiles.isEmpty() >> false
        1 * task.getCacheIf() >> Specs.SATISFIES_ALL

        1 * taskInputHasher.createHash(task, projectDir) >> cacheKey
        1 * taskResultCache.get(cacheKey) >> cachedResult
        1 * taskResultPacker.unpack(projectDir, cachedResult)
        1 * task.getProject() >> project
        1 * project.getProjectDir() >> projectDir
        1 * taskState.upToDate("CACHED")
        0 * _
    }

    def "executes task when no cached result is available"() {
        def cachedResult = Mock(TaskResultOutput)

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.getFiles() >> outputFiles
        1 * outputFiles.isEmpty() >> false
        1 * task.getCacheIf() >> Specs.SATISFIES_ALL

        1 * taskInputHasher.createHash(task, projectDir) >> cacheKey
        1 * taskResultCache.get(cacheKey) >> null
        1 * task.getProject() >> project
        1 * project.getProjectDir() >> projectDir

        then:
        1 * delegate.execute(task, taskState, taskContext)
        _ * taskState.getFailure() >> null

        then:
        1 * task.outputs >> outputs
        1 * outputs.getFiles() >> outputFiles
        1 * taskResultPacker.pack(projectDir, outputFiles) >> cachedResult
        1 * taskResultCache.put(cacheKey, cachedResult)
        0 * _
    }

    def "does not cache results when executed task fails"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.getFiles() >> outputFiles
        1 * outputFiles.isEmpty() >> false
        1 * task.getCacheIf() >> Specs.SATISFIES_ALL

        1 * taskInputHasher.createHash(task, projectDir) >> cacheKey
        1 * taskResultCache.get(cacheKey) >> null
        1 * task.getProject() >> project
        1 * project.getProjectDir() >> projectDir

        then:
        1 * delegate.execute(task, taskState, taskContext)
        _ * taskState.getFailure() >> new RuntimeException()
        0 * _
    }

    def "executes task when task doesn't declare any outputs"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.getFiles() >> outputFiles
        1 * outputFiles.isEmpty() >> true

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }

    def "executes task and does not cache results when cacheIf is false"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.getFiles() >> outputFiles
        1 * outputFiles.isEmpty() >> false
        1 * task.getCacheIf() >> Specs.SATISFIES_NONE

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }
}
