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

import java.util.Date;

/**
 * Signals that no archived recordings exist covering the requested time range for the given JVM.
 */
public class UnsatisfiableRangeException extends Exception {

    public UnsatisfiableRangeException(String jvmId, Date from, Date to) {
        super(
                String.format(
                        "No archived recordings found for jvmId '%s' covering the range [%s, %s]",
                        jvmId, from, to));
    }
}
