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
package io.cryostat.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import io.cryostat.mcp.model.ActiveRecordingFilter;
import io.cryostat.mcp.model.ArchivedRecordingDescriptor;
import io.cryostat.mcp.model.ArchivedRecordingDirectory;
import io.cryostat.mcp.model.DiscoveryNode;
import io.cryostat.mcp.model.DiscoveryNodeFilter;
import io.cryostat.mcp.model.EventTemplate;
import io.cryostat.mcp.model.Health;
import io.cryostat.mcp.model.RecordingDescriptor;
import io.cryostat.mcp.model.Target;
import io.cryostat.mcp.model.graphql.ActiveRecordingNode;
import io.cryostat.mcp.model.graphql.ActiveRecordingsWithStop;
import io.cryostat.mcp.model.graphql.StoppedRecording;
import io.cryostat.mcp.model.graphql.TargetNodeForStop;
import io.cryostat.mcp.model.graphql.TargetWithStop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryostatMCPTest {

    @Mock private CryostatRESTClient restClient;

    @Mock private CryostatGraphQLClient graphqlClient;

    @Mock private ObjectMapper objectMapper;

    private CryostatMCP cryostatMCP;

    @BeforeEach
    void setUp() {
        cryostatMCP = new CryostatMCP(restClient, graphqlClient, objectMapper);
    }

    @Test
    void testConstructorWithParameters() {
        assertNotNull(cryostatMCP);
        Health mockHealth = mock(Health.class);
        when(restClient.health()).thenReturn(mockHealth);

        Health result = cryostatMCP.getHealth();

        assertSame(mockHealth, result);
        verify(restClient).health();
    }

    @Test
    void testGetHealth() {
        Health mockHealth = mock(Health.class);
        when(restClient.health()).thenReturn(mockHealth);

        Health result = cryostatMCP.getHealth();

        assertSame(mockHealth, result);
        verify(restClient).health();
    }

    @Test
    void testGetDiscoveryTree() {
        DiscoveryNode mockNode = mock(DiscoveryNode.class);
        when(restClient.getDiscoveryTree(true)).thenReturn(mockNode);

        DiscoveryNode result = cryostatMCP.getDiscoveryTree(true);

        assertSame(mockNode, result);
        verify(restClient).getDiscoveryTree(true);
    }

    @Test
    void testGetDiscoveryTreeWithoutMerge() {
        DiscoveryNode mockNode = mock(DiscoveryNode.class);
        when(restClient.getDiscoveryTree(false)).thenReturn(mockNode);

        DiscoveryNode result = cryostatMCP.getDiscoveryTree(false);

        assertSame(mockNode, result);
        verify(restClient).getDiscoveryTree(false);
    }

    @Test
    void testListTargetsWithNoFilters() {
        List<io.cryostat.mcp.model.graphql.DiscoveryNode> mockNodes =
                Arrays.asList(
                        mock(io.cryostat.mcp.model.graphql.DiscoveryNode.class),
                        mock(io.cryostat.mcp.model.graphql.DiscoveryNode.class));
        when(graphqlClient.targetNodes(null, null)).thenReturn(mockNodes);

        List<io.cryostat.mcp.model.graphql.DiscoveryNode> result =
                cryostatMCP.listTargets(null, null, null, null, null, null);

        assertEquals(mockNodes, result);
        verify(graphqlClient).targetNodes(null, null);
    }

    @Test
    void testListTargetsWithFilters() {
        List<Long> ids = Arrays.asList(1L, 2L);
        List<Long> targetIds = Arrays.asList(10L, 20L);
        List<String> names = Arrays.asList("pod1", "pod2");
        List<String> labels = Arrays.asList("env=prod", "app=test");

        List<io.cryostat.mcp.model.graphql.DiscoveryNode> mockNodes =
                Collections.singletonList(mock(io.cryostat.mcp.model.graphql.DiscoveryNode.class));

        when(graphqlClient.targetNodes(any(DiscoveryNodeFilter.class), eq(null)))
                .thenReturn(mockNodes);

        List<io.cryostat.mcp.model.graphql.DiscoveryNode> result =
                cryostatMCP.listTargets(ids, targetIds, names, labels, null, null);

        assertEquals(mockNodes, result);
        verify(graphqlClient).targetNodes(any(DiscoveryNodeFilter.class), eq(null));
    }

    @Test
    void testListTargetsWithAuditLog() {
        List<io.cryostat.mcp.model.graphql.DiscoveryNode> mockNodes =
                Collections.singletonList(mock(io.cryostat.mcp.model.graphql.DiscoveryNode.class));

        when(graphqlClient.targetNodes(null, true)).thenReturn(mockNodes);

        List<io.cryostat.mcp.model.graphql.DiscoveryNode> result =
                cryostatMCP.listTargets(null, null, null, null, null, true);

        assertEquals(mockNodes, result);
        verify(graphqlClient).targetNodes(null, true);
    }

    @Test
    void testIsPresentWithNullCollection() {
        assertFalse(CryostatMCP.isPresent(null));
    }

    @Test
    void testIsPresentWithEmptyCollection() {
        assertFalse(CryostatMCP.isPresent(Collections.emptyList()));
    }

    @Test
    void testIsPresentWithNonEmptyCollection() {
        assertTrue(CryostatMCP.isPresent(Arrays.asList("item1", "item2")));
    }

    @Test
    void testGetAuditTarget() {
        String jvmId = "test-jvm-id";
        Target mockTarget = mock(Target.class);
        when(restClient.auditTarget(jvmId)).thenReturn(mockTarget);

        Target result = cryostatMCP.getAuditTarget(jvmId);

        assertSame(mockTarget, result);
        verify(restClient).auditTarget(jvmId);
    }

    @Test
    void testGetAuditTargetLineage() {
        String jvmId = "test-jvm-id";
        DiscoveryNode mockNode = mock(DiscoveryNode.class);
        when(restClient.auditTargetLineage(jvmId)).thenReturn(mockNode);

        DiscoveryNode result = cryostatMCP.getAuditTargetLineage(jvmId);

        assertSame(mockNode, result);
        verify(restClient).auditTargetLineage(jvmId);
    }

    @Test
    void testListTargetEventTemplates() {
        long targetId = 123L;
        List<EventTemplate> mockTemplates =
                Arrays.asList(mock(EventTemplate.class), mock(EventTemplate.class));
        when(restClient.targetEventTemplates(targetId)).thenReturn(mockTemplates);

        List<EventTemplate> result = cryostatMCP.listTargetEventTemplates(targetId);

        assertEquals(mockTemplates, result);
        verify(restClient).targetEventTemplates(targetId);
    }

    @Test
    void testGetTargetEventTemplate() {
        long targetId = 123L;
        String templateType = "TARGET";
        String templateName = "Profiling";
        String mockXml = "<jfr>...</jfr>";
        when(restClient.targetEventTemplate(targetId, templateType, templateName))
                .thenReturn(mockXml);

        String result = cryostatMCP.getTargetEventTemplate(targetId, templateType, templateName);

        assertEquals(mockXml, result);
        verify(restClient).targetEventTemplate(targetId, templateType, templateName);
    }

    @Test
    void testListTargetActiveRecordings() {
        long targetId = 123L;
        List<RecordingDescriptor> mockRecordings =
                Arrays.asList(mock(RecordingDescriptor.class), mock(RecordingDescriptor.class));
        when(restClient.targetActiveRecordings(targetId)).thenReturn(mockRecordings);

        List<RecordingDescriptor> result = cryostatMCP.listTargetActiveRecordings(targetId);

        assertEquals(mockRecordings, result);
        verify(restClient).targetActiveRecordings(targetId);
    }

    @Test
    void testListTargetArchivedRecordings() {
        String jvmId = "test-jvm-id";
        List<ArchivedRecordingDirectory> mockArchives =
                Arrays.asList(
                        mock(ArchivedRecordingDirectory.class),
                        mock(ArchivedRecordingDirectory.class));
        when(restClient.targetArchivedRecordings(jvmId)).thenReturn(mockArchives);

        List<ArchivedRecordingDirectory> result = cryostatMCP.listTargetArchivedRecordings(jvmId);

        assertEquals(mockArchives, result);
        verify(restClient).targetArchivedRecordings(jvmId);
    }

    @Test
    void testArchiveTargetRecording() {
        long targetId = 123L;
        String jvmId = "test-jvm-id";
        String snapshotName = "snapshot";
        long remoteId = 42L;

        CryostatMCP spyMCP = spy(cryostatMCP);
        doNothing().when(spyMCP).sleep(anyLong());

        RecordingDescriptor snapshot =
                new RecordingDescriptor(
                        1L,
                        remoteId,
                        "STOPPED",
                        0L,
                        0L,
                        false,
                        false,
                        true,
                        0L,
                        0L,
                        snapshotName,
                        null,
                        null,
                        null);
        // Archived name includes encoded target alias prefix and timestamp suffix
        ArchivedRecordingDescriptor olderArchive =
                new ArchivedRecordingDescriptor(
                        jvmId,
                        "-deployments-quarkus-run-jar_snapshot_20260624T100000Z.jfr",
                        null,
                        null,
                        null,
                        0L,
                        1000L);
        ArchivedRecordingDescriptor newerArchive =
                new ArchivedRecordingDescriptor(
                        jvmId,
                        "-deployments-quarkus-run-jar_snapshot_20260625T162915Z.jfr",
                        null,
                        null,
                        null,
                        0L,
                        2000L);
        ArchivedRecordingDirectory dir =
                new ArchivedRecordingDirectory(null, jvmId, List.of(olderArchive, newerArchive));

        when(restClient.createSnapshot(targetId)).thenReturn(snapshot);
        when(restClient.patchRecording(targetId, remoteId, "save")).thenReturn("request-id");
        when(restClient.targetArchivedRecordings(jvmId)).thenReturn(List.of(dir));

        ArchivedRecordingDescriptor result = spyMCP.archiveTargetRecording(targetId, jvmId);

        assertSame(newerArchive, result);
        verify(restClient).createSnapshot(targetId);
        verify(restClient).patchRecording(targetId, remoteId, "save");
        verify(restClient).deleteRecording(targetId, remoteId);
        verify(spyMCP).sleep(CryostatMCP.ARCHIVE_INITIAL_DELAY_MS);
        verify(restClient).targetArchivedRecordings(jvmId);
    }

    @Test
    void testArchiveTargetRecordingNotFound() {
        long targetId = 123L;
        String jvmId = "test-jvm-id";
        String snapshotName = "snapshot";
        long remoteId = 42L;

        CryostatMCP spyMCP = spy(cryostatMCP);
        doNothing().when(spyMCP).sleep(anyLong());

        RecordingDescriptor snapshot =
                new RecordingDescriptor(
                        1L,
                        remoteId,
                        "STOPPED",
                        0L,
                        0L,
                        false,
                        false,
                        true,
                        0L,
                        0L,
                        snapshotName,
                        null,
                        null,
                        null);
        ArchivedRecordingDirectory dir = new ArchivedRecordingDirectory(null, jvmId, List.of());

        when(restClient.createSnapshot(targetId)).thenReturn(snapshot);
        when(restClient.patchRecording(targetId, remoteId, "save")).thenReturn("request-id");
        when(restClient.targetArchivedRecordings(jvmId)).thenReturn(List.of(dir));

        assertThrows(
                NoSuchElementException.class, () -> spyMCP.archiveTargetRecording(targetId, jvmId));

        verify(spyMCP).sleep(CryostatMCP.ARCHIVE_INITIAL_DELAY_MS);
        verify(spyMCP, times(CryostatMCP.ARCHIVE_POLL_ATTEMPTS - 1))
                .sleep(CryostatMCP.ARCHIVE_RETRY_DELAY_MS);
        verify(restClient, times(CryostatMCP.ARCHIVE_POLL_ATTEMPTS))
                .targetArchivedRecordings(jvmId);
    }

    @Test
    void testStartTargetRecording() throws JsonProcessingException {
        long targetId = 123L;
        String recordingName = "test-recording";
        String templateName = "Profiling";
        String templateType = "TARGET";
        long duration = 60L;

        RecordingDescriptor mockDescriptor = mock(RecordingDescriptor.class);
        String expectedMetadata = "{\"labels\":{\"autoanalyze\":\"true\"}}";

        when(objectMapper.writeValueAsString(any())).thenReturn(expectedMetadata);
        when(restClient.startRecording(
                        eq(targetId),
                        eq(recordingName),
                        eq("template=Profiling,type=TARGET"),
                        eq(duration),
                        eq(true),
                        eq(expectedMetadata),
                        eq(true)))
                .thenReturn(mockDescriptor);

        RecordingDescriptor result =
                cryostatMCP.startTargetRecording(
                        targetId, recordingName, templateName, templateType, duration);

        assertSame(mockDescriptor, result);
        verify(objectMapper)
                .writeValueAsString(eq(Map.of("labels", Map.of("autoanalyze", "true"))));
        verify(restClient)
                .startRecording(
                        targetId,
                        recordingName,
                        "template=Profiling,type=TARGET",
                        duration,
                        true,
                        expectedMetadata,
                        true);
    }

    @Test
    void testStartTargetRecordingThrowsJsonProcessingException() throws JsonProcessingException {
        long targetId = 123L;
        String recordingName = "test-recording";
        String templateName = "Profiling";
        String templateType = "TARGET";
        long duration = 60L;

        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Test error") {});

        assertThrows(
                JsonProcessingException.class,
                () ->
                        cryostatMCP.startTargetRecording(
                                targetId, recordingName, templateName, templateType, duration));

        verify(objectMapper).writeValueAsString(any());
        verify(restClient, never())
                .startRecording(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyBoolean(),
                        anyString(),
                        anyBoolean());
    }

    @Test
    void testScrapeMetrics() {
        double minScore = 25.0;
        String mockMetrics = "# HELP metric_name\nmetric_name{label=\"value\"} 42.0";
        when(restClient.scrapeMetrics(minScore)).thenReturn(mockMetrics);

        String result = cryostatMCP.scrapeMetrics(minScore);

        assertEquals(mockMetrics, result);
        verify(restClient).scrapeMetrics(minScore);
    }

    @Test
    void testScrapeMetricsWithDefaultScore() {
        double defaultScore = -1.0;
        String mockMetrics = "# HELP metric_name\nmetric_name{label=\"value\"} 42.0";
        when(restClient.scrapeMetrics(defaultScore)).thenReturn(mockMetrics);

        String result = cryostatMCP.scrapeMetrics(defaultScore);

        assertEquals(mockMetrics, result);
        verify(restClient).scrapeMetrics(defaultScore);
    }

    @Test
    void testScrapeTargetMetrics() {
        String jvmId = "test-jvm-id";
        String mockMetrics = "# HELP metric_name\nmetric_name{label=\"value\"} 42.0";
        when(restClient.scrapeTargetMetrics(jvmId)).thenReturn(mockMetrics);

        String result = cryostatMCP.scrapeTargetMetrics(jvmId);

        assertEquals(mockMetrics, result);
        verify(restClient).scrapeTargetMetrics(jvmId);
    }

    @Test
    void testGetTargetReport() {
        long targetId = 123L;
        Object mockReport = Map.of("score", 75.0, "evaluation", "MEDIUM");
        when(restClient.getTargetReport(targetId)).thenReturn(mockReport);

        Object result = cryostatMCP.getTargetReport(targetId);

        assertSame(mockReport, result);
        verify(restClient).getTargetReport(targetId);
    }

    @Test
    void testExecuteQuery() {
        String jvmId = "test-jvm-id";
        String filename = "recording.jfr";
        String query = "SELECT * FROM jfr.\"jdk.ObjectAllocationSample\" LIMIT 10";
        List<List<String>> mockResults =
                Arrays.asList(Arrays.asList("col1", "col2"), Arrays.asList("val1", "val2"));
        when(restClient.executeQuery(jvmId, filename, query)).thenReturn(mockResults);

        List<List<String>> result = cryostatMCP.executeQuery(jvmId, filename, query);

        assertEquals(mockResults, result);
        verify(restClient).executeQuery(jvmId, filename, query);
    }

    @Test
    void testListArchivedRecordingEventTypes() {
        String jvmId = "test-jvm-id";
        String filename = "recording.jfr";
        List<List<String>> mockResults =
                Arrays.asList(Arrays.asList("name"), Arrays.asList("jdk.ObjectAllocationSample"));
        when(restClient.executeQuery(jvmId, filename, "tables")).thenReturn(mockResults);

        List<List<String>> result = cryostatMCP.listArchivedRecordingEventTypes(jvmId, filename);

        assertEquals(mockResults, result);
        verify(restClient).executeQuery(jvmId, filename, "tables");
    }

    @Test
    void testListArchivedRecordingEventFields() {
        String jvmId = "test-jvm-id";
        String filename = "recording.jfr";
        String eventType = "jdk.ObjectAllocationSample";
        List<List<String>> mockResults =
                Arrays.asList(Arrays.asList("name"), Arrays.asList("startTime"));
        when(restClient.executeQuery(jvmId, filename, "columns jdk.ObjectAllocationSample"))
                .thenReturn(mockResults);

        List<List<String>> result =
                cryostatMCP.listArchivedRecordingEventFields(jvmId, filename, eventType);

        assertEquals(mockResults, result);
        verify(restClient).executeQuery(jvmId, filename, "columns jdk.ObjectAllocationSample");
    }

    @Test
    void testListArchivedRecordingEvents() {
        String jvmId = "test-jvm-id";
        String filename = "recording.jfr";
        String eventType = "jdk.ObjectAllocationSample";
        List<List<String>> mockResults =
                Arrays.asList(Arrays.asList("startTime", "weight"), Arrays.asList("1", "512"));
        when(restClient.executeQuery(
                        jvmId,
                        filename,
                        "SELECT * FROM jfr.\"jdk.ObjectAllocationSample\" LIMIT 25"))
                .thenReturn(mockResults);

        List<List<String>> result =
                cryostatMCP.listArchivedRecordingEvents(jvmId, filename, eventType, null, 25);

        assertEquals(mockResults, result);
        verify(restClient)
                .executeQuery(
                        jvmId,
                        filename,
                        "SELECT * FROM jfr.\"jdk.ObjectAllocationSample\" LIMIT 25");
    }

    @Test
    void testListArchivedRecordingEventsSelectsColumns() {
        String jvmId = "test-jvm-id";
        String filename = "recording.jfr";
        String eventType = "jdk.ObjectAllocationSample";
        List<String> columns = List.of("startTime", "objectClass", "weight");
        List<List<String>> mockResults =
                Arrays.asList(Arrays.asList("startTime", "objectClass", "weight"));
        when(restClient.executeQuery(
                        jvmId,
                        filename,
                        "SELECT \"startTime\", \"objectClass\", \"weight\" FROM"
                                + " jfr.\"jdk.ObjectAllocationSample\" LIMIT 1"))
                .thenReturn(mockResults);

        List<List<String>> result =
                cryostatMCP.listArchivedRecordingEvents(jvmId, filename, eventType, columns, 1);

        assertEquals(mockResults, result);
        verify(restClient)
                .executeQuery(
                        jvmId,
                        filename,
                        "SELECT \"startTime\", \"objectClass\", \"weight\" FROM"
                                + " jfr.\"jdk.ObjectAllocationSample\" LIMIT 1");
    }

    @Test
    void testListArchivedRecordingEventsEscapesIdentifiers() {
        String jvmId = "test-jvm-id";
        String filename = "recording.jfr";
        String eventType = "jdk.Event\"Type";
        List<String> columns = List.of("object\"Class");
        List<List<String>> mockResults = Collections.singletonList(Arrays.asList("startTime"));
        when(restClient.executeQuery(
                        jvmId,
                        filename,
                        "SELECT \"object\"\"Class\" FROM jfr.\"jdk.Event\"\"Type\" LIMIT 1"))
                .thenReturn(mockResults);

        List<List<String>> result =
                cryostatMCP.listArchivedRecordingEvents(jvmId, filename, eventType, columns, 1);

        assertEquals(mockResults, result);
        verify(restClient)
                .executeQuery(
                        jvmId,
                        filename,
                        "SELECT \"object\"\"Class\" FROM jfr.\"jdk.Event\"\"Type\" LIMIT 1");
    }

    @Test
    void testListArchivedRecordingEventsRejectsBlankColumn() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        cryostatMCP.listArchivedRecordingEvents(
                                "test-jvm-id",
                                "recording.jfr",
                                "jdk.ThreadStart",
                                List.of("startTime", " "),
                                1));

        verifyNoInteractions(restClient);
    }

    @Test
    void testListArchivedRecordingEventsRejectsNonPositiveLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        cryostatMCP.listArchivedRecordingEvents(
                                "test-jvm-id", "recording.jfr", "jdk.ThreadStart", List.of(), 0));

        verifyNoInteractions(restClient);
    }

    @Test
    void testGetQueryAdditionalFunctions() {
        List<CryostatMCP.QueryExample> result = cryostatMCP.getQueryAdditionalFunctions();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(4, result.size());

        // Verify first example
        CryostatMCP.QueryExample firstExample = result.get(0);
        assertTrue(firstExample.description().contains("class name"));
        assertTrue(firstExample.query().contains("CLASS_NAME"));
    }

    @Test
    void testGetQueryExamples() {
        List<CryostatMCP.QueryExample> result = cryostatMCP.getQueryExamples();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(8, result.size());

        // Verify first example
        CryostatMCP.QueryExample firstExample = result.get(0);
        assertEquals(
                "List the available JFR event types (tables) in a recording",
                firstExample.description());
        assertEquals("tables", firstExample.query());
    }

    @Test
    void testStopTargetRecording() {
        long targetId = 123L;
        String recordingName = "my-recording";

        StoppedRecording stoppedRecording =
                new StoppedRecording(
                        1L,
                        42L,
                        "STOPPED",
                        0L,
                        0L,
                        false,
                        false,
                        true,
                        0L,
                        0L,
                        recordingName,
                        null,
                        null);
        ActiveRecordingNode recordingNode = new ActiveRecordingNode(stoppedRecording);
        ActiveRecordingsWithStop activeRecordings =
                new ActiveRecordingsWithStop(List.of(recordingNode));
        TargetWithStop targetWithStop = new TargetWithStop(activeRecordings);
        TargetNodeForStop targetNode = new TargetNodeForStop(targetWithStop);

        DiscoveryNodeFilter expectedNodeFilter =
                DiscoveryNodeFilter.builder().targetIds(List.of(targetId)).build();
        ActiveRecordingFilter expectedRecordingFilter = new ActiveRecordingFilter(recordingName);

        when(graphqlClient.stopActiveRecording(expectedNodeFilter, expectedRecordingFilter))
                .thenReturn(List.of(targetNode));

        StoppedRecording result = cryostatMCP.stopTargetRecording(targetId, recordingName);

        assertSame(stoppedRecording, result);
        verify(graphqlClient).stopActiveRecording(expectedNodeFilter, expectedRecordingFilter);
        verifyNoInteractions(restClient);
    }

    @Test
    void testStopTargetRecordingNotFound() {
        long targetId = 123L;
        String recordingName = "missing-recording";

        DiscoveryNodeFilter expectedNodeFilter =
                DiscoveryNodeFilter.builder().targetIds(List.of(targetId)).build();
        ActiveRecordingFilter expectedRecordingFilter = new ActiveRecordingFilter(recordingName);

        when(graphqlClient.stopActiveRecording(expectedNodeFilter, expectedRecordingFilter))
                .thenReturn(List.of());

        assertThrows(
                NoSuchElementException.class,
                () -> cryostatMCP.stopTargetRecording(targetId, recordingName));

        verify(graphqlClient).stopActiveRecording(expectedNodeFilter, expectedRecordingFilter);
        verifyNoInteractions(restClient);
    }

    @Test
    void testQueryExampleRecord() {
        String description = "Test description";
        String query = "SELECT * FROM test";

        CryostatMCP.QueryExample example = new CryostatMCP.QueryExample(description, query);

        assertEquals(description, example.description());
        assertEquals(query, example.query());
    }
}
