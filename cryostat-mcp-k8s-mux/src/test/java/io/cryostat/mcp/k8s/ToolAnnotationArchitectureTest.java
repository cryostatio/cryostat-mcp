/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.mcp.k8s;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.util.Arrays;
import java.util.Optional;

import io.cryostat.mcp.CryostatToolMetadata;
import io.cryostat.mcp.CryostatVersion;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.Tool;
import org.junit.jupiter.api.Test;

class ToolAnnotationArchitectureTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("io.cryostat.mcp.k8s");

    @Test
    void toolAnnotatedMethodsShouldAlsoHaveMetaFieldAnnotation() {
        ArchRule rule =
                methods()
                        .that()
                        .areAnnotatedWith(Tool.class)
                        .should(haveToolLevelMetadata())
                        .andShould(haveMinimumCryostatVersionMetadata())
                        .because(
                                "all @Tool annotated methods must specify tool-level and minimum"
                                    + " Cryostat version metadata using @MetaField annotations");

        rule.check(CLASSES);
    }

    @Test
    void toolAnnotatedMethodsShouldHaveValidMetaFieldAttributes() {
        ArchRule rule =
                methods()
                        .that()
                        .areAnnotatedWith(Tool.class)
                        .should(haveValidMetaFieldAnnotation())
                        .because(
                                "all @Tool annotated methods must have @MetaField with correct"
                                        + " prefix ('"
                                        + ToolLevelFilter.TOOL_LEVEL_META_PREFIX
                                        + "'), name ('"
                                        + ToolLevelFilter.TOOL_LEVEL_META_NAME
                                        + "'), and value (one of: LOW, HIGH, ALL)");

        rule.check(CLASSES);
    }

    private static ArchCondition<JavaMethod> haveValidMetaFieldAnnotation() {
        return new ArchCondition<JavaMethod>("have valid @MetaField annotation attributes") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean isValid = true;
                StringBuilder errorMessage = new StringBuilder();

                Optional<MetaField> toolLevel =
                        findMetaField(
                                method,
                                ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
                                ToolLevelFilter.TOOL_LEVEL_META_NAME);
                if (toolLevel.isEmpty()) {
                    isValid = false;
                    errorMessage.append(
                            String.format(
                                    "Expected @MetaField(prefix='%s', name='%s'). ",
                                    ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
                                    ToolLevelFilter.TOOL_LEVEL_META_NAME));
                } else if (Arrays.stream(ToolLevelFilter.ToolLevel.values())
                        .noneMatch(level -> level.name().equals(toolLevel.get().value()))) {
                    isValid = false;
                    errorMessage.append(
                            String.format(
                                    "Expected tool-level value to be one of %s but found '%s'. ",
                                    Arrays.toString(ToolLevelFilter.ToolLevel.values()),
                                    toolLevel.get().value()));
                }

                Optional<MetaField> minimumVersion =
                        findMetaField(
                                method,
                                CryostatToolMetadata.META_PREFIX,
                                CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME);
                if (minimumVersion.isEmpty()) {
                    isValid = false;
                    errorMessage.append(
                            String.format(
                                    "Expected @MetaField(prefix='%s', name='%s'). ",
                                    CryostatToolMetadata.META_PREFIX,
                                    CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME));
                } else if (CryostatVersion.parse(minimumVersion.get().value()).isEmpty()) {
                    isValid = false;
                    errorMessage.append(
                            String.format(
                                    "Expected parseable minimum Cryostat version but found '%s'. ",
                                    minimumVersion.get().value()));
                }

                if (!isValid) {
                    String message =
                            String.format(
                                    "Method %s.%s() has invalid @MetaField annotation: %s",
                                    method.getOwner().getSimpleName(),
                                    method.getName(),
                                    errorMessage.toString().trim());
                    events.add(SimpleConditionEvent.violated(method, message));
                } else {
                    events.add(SimpleConditionEvent.satisfied(method, "has valid @MetaField"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> haveToolLevelMetadata() {
        return new ArchCondition<JavaMethod>("have tool-level metadata") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean hasMetadata =
                        findMetaField(
                                        method,
                                        ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
                                        ToolLevelFilter.TOOL_LEVEL_META_NAME)
                                .isPresent();
                String message =
                        String.format(
                                "Method %s.%s() %s tool-level @MetaField annotation",
                                method.getOwner().getSimpleName(),
                                method.getName(),
                                hasMetadata ? "has" : "is missing");
                events.add(
                        hasMetadata
                                ? SimpleConditionEvent.satisfied(method, message)
                                : SimpleConditionEvent.violated(method, message));
            }
        };
    }

    private static ArchCondition<JavaMethod> haveMinimumCryostatVersionMetadata() {
        return new ArchCondition<JavaMethod>("have minimum Cryostat version metadata") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean hasMetadata =
                        findMetaField(
                                        method,
                                        CryostatToolMetadata.META_PREFIX,
                                        CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME)
                                .isPresent();
                String message =
                        String.format(
                                "Method %s.%s() %s minimum Cryostat version @MetaField annotation",
                                method.getOwner().getSimpleName(),
                                method.getName(),
                                hasMetadata ? "has" : "is missing");
                events.add(
                        hasMetadata
                                ? SimpleConditionEvent.satisfied(method, message)
                                : SimpleConditionEvent.violated(method, message));
            }
        };
    }

    private static Optional<MetaField> findMetaField(
            JavaMethod method, String prefix, String name) {
        return Arrays.stream(method.reflect().getAnnotationsByType(MetaField.class))
                .filter(metaField -> prefix.equals(metaField.prefix()))
                .filter(metaField -> name.equals(metaField.name()))
                .findFirst();
    }
}
