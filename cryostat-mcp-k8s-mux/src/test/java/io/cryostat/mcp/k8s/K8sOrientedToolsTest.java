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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.k8s.PodNameResolver.TargetInfo;
import io.cryostat.mcp.model.ArchivedRecordingDescriptor;
import io.cryostat.mcp.model.KeyValue;
import io.cryostat.mcp.model.Metadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class K8sOrientedToolsTest {

    @Mock private CryostatMCPInstanceManager instanceManager;
    @Mock private PodNameResolver podNameResolver;
    @Mock private ArchivedRecordingSynthesizer synthesizer;
    @Mock private CryostatMCP mcp;

    private K8sOrientedTools tools;

    private static final String NAMESPACE = "test-ns";
    private static final String POD_NAME = "my-pod";
    private static final String JVM_ID = "jvmid123";
    private static final TargetInfo TARGET = new TargetInfo(POD_NAME, 1234, JVM_ID);

    @BeforeEach
    void setUp() {
        tools = new K8sOrientedTools();
        tools.instanceManager = instanceManager;
        tools.podNameResolver = podNameResolver;
        tools.synthesizer = synthesizer;

        lenient().when(instanceManager.createInstance(NAMESPACE)).thenReturn(mcp);
        lenient().when(podNameResolver.resolveTarget(NAMESPACE, POD_NAME)).thenReturn(TARGET);
    }

    @Test
    void testGetAnalysisReport_singleRecording() throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));

        ArchivedRecordingDescriptor recording = recording("rec1.jfr");
        String expectedReport = "{\"score\":42}";

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to)).thenReturn(recording);
        when(mcp.getArchivedReport(JVM_ID, "rec1.jfr")).thenReturn(expectedReport);

        String result = tools.getAnalysisReport(NAMESPACE, POD_NAME, fromTs, toTs);

        assertEquals(expectedReport, result);
        verify(synthesizer).synthesize(NAMESPACE, TARGET, from, to);
        verify(mcp).getArchivedReport(JVM_ID, "rec1.jfr");
    }

    @Test
    void testGetAnalysisReport_propagatesUnsatisfiableRangeException()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to))
                .thenThrow(new UnsatisfiableRangeException(JVM_ID, from, to));

        assertThrows(
                UnsatisfiableRangeException.class,
                () -> tools.getAnalysisReport(NAMESPACE, POD_NAME, fromTs, toTs));

        verify(mcp, never()).getArchivedReport(any(), any());
    }

    @Test
    void testGetAnalysisReport_propagatesIOExceptionFromReport()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));

        ArchivedRecordingDescriptor recording = recording("rec1.jfr");

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to)).thenReturn(recording);
        when(mcp.getArchivedReport(JVM_ID, "rec1.jfr"))
                .thenThrow(new IOException("connection refused"));

        assertThrows(
                IOException.class,
                () -> tools.getAnalysisReport(NAMESPACE, POD_NAME, fromTs, toTs));
    }

    @Test
    void testGetAnalysisReport_invalidTimestampThrowsDateTimeParseException() {
        assertThrows(
                Exception.class,
                () -> tools.getAnalysisReport(NAMESPACE, POD_NAME, "not-a-date", "also-not"));
    }

    @Test
    void testListEventTypesInRecordings_delegatesToMcp()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));

        ArchivedRecordingDescriptor recording = recording("rec1.jfr");
        List<List<String>> expected =
                List.of(List.of("jdk.GarbageCollection"), List.of("jdk.CPULoad"));

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to)).thenReturn(recording);
        when(mcp.listArchivedRecordingEventTypes(JVM_ID, "rec1.jfr")).thenReturn(expected);

        List<List<String>> result = tools.listJfrEvents(NAMESPACE, POD_NAME, fromTs, toTs);

        assertEquals(expected, result);
        verify(synthesizer).synthesize(NAMESPACE, TARGET, from, to);
        verify(mcp).listArchivedRecordingEventTypes(JVM_ID, "rec1.jfr");
    }

    @Test
    void testListEventTypesInRecordings_propagatesUnsatisfiableRangeException()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to))
                .thenThrow(new UnsatisfiableRangeException(JVM_ID, from, to));

        assertThrows(
                UnsatisfiableRangeException.class,
                () -> tools.listJfrEvents(NAMESPACE, POD_NAME, fromTs, toTs));

        verify(mcp, never()).listArchivedRecordingEventTypes(any(), any());
    }

    @Test
    void testListEventFieldsInRecordings_delegatesToMcp()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));
        String eventType = "jdk.GarbageCollection";

        ArchivedRecordingDescriptor recording = recording("rec1.jfr");
        List<List<String>> expected = List.of(List.of("startTime"), List.of("gcId"));

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to)).thenReturn(recording);
        when(mcp.listArchivedRecordingEventFields(JVM_ID, "rec1.jfr", eventType))
                .thenReturn(expected);

        List<List<String>> result =
                tools.getJfrSchema(NAMESPACE, POD_NAME, fromTs, toTs, eventType);

        assertEquals(expected, result);
        verify(synthesizer).synthesize(NAMESPACE, TARGET, from, to);
        verify(mcp).listArchivedRecordingEventFields(JVM_ID, "rec1.jfr", eventType);
    }

    @Test
    void testListEventFieldsInRecordings_propagatesUnsatisfiableRangeException()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to))
                .thenThrow(new UnsatisfiableRangeException(JVM_ID, from, to));

        assertThrows(
                UnsatisfiableRangeException.class,
                () ->
                        tools.getJfrSchema(
                                NAMESPACE, POD_NAME, fromTs, toTs, "jdk.GarbageCollection"));

        verify(mcp, never()).listArchivedRecordingEventFields(any(), any(), any());
    }

    @Test
    void testListEventsInRecordings_delegatesToMcp()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));
        String eventType = "jdk.GarbageCollection";
        List<String> columns = List.of("startTime", "gcId");
        int limit = 50;

        ArchivedRecordingDescriptor recording = recording("rec1.jfr");
        List<List<String>> expected =
                List.of(List.of("2024-01-01T00:00:01Z", "1"), List.of("2024-01-01T00:00:02Z", "2"));

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to)).thenReturn(recording);
        when(mcp.listArchivedRecordingEvents(JVM_ID, "rec1.jfr", eventType, columns, limit))
                .thenReturn(expected);

        List<List<String>> result =
                tools.getJfrEvents(NAMESPACE, POD_NAME, fromTs, toTs, eventType, columns, limit);

        assertEquals(expected, result);
        verify(synthesizer).synthesize(NAMESPACE, TARGET, from, to);
        verify(mcp).listArchivedRecordingEvents(JVM_ID, "rec1.jfr", eventType, columns, limit);
    }

    @Test
    void testListEventsInRecordings_nullColumns_delegatesToMcp()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));
        String eventType = "jdk.GarbageCollection";

        ArchivedRecordingDescriptor recording = recording("rec1.jfr");
        List<List<String>> expected = List.of(List.of("2024-01-01T00:00:01Z", "1", "Young"));

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to)).thenReturn(recording);
        when(mcp.listArchivedRecordingEvents(JVM_ID, "rec1.jfr", eventType, null, 100))
                .thenReturn(expected);

        List<List<String>> result =
                tools.getJfrEvents(NAMESPACE, POD_NAME, fromTs, toTs, eventType, null, 100);

        assertEquals(expected, result);
        verify(mcp).listArchivedRecordingEvents(JVM_ID, "rec1.jfr", eventType, null, 100);
    }

    @Test
    void testListEventsInRecordings_propagatesUnsatisfiableRangeException()
            throws IOException, UnsatisfiableRangeException {
        String fromTs = "2024-01-01T00:00:00Z";
        String toTs = "2024-01-01T01:00:00Z";
        Date from = Date.from(Instant.parse(fromTs));
        Date to = Date.from(Instant.parse(toTs));

        when(synthesizer.synthesize(NAMESPACE, TARGET, from, to))
                .thenThrow(new UnsatisfiableRangeException(JVM_ID, from, to));

        assertThrows(
                UnsatisfiableRangeException.class,
                () ->
                        tools.getJfrEvents(
                                NAMESPACE,
                                POD_NAME,
                                fromTs,
                                toTs,
                                "jdk.GarbageCollection",
                                null,
                                100));

        verify(mcp, never()).listArchivedRecordingEvents(any(), any(), any(), any(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "listEventTypesInRecordings",
                "listEventFieldsInRecordings",
                "listEventsInRecordings"
            })
    void testInvalidTimestampThrowsException(String methodName) {
        assertThrows(
                Exception.class,
                () -> {
                    switch (methodName) {
                        case "listEventTypesInRecordings" ->
                                tools.listJfrEvents(NAMESPACE, POD_NAME, "not-a-date", "also-not");
                        case "listEventFieldsInRecordings" ->
                                tools.getJfrSchema(
                                        NAMESPACE,
                                        POD_NAME,
                                        "not-a-date",
                                        "also-not",
                                        "jdk.GarbageCollection");
                        case "listEventsInRecordings" ->
                                tools.getJfrEvents(
                                        NAMESPACE,
                                        POD_NAME,
                                        "not-a-date",
                                        "also-not",
                                        "jdk.GarbageCollection",
                                        null,
                                        100);
                    }
                });
    }

    private static ArchivedRecordingDescriptor recording(String name) {
        List<KeyValue> labels =
                List.of(
                        new KeyValue("startTime", "1000"),
                        new KeyValue("duration", "3600000"),
                        new KeyValue("jvmId", JVM_ID));
        return new ArchivedRecordingDescriptor(
                JVM_ID,
                name,
                "/api/v4/download/" + name,
                "/api/v4/reports/" + name,
                new Metadata(labels),
                4096L,
                4601000L);
    }
}
