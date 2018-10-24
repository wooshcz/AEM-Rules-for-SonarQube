/*-
 * #%L
 * AEM Rules for SonarQube
 * %%
 * Copyright (C) 2015 Cognifide Limited
 * %%
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
 * #L%
 */
package com.cognifide.aemrules.htl;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cognifide.aemrules.extensions.AemRulesRulesDefinition;
import com.cognifide.aemrules.htl.lex.HtlLexer;
import com.cognifide.aemrules.htl.rules.CheckClasses;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFilePredicates;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.DefaultActiveRules;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Configuration;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.plugins.html.api.HtmlConstants;


public class HtlSensorTest {

    private static final File TEST_DIR = new File("src/test/resources/sensor");

    private HtlSensor sensor;

    private SensorContextTester tester;

    @Before
    public void setUp() {
        RulesDefinition rulesDefinition = new AemRulesRulesDefinition();
        RulesDefinition.Context context = new RulesDefinition.Context();
        rulesDefinition.define(context);
        RulesDefinition.Repository htlRepository = context.repository(CheckClasses.REPOSITORY_KEY);

        List<NewActiveRule> ar = new ArrayList<>();
        for (RulesDefinition.Rule rule : htlRepository.rules()) {
            ar.add(new ActiveRulesBuilder().create(RuleKey.of(CheckClasses.REPOSITORY_KEY, rule.key())));
        }
        ActiveRules activeRules = new DefaultActiveRules(ar);

        CheckFactory checkFactory = new CheckFactory(activeRules);
        FileLinesContextFactory fileLinesContextFactory = mock(FileLinesContextFactory.class);
        when(fileLinesContextFactory.createFor(Mockito.any(InputFile.class))).thenReturn(mock(FileLinesContext.class));

        FileSystem fileSystem = mock(FileSystem.class);
        when(fileSystem.predicates()).thenReturn(new DefaultFilePredicates(null));

        Configuration configuration = mock(Configuration.class);
        when(configuration.getStringArray(HtlConstants.FILE_EXTENSIONS_PROP_KEY)).thenReturn(HtlConstants.FILE_EXTENSIONS_DEF_VALUE.split(","));
        sensor = new HtlSensor(fileLinesContextFactory, configuration, checkFactory, fileSystem);
        tester = SensorContextTester.create(TEST_DIR);
    }

    @Test
    public void jspFile() throws Exception {
        DefaultInputFile inputFile = createInputFile(TEST_DIR, "test.jsp");
        tester.fileSystem().add(inputFile);
        sensor.execute(tester);
        assertThat(tester.allIssues()).isEmpty();
    }

    @Test
    public void htmlFile() throws Exception {
        DefaultInputFile inputFile = createInputFile(TEST_DIR, "test.html");
        tester.fileSystem().add(inputFile);
        sensor.execute(tester);
        assertThat(tester.allIssues()).isNotEmpty();
    }

    @Test
    public void cancellation() throws Exception {
        DefaultInputFile inputFile = createInputFile(TEST_DIR, "test.html");
        tester.fileSystem().add(inputFile);
        tester.setCancelled(true);
        sensor.execute(tester);
        assertThat(tester.allIssues()).isEmpty();
    }

    @Test
    public void sonarlint() throws Exception {
        DefaultInputFile inputFile = createInputFile(TEST_DIR, "test.html");
        tester.fileSystem().add(inputFile);
        sensor.execute(tester);
        String componentKey = inputFile.key();
        assertThat(tester.allIssues()).isNotEmpty();
        assertThat(tester.cpdTokens(componentKey)).isNull();
        assertThat(tester.highlightingTypeAt(componentKey, 1, 0)).isEmpty();
    }

    @Test
    public void compilationException() throws Exception {
        DefaultInputFile inputFile = createInputFile(TEST_DIR, "error.html");
        tester.fileSystem().add(inputFile);
        sensor.execute(tester);
        assertThat(tester.allAnalysisErrors()).isNotEmpty();
    }

    @Test
    public void expressionWithinHtmlComment() throws Exception {
        DefaultInputFile inputFile = createInputFile(TEST_DIR, "comment.html");
        tester.fileSystem().add(inputFile);
        sensor.execute(tester);
        assertThat(tester.allAnalysisErrors()).isNotEmpty();
    }

    @Test
    public void testDescriptor() {
        DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
        sensor.describe(descriptor);
        assertThat(descriptor.name()).isEqualTo("HTL");
        assertThat(descriptor.languages()).isEmpty();
    }

    private DefaultInputFile createInputFile(File dir, String fileName) throws IOException {
        return new TestInputFileBuilder("key", fileName)
            .setModuleBaseDir(dir.toPath())
            .setLanguage(HtmlConstants.LANGUAGE_KEY)
            .setType(InputFile.Type.MAIN)
            .initMetadata(Files.toString(new File(dir, fileName), StandardCharsets.UTF_8))
            .setCharset(StandardCharsets.UTF_8)
            .build();
    }

}