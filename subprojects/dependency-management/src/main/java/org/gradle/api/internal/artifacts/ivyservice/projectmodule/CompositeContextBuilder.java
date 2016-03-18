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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.util.GUtil;

import java.io.File;

public class CompositeContextBuilder {
    private final GradleLauncherFactory launcherFactory;
    private final DefaultCompositeBuildContext context = new DefaultCompositeBuildContext();

    public CompositeContextBuilder(GradleLauncherFactory launcherFactory) {
        this.launcherFactory = launcherFactory;
    }

    public void addParticipant(String name, File rootDir) {
        registerParticipant(launcherFactory, name, rootDir, context);
    }

    private static void registerParticipant(GradleLauncherFactory gradleLauncherFactory, String buildName, File buildDir, DefaultCompositeBuildContext context) {
        StartParameter startParameter = new StartParameter();
        startParameter.setSearchUpwards(false);
        startParameter.setProjectDir(buildDir);

        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameter);
        try {
            GradleInternal gradle = (GradleInternal) gradleLauncher.getBuildAnalysis().getGradle();
            for (Project project : gradle.getRootProject().getAllprojects()) {
                ProjectInternal defaultProject = (ProjectInternal) project;

                String group = defaultProject.getGroup().toString();
                String name = defaultProject.getName();
                if (GUtil.isTrue(group) && GUtil.isTrue(name)) {
                    String projectPath = buildName + ":" + defaultProject.getPath();
                    context.register(new DefaultModuleIdentifier(group, name), projectPath, defaultProject.getProjectDir());
                }
            }
        } finally {
            gradleLauncher.stop();
        }
    }

    public CompositeBuildContext build() {
        return context;
    }
}
