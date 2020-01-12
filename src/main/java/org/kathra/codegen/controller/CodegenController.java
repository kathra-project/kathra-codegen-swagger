/* 
 * Copyright 2019 The Kathra Authors.
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
 *
 *    IRT SystemX (https://www.kathra.org/)    
 *
 */

package org.kathra.codegen.controller;

import org.kathra.codegen.ClientOptInput;
import org.kathra.codegen.KathraGenerator;
import org.kathra.codegen.config.CodegenConfigurator;
import org.kathra.codegen.service.CodegenService;
import org.kathra.codegen.model.*;
import org.kathra.utils.KathraException;
import org.kathra.utils.annotations.Eager;
import org.apache.camel.cdi.ContextName;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.Yaml;
import org.zeroturnaround.zip.ZipUtil;

import javax.activation.FileDataSource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Named("CodegenController")
@ApplicationScoped
@Eager
@ContextName("Codegen")
public class CodegenController implements CodegenService {

    Config config = new Config();

        /**
    * Generate archive from template
    * 
    * @param codeGenTemplate CodeGenTemplate to generate code (required)
    * @return FileDataSource
    */
    public FileDataSource generateFromTemplate(CodeGenTemplate codeGenTemplate) throws Exception {
        if (codeGenTemplate == null) {
            throw new IllegalArgumentException("codeGenTemplate is null");
        }

        final String name = getValue(codeGenTemplate, "NAME");
        final String group = getValue(codeGenTemplate, "GROUP");
        final String version = getValue(codeGenTemplate, "VERSION");
        
        final File swaggerFile = File.createTempFile(System.getProperty("java.io.tmpdir")+Long.toString(System.nanoTime()),"swagger.yaml");
        try {
            FileUtils.writeStringToFile(swaggerFile, getValue(codeGenTemplate, "SWAGGER2_SPEC"));
            switch(codeGenTemplate.getName()) {
                case "LIBRARY_JAVA_REST_CLIENT":
                return generateClient(swaggerFile, "JAVA", name, group, version);
                case "LIBRARY_JAVA_MODEL":
                return generateModel(swaggerFile, "JAVA", name, group, version);
                case "LIBRARY_JAVA_REST_INTERFACE":
                return generateInterface(swaggerFile, "JAVA", name, group, version);
                case "SERVER_JAVA_REST":
                return generateImplementation(swaggerFile, getValue(codeGenTemplate, "IMPLEMENTATION_NAME"), "JAVA", name, group, version);
                case "LIBRARY_PYTHON_REST_CLIENT":
                return generateClient(swaggerFile, "PYTHON", name, group, version);
                case "LIBRARY_PYTHON_MODEL":
                return generateModel(swaggerFile, "PYTHON", name, group, version);
                case "LIBRARY_PYTHON_REST_INTERFACE":
                return generateInterface(swaggerFile, "PYTHON", name, group, version);
                case "SERVER_PYTHON_REST":
                return generateImplementation(swaggerFile, getValue(codeGenTemplate, "IMPLEMENTATION_NAME"), "PYTHON", name, group, version);
            }
            throw new IllegalArgumentException("Unknown template : " +codeGenTemplate.getName());
        } finally {
            swaggerFile.delete();
        }
    }
    

