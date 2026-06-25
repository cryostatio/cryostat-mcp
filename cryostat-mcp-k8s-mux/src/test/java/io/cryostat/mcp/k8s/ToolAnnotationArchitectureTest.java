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

import com.tngtech.archunit.core.domain.JavaAnnotation;
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
                        .should()
                        .beAnnotatedWith(MetaField.class)
                        .because(
                                "all @Tool annotated methods must specify the tool-level using"
                                        + " @MetaField annotation");

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
                Optional<JavaAnnotation<JavaMethod>> metaFieldAnnotation =
                        method.getAnnotations().stream()
                                .filter(
                                        ann ->
                                                ann.getRawType()
                                                        .getName()
                                                        .equals(MetaField.class.getName()))
                                .findFirst();

                if (metaFieldAnnotation.isEmpty()) {
                    String message =
                            String.format(
                                    "Method %s.%s() is annotated with @Tool but missing @MetaField"
                                            + " annotation",
                                    method.getOwner().getSimpleName(), method.getName());
                    events.add(SimpleConditionEvent.violated(method, message));
                    return;
                }

                JavaAnnotation<JavaMethod> annotation = metaFieldAnnotation.get();

                String prefix = (String) annotation.get("prefix").orElse(null);
                String name = (String) annotation.get("name").orElse(null);
                String value = (String) annotation.get("value").orElse(null);

                boolean isValid = true;
                StringBuilder errorMessage = new StringBuilder();

                if (!ToolLevelFilter.TOOL_LEVEL_META_PREFIX.equals(prefix)) {
                    isValid = false;
                    errorMessage.append(
                            String.format(
                                    "Expected prefix '%s' but found '%s'. ",
                                    ToolLevelFilter.TOOL_LEVEL_META_PREFIX, prefix));
                }

                if (!ToolLevelFilter.TOOL_LEVEL_META_NAME.equals(name)) {
                    isValid = false;
                    errorMessage.append(
                            String.format(
                                    "Expected name '%s' but found '%s'. ",
                                    ToolLevelFilter.TOOL_LEVEL_META_NAME, name));
                }

                if (value == null
                        || Arrays.stream(ToolLevelFilter.ToolLevel.values())
                                .noneMatch(level -> level.name().equals(value))) {
                    isValid = false;
                    errorMessage.append(
                            String.format(
                                    "Expected value to be one of %s but found '%s'. ",
                                    Arrays.toString(ToolLevelFilter.ToolLevel.values()), value));
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
}
