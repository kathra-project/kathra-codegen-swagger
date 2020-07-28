/*
 * Copyright (c) 2020. The Kathra Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *    IRT SystemX (https://www.kathra.org/)
 *
 */

package org.kathra.codegen.controller;

import org.kathra.utils.ConfigManager;

/**
 * @author Jérémy Guillemot <Jeremy.Guillemot@kathra.org>
 */
public class Config extends ConfigManager {

    private String artifactHostUrl;
    private String repositoryPython;

    public Config() {
        artifactHostUrl = getProperty("ARTIFACT_REPOSITORY_URL");
        repositoryPython = getProperty("ARTIFACT_PIP_REPOSITORY_NAME");
    }

    public String getRepositoryPython() {
        return repositoryPython;
    }

    public String getArtifactHostUrl() {
        return artifactHostUrl;
    }
}
