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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Descriptor for a non-directed tool that operates across all Cryostat instances. Non-directed
 * tools query all available instances and aggregate the results using a custom aggregation
 * strategy.
 *
 * @param <T> The type of result returned by the tool
 */
public class NonDirectedToolDescriptor<T> {

    private final String name;
    private final String description;
    private final List<ToolArgumentDescriptor> arguments;
    private final ToolInvoker<T> invoker;
    private final AggregationStrategy<T> aggregationStrategy;
    private final Class<T> returnType;

    private NonDirectedToolDescriptor(Builder<T> builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.description =
                Objects.requireNonNull(builder.description, "description must not be null");
        this.arguments = Collections.unmodifiableList(new ArrayList<>(builder.arguments));
        this.invoker = Objects.requireNonNull(builder.invoker, "invoker must not be null");
        this.aggregationStrategy =
                Objects.requireNonNull(
                        builder.aggregationStrategy, "aggregationStrategy must not be null");
        this.returnType = Objects.requireNonNull(builder.returnType, "returnType must not be null");
    }

    /**
     * Gets the tool name.
     *
     * @return The tool name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the tool description.
     *
     * @return The tool description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the list of argument descriptors.
     *
     * @return Immutable list of argument descriptors
     */
    public List<ToolArgumentDescriptor> getArguments() {
        return arguments;
    }

    /**
     * Gets the tool invoker function.
     *
     * @return The invoker that calls the tool on a single instance
     */
    public ToolInvoker<T> getInvoker() {
        return invoker;
    }

    /**
     * Gets the aggregation strategy.
     *
     * @return The strategy for aggregating results from multiple instances
     */
    public AggregationStrategy<T> getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * Gets the return type.
     *
     * @return The Java type of the result
     */
    public Class<T> getReturnType() {
        return returnType;
    }

    /**
     * Creates a new builder for NonDirectedToolDescriptor.
     *
     * @param <T> The type of result returned by the tool
     * @return A new builder instance
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder for NonDirectedToolDescriptor.
     *
     * @param <T> The type of result returned by the tool
     */
    public static class Builder<T> {
        private String name;
        private String description;
        private final List<ToolArgumentDescriptor> arguments = new ArrayList<>();
        private ToolInvoker<T> invoker;
        private AggregationStrategy<T> aggregationStrategy;
        private Class<T> returnType;

        private Builder() {}

        /**
         * Sets the tool name.
         *
         * @param name The tool name
         * @return This builder
         */
        public Builder<T> name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the tool description.
         *
         * @param description The tool description
         * @return This builder
         */
        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Adds an argument descriptor.
         *
         * @param argument The argument descriptor to add
         * @return This builder
         */
        public Builder<T> addArgument(ToolArgumentDescriptor argument) {
            this.arguments.add(Objects.requireNonNull(argument, "argument must not be null"));
            return this;
        }

        /**
         * Sets the tool invoker.
         *
         * @param invoker The invoker function
         * @return This builder
         */
        public Builder<T> invoker(ToolInvoker<T> invoker) {
            this.invoker = invoker;
            return this;
        }

        /**
         * Sets the aggregation strategy.
         *
         * @param aggregationStrategy The aggregation strategy
         * @return This builder
         */
        public Builder<T> aggregationStrategy(AggregationStrategy<T> aggregationStrategy) {
            this.aggregationStrategy = aggregationStrategy;
            return this;
        }

        /**
         * Sets the return type.
         *
         * @param returnType The Java type of the result
         * @return This builder
         */
        public Builder<T> returnType(Class<T> returnType) {
            this.returnType = returnType;
            return this;
        }

        /**
         * Builds the NonDirectedToolDescriptor.
         *
         * @return A new NonDirectedToolDescriptor instance
         * @throws NullPointerException if any required field is null
         */
        public NonDirectedToolDescriptor<T> build() {
            return new NonDirectedToolDescriptor<>(this);
        }
    }

    @Override
    public String toString() {
        return "NonDirectedToolDescriptor{"
                + "name='"
                + name
                + '\''
                + ", description='"
                + description
                + '\''
                + ", arguments="
                + arguments.size()
                + ", returnType="
                + returnType.getSimpleName()
                + '}';
    }
}
