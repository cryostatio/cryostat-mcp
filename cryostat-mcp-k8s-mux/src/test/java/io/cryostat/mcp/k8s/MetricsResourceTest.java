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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MetricsResourceTest {

    @InjectMock NonDirectedTools nonDirectedTools;

    @Test
    void testGetMetricsWithoutParameter() {
        String expectedMetrics = "# HELP test_metric\ntest_metric 1.0\n";
        when(nonDirectedTools.scrapeGlobalMetrics(0.0)).thenReturn(expectedMetrics);

        given().when()
                .get("/api/metrics")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(equalTo(expectedMetrics));

        verify(nonDirectedTools).scrapeGlobalMetrics(0.0);
    }

    @Test
    void testGetMetricsWithParameter() {
        String expectedMetrics = "# HELP filtered_metric\nfiltered_metric 25.0\n";
        when(nonDirectedTools.scrapeGlobalMetrics(25.0)).thenReturn(expectedMetrics);

        given().queryParam("minTargetScore", 25.0)
                .when()
                .get("/api/metrics")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(equalTo(expectedMetrics));

        verify(nonDirectedTools).scrapeGlobalMetrics(25.0);
    }

    @Test
    void testGetMetricsWithZeroParameter() {
        String expectedMetrics = "# HELP all_metrics\nall_metrics 0.0\n";
        when(nonDirectedTools.scrapeGlobalMetrics(0.0)).thenReturn(expectedMetrics);

        given().queryParam("minTargetScore", 0.0)
                .when()
                .get("/api/metrics")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(equalTo(expectedMetrics));

        verify(nonDirectedTools).scrapeGlobalMetrics(0.0);
    }

    @Test
    void testGetMetricsReturnsEmptyString() {
        when(nonDirectedTools.scrapeGlobalMetrics(anyDouble())).thenReturn("");

        given().when()
                .get("/api/metrics")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(equalTo(""));
    }

    @Test
    void testGetMetricsWithNegativeParameter() {
        String expectedMetrics = "# HELP negative_test\nnegative_test -5.0\n";
        when(nonDirectedTools.scrapeGlobalMetrics(-5.0)).thenReturn(expectedMetrics);

        given().queryParam("minTargetScore", -5.0)
                .when()
                .get("/api/metrics")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(equalTo(expectedMetrics));

        verify(nonDirectedTools).scrapeGlobalMetrics(-5.0);
    }

    @Test
    void testGetMetricsWithLargeParameter() {
        String expectedMetrics = "";
        when(nonDirectedTools.scrapeGlobalMetrics(100.0)).thenReturn(expectedMetrics);

        given().queryParam("minTargetScore", 100.0)
                .when()
                .get("/api/metrics")
                .then()
                .statusCode(200)
                .contentType("text/plain")
                .body(equalTo(expectedMetrics));

        verify(nonDirectedTools).scrapeGlobalMetrics(100.0);
    }
}