    private String getValue(CodeGenTemplate codeGenTemplate, String key) {
        return codeGenTemplate  .getArguments()
                                .stream()
                                .filter(arg -> key.equals(arg.getKey()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalAccessError("Unable to find argument '"+key+"'"))
                                .getValue();
    }

    /**
    * Get all templates for codegen generation
    * 
    * @return List<CodeGenTemplate>
    */
    public List<CodeGenTemplate> getTemplates() {
        List<CodeGenTemplate> templates = new ArrayList<>();
        templates.add(getLibraryTemplate("JAVA", "REST_CLIENT"));
        templates.add(getLibraryTemplate("JAVA", "MODEL"));
        templates.add(getLibraryTemplate("JAVA", "REST_INTERFACE"));
        templates.add(getServerTemplate("JAVA", "REST"));
        templates.add(getLibraryTemplate("PYTHON", "REST_CLIENT"));
        templates.add(getLibraryTemplate("PYTHON", "MODEL"));
        templates.add(getLibraryTemplate("PYTHON", "REST_INTERFACE"));
        templates.add(getServerTemplate("PYTHON", "REST"));
        return templates;
    }

    public CodeGenTemplate getLibraryTemplate(String language, String type) {
        return new CodeGenTemplate()
                .name("LIBRARY_"+language+"_"+type)
                .addArgumentsItem(new CodeGenTemplateArgument().key("GROUP"))
                .addArgumentsItem(new CodeGenTemplateArgument().key("NAME"))
                .addArgumentsItem(new CodeGenTemplateArgument().key("VERSION"))
                .addArgumentsItem(new CodeGenTemplateArgument().key("SWAGGER2_SPEC"));
    }

    public CodeGenTemplate getServerTemplate(String language, String type) {
        return new CodeGenTemplate()
                .name("SERVER_"+language+"_"+type)
                .addArgumentsItem(new CodeGenTemplateArgument().key("IMPLEMENTATION_NAME"))
                .addArgumentsItem(new CodeGenTemplateArgument().key("GROUP"))
                .addArgumentsItem(new CodeGenTemplateArgument().key("NAME"))
                .addArgumentsItem(new CodeGenTemplateArgument().key("VERSION"))
                .addArgumentsItem(new CodeGenTemplateArgument().key("SWAGGER2_SPEC"));
    }

    /**
    * Generate Client
    *
    * @param api The API
    * @param language The desired programming language (required)
    * @param artifactName The name of artifact to generate (optional)
    * @param groupId The groupId of artifact to generate (optional)
    * @param artifactVersion The version of artifact to generate (optional)
    * @return FileDataSource
    */
    public FileDataSource generateClient(File api, String language, String artifactName, String groupId, String artifactVersion) throws Exception {
        if (language.equalsIgnoreCase("JAVA")) {
            return generateJava(api, artifactName, groupId, artifactVersion, "client", null);
        } else if (language.equalsIgnoreCase("PYTHON")) {
            return generatePython(api, artifactName, groupId, artifactVersion, "client", null);
        } else {
            return null;
        }
    }

    /**
    * Generate Implementation
    *
    * @param api The API
    * @param implemName The name of artifact to generate (required)
    * @param language The desired programming language (required)
    * @param artifactName The name of artifact to generate (optional)
    * @param groupId The groupId of artifact to generate (optional)
    * @param artifactVersion The version of artifact to generate (optional)
    * @return FileDataSource
    */
    public FileDataSource generateImplementation(File api, String implemName, String language, String artifactName, String groupId, String artifactVersion) throws Exception {
        if (language.equalsIgnoreCase("JAVA")) {
            return generateJava(api, artifactName, groupId, artifactVersion, "implem", implemName);
        } else if (language.equalsIgnoreCase("PYTHON")) {
            return generatePythonImpl(api, artifactName, groupId, artifactVersion, implemName);
        } else {
            return null;
        }
    }

    /**
    * Generate Interface
    *
    * @param api The API definition file (required)
    * @param language The desired programming language (required)
    * @param artifactName The name of artifact to generate (optional)
    * @param groupId The groupId of artifact to generate (optional)
    * @param artifactVersion The version of artifact to generate (optional)
    * @return FileDataSource
    */
    public FileDataSource generateInterface(File api, String language, String artifactName, String groupId, String artifactVersion) throws Exception {
        if (language.equalsIgnoreCase("JAVA")) {
            return generateJava(api, artifactName, groupId, artifactVersion, "interface", null);
        } else if (language.equalsIgnoreCase("PYTHON")) {
            return generatePython(api, artifactName, groupId, artifactVersion, "interface", null);
        } else {
            return null;
        }
    }

    /**
    * Generate Model
    *
    * @param api The API definition file (required)
    * @param language The desired programming language (required)
    * @param artifactName The name of artifact to generate (optional)
    * @param groupId The groupId of artifact to generate (optional)
    * @param artifactVersion The version of artifact to generate (optional)
    * @return FileDataSource
    */
    public FileDataSource generateModel(File api, String language, String artifactName, String groupId, String artifactVersion) throws Exception {
        if (language.equalsIgnoreCase("JAVA")) {
            return generateJava(api, artifactName, groupId, artifactVersion, "model", null);
        } else if (language.equalsIgnoreCase("PYTHON")) {
            return generatePython(api, artifactName, groupId, artifactVersion, "model", null);
        } else {
            return null;
        }
    }

    public FileDataSource generatePython(File spec, String artifactName, String groupId, String artifactVersion, String library, String implemName) throws Exception {
        Yaml yaml = new Yaml();

        if (groupId == null || artifactName == null || artifactVersion == null) {
            LinkedHashMap swaggerFile =  yaml.load(FileUtils.openInputStream(spec));
            if (groupId == null) groupId = getPropInSwaggerFile(swaggerFile, "x-groupId");
            if (artifactName == null) artifactName = getPropInSwaggerFile(swaggerFile, "x-artifactName");
            if (artifactVersion == null) artifactVersion = getPropInSwaggerFile(swaggerFile, "version");
        }

        String groupName = groupId.substring(groupId.lastIndexOf('.') + 1);
        StringBuilder artifactIdSb = new StringBuilder().append(groupName).append("-").append(artifactName).append("-");

        if (library.equalsIgnoreCase("implem")) {
            artifactIdSb.append(implemName);
        } else {
            artifactIdSb.append(library);
        }

        CodegenConfigurator configurator = new CodegenConfigurator();

        String artifactId = artifactIdSb.toString();
        initializeConfigurator(spec, "KathraPython", artifactName, groupId, artifactVersion, library, artifactId, configurator);
        configurator.setArtifactVersionApi(artifactVersion);

        return getFileDataSource(spec, library, artifactIdSb, artifactId, configurator);
    }

    /**
     * This thingy is crazy !! upgrading or improvements are forbidden ! Coded to
     * Ce truc est legacy de ouf !! alors on arrête les conneries et plus de nouveaux dev dessus !
     *
     * @param spec
     * @param artifactName
     * @param groupId
     * @param artifactVersion
     * @param implemName
     * @return
     * @throws Exception
     */
    @Deprecated
    public FileDataSource generatePythonImpl(File spec, String artifactName, String groupId, String artifactVersion, String implemName) throws Exception {
        Yaml yaml = new Yaml();

        LinkedHashMap swaggerFile =  yaml.load(FileUtils.openInputStream(spec));
        String groupIdApi = getPropInSwaggerFile(swaggerFile, "x-groupId");
        String artifactNameApi = getPropInSwaggerFile(swaggerFile, "x-artifactName");
        String artifactVersionApi = getPropInSwaggerFile(swaggerFile, "version");

        String groupName = groupId.substring(groupId.lastIndexOf('.') + 1);
        StringBuilder artifactIdSb = new StringBuilder().append(groupName).append("-").append(artifactNameApi).append("-");

        artifactIdSb.append(artifactName.replaceAll("-",""));

        CodegenConfigurator configurator = new CodegenConfigurator();

        String artifactId = artifactIdSb.toString();
        initializeConfigurator(spec, "KathraPython", artifactNameApi, groupIdApi, artifactVersion, "implem", artifactId, configurator);
        configurator.setArtifactVersionApi(artifactVersionApi);

        return getFileDataSource(spec, "implem", artifactIdSb, artifactId, configurator);
    }


    public FileDataSource generateJava(File spec, String artifactName, String groupId, String artifactVersion, String library, String implemName) throws Exception {

         if (groupId == null || artifactName == null || artifactVersion == null) {
             Yaml yaml = new Yaml();
             LinkedHashMap swaggerFile =  yaml.load(FileUtils.openInputStream(spec));
            if (groupId == null) groupId = getPropInSwaggerFile(swaggerFile, "x-groupId");
            if (artifactName == null) artifactName = getPropInSwaggerFile(swaggerFile, "x-artifactName");
            if (artifactVersion == null) artifactVersion = getPropInSwaggerFile(swaggerFile, "version");
        }

        if (!artifactVersion.trim().endsWith("-SNAPSHOT")) {
            artifactVersion = artifactVersion + "-SNAPSHOT";
        }

        String groupName = groupId.substring(groupId.lastIndexOf('.') + 1);
        StringBuilder sb = new StringBuilder().append(groupName).append("-").append(artifactName).append("-");
        String artifactId;
        if (library.equalsIgnoreCase("implem")) {
            sb.append(implemName);
            artifactId = artifactName;
        } else {
            sb.append(library);
            artifactId = sb.toString();
        }

        CodegenConfigurator configurator = new CodegenConfigurator();

        initializeConfigurator(spec, "KathraJava", artifactName, groupId, artifactVersion, library, artifactId, configurator);

        if (library.equalsIgnoreCase("implem")) {
            configureArtifactApiInterface(spec, configurator);
        }

        return getFileDataSource(spec, library, sb, artifactId, configurator);
    }

    private FileDataSource getFileDataSource(File spec, String library, StringBuilder sb, String artifactId, CodegenConfigurator configurator) throws IOException {
        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        sb.setLength(0);
        sb.append(spec.getPath().toLowerCase()).append("d").append(File.separatorChar);
        File dir = new File(sb.toString() + "gen");
        File zip = new File(sb.toString() + artifactId.toLowerCase() + ".zip");

        dir.mkdirs();
        new KathraGenerator().opts(clientOptInput).generate();
        if (library.equalsIgnoreCase("interface")) {
            Files.copy(spec.toPath(), Paths.get(configurator.getOutputDir().toLowerCase() + File.separatorChar + "swagger.yml"), REPLACE_EXISTING);
        }
        dir = new File(configurator.getOutputDir().toLowerCase());
        ZipUtil.pack(dir, zip);
        return new FileDataSource(zip);
    }

    private void configureArtifactApiInterface(File spec, CodegenConfigurator configurator) throws IOException, MissingArgumentException {
        Yaml yaml = new Yaml();
        LinkedHashMap swaggerFile =  yaml.load(FileUtils.openInputStream(spec));
        String groupIdApi = getPropInSwaggerFile(swaggerFile, "x-groupId");
        String artifactIdApi = getPropInSwaggerFile(swaggerFile, "x-artifactName");
        configurator.setArtifactIdApi(groupIdApi.split("\\.")[groupIdApi.split("\\.").length-1]+"-"+artifactIdApi.toLowerCase()+"-interface");
        configurator.setGroupIdApi(groupIdApi);
        configurator.setArtifactVersionApi(getPropInSwaggerFile(swaggerFile, "version") + "-SNAPSHOT");
        configurator.setApiPackage(artifactIdApi);
        configurator.setModelPackage(artifactIdApi);
    }

    private void initializeConfigurator(File spec, String language, String artifactName, String groupId, String artifactVersion, String library, String artifactId, CodegenConfigurator configurator) {
        if (isNotEmpty(spec.getPath())) {
            configurator.setInputSpec(spec.getPath());
        }

        if (isNotEmpty(language)) {
            configurator.setLang(language);
        }

        if (isNotEmpty(spec.getPath())) {
            configurator.setOutputDir(spec.getPath() + "d" + File.separatorChar + "gen" + File.separatorChar + artifactId.toLowerCase());
        }

        if (isNotEmpty(artifactName)) {
            configurator.setApiPackage(artifactName);
        }

        if (isNotEmpty(config.getArtifactHostUrl())) {
            configurator.setRepositoryUrl(config.getArtifactHostUrl());
        }
        if (isNotEmpty(config.getRepositoryPython())) {
            configurator.setRepositoryPythonName(config.getRepositoryPython());
        }

        if (isNotEmpty(artifactName)) {
            configurator.setModelPackage(artifactName.toLowerCase());
        }

        if (isNotEmpty(groupId)) {
            configurator.setGroupId(groupId);
            configurator.setInvokerPackage(groupId);
        }

        if (isNotEmpty(artifactId)) {
            configurator.setArtifactId(artifactId);
        }

        if (isNotEmpty(artifactVersion)) {
            configurator.setArtifactVersion(artifactVersion);
        }

        if (isNotEmpty(library)) {
            configurator.setLibrary(library);
        }
    }

    private String getPropInSwaggerFile(LinkedHashMap swaggerFile, String propName) throws MissingArgumentException {
        if (swaggerFile.containsKey("info")) {
            LinkedHashMap info = (LinkedHashMap) swaggerFile.get("info");
            String prop = (String) info.get(propName);
            if (prop == null || prop.isEmpty()) throw new MissingArgumentException("Missing " + propName);
            return prop;
        }
        throw new MissingArgumentException("Missing info section from swagger");
    }

}
