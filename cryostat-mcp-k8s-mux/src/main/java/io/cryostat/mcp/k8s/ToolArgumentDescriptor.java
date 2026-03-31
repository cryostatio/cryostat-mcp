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

import java.util.Objects;

/**
 * Type-safe descriptor for tool arguments. Defines the name, description, requirement status, and
 * type of a tool parameter.
 */
public class ToolArgumentDescriptor {

    private final String name;
    private final String description;
    private final boolean required;
    private final Class<?> type;

    /**
     * Creates a new tool argument descriptor.
     *
     * @param name The argument name
     * @param description The argument description
     * @param required Whether the argument is required
     * @param type The Java type of the argument
     */
    public ToolArgumentDescriptor(
            String name, String description, boolean required, Class<?> type) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.required = required;
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Gets the argument name.
     *
     * @return The argument name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the argument description.
     *
     * @return The argument description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if the argument is required.
     *
     * @return true if required, false otherwise
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Gets the argument type.
     *
     * @return The Java type of the argument
     */
    public Class<?> getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolArgumentDescriptor that = (ToolArgumentDescriptor) o;
        return required == that.required
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, required, type);
    }

    @Override
    public String toString() {
        return "ToolArgumentDescriptor{"
                + "name='"
                + name
                + '\''
                + ", description='"
                + description
                + '\''
                + ", required="
                + required
                + ", type="
                + type.getSimpleName()
                + '}';
    }
}
