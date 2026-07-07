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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.model.ArchivedRecordingDescriptor;
import io.cryostat.mcp.model.ArchivedRecordingDirectory;
import io.cryostat.mcp.model.KeyValue;
import io.cryostat.mcp.model.Metadata;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArchivedRecordingSynthesizerTest {

    @Mock private CryostatMCPInstanceManager instanceManager;

    @Mock private CryostatMCP mcp;

    @Mock private Logger log;

    @Mock private OutputStream outputStream;

    private ArchivedRecordingSynthesizer synthesizer;

    private static final String NAMESPACE = "test-ns";
    private static final String JVM_ID = "jvmid123";

    @BeforeEach
    void setUp() {
        synthesizer = new ArchivedRecordingSynthesizer();
        synthesizer.instanceManager = instanceManager;
        synthesizer.log = log;
        synthesizer.tempDir = Path.of("/tmp/cryostat-mcp-synthetic-test");
        when(instanceManager.createInstance(NAMESPACE)).thenReturn(mcp);
    }

    @Test
    void testThrowsWhenNoRecordingsExist() {
        when(mcp.listTargetArchivedRecordings(JVM_ID)).thenReturn(List.of());

        assertThrows(
                UnsatisfiableRangeException.class,
                () -> synthesizer.synthesize(NAMESPACE, JVM_ID, new Date(1000L), new Date(2000L)));
    }

    @Test
    void testThrowsWhenRecordingsExistButNoneIntersectRange() {
        ArchivedRecordingDescriptor rec = recording("rec1.jfr", 5000L, 1000L);
        when(mcp.listTargetArchivedRecordings(JVM_ID)).thenReturn(List.of(dir(JVM_ID, rec)));

        assertThrows(
                UnsatisfiableRangeException.class,
                () -> synthesizer.synthesize(NAMESPACE, JVM_ID, new Date(1000L), new Date(2000L)));
    }

    @Test
    void testReturnsSingleRecordingWhenExactlyOneIntersects()
            throws UnsatisfiableRangeException, IOException {
        ArchivedRecordingDescriptor rec = recording("rec1.jfr", 500L, 2000L);
        when(mcp.listTargetArchivedRecordings(JVM_ID)).thenReturn(List.of(dir(JVM_ID, rec)));

        ArchivedRecordingDescriptor result =
                synthesizer.synthesize(NAMESPACE, JVM_ID, new Date(1000L), new Date(2000L));

        assertSame(rec, result);
        verify(mcp, never()).uploadArchivedRecording(any(), any(), any(), any());
    }

    @Test
    void testReturnsSingleRecordingWhenMultipleExistButOnlyOneIntersects()
            throws UnsatisfiableRangeException, IOException {
        ArchivedRecordingDescriptor rec1 = recording("rec1.jfr", 500L, 2000L);
        ArchivedRecordingDescriptor rec2 = recording("rec2.jfr", 10000L, 1000L);
        when(mcp.listTargetArchivedRecordings(JVM_ID)).thenReturn(List.of(dir(JVM_ID, rec1, rec2)));

        ArchivedRecordingDescriptor result =
                synthesizer.synthesize(NAMESPACE, JVM_ID, new Date(1000L), new Date(2000L));

        assertSame(rec1, result);
        verify(mcp, never()).uploadArchivedRecording(any(), any(), any(), any());
    }

    @Test
    void testSynthesizesWhenTwoRecordingsIntersect()
            throws UnsatisfiableRangeException, IOException {
        long start1 = 500L;
        long dur1 = 1500L;
        long start2 = 1800L;
        long dur2 = 1000L;

        ArchivedRecordingDescriptor rec1 = recording("rec1.jfr", start1, dur1);
        ArchivedRecordingDescriptor rec2 = recording("rec2.jfr", start2, dur2);
        ArchivedRecordingDescriptor syntheticRec =
                recording("synthetic_jvmid12_500_2800.jfr", start1, start2 + dur2 - start1);

        when(mcp.listTargetArchivedRecordings(JVM_ID)).thenReturn(List.of(dir(JVM_ID, rec1, rec2)));

        InputStream stream1 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        InputStream stream2 = new ByteArrayInputStream(new byte[] {4, 5, 6});
        when(mcp.downloadArchivedRecording(JVM_ID, "rec1.jfr")).thenReturn(stream1);
        when(mcp.downloadArchivedRecording(JVM_ID, "rec2.jfr")).thenReturn(stream2);

        when(mcp.uploadArchivedRecording(eq(JVM_ID), anyString(), any(File.class), anyMap()))
                .thenReturn(syntheticRec);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            Path tempFile = synthesizer.tempDir.resolve("synthetic_jvmid123_500_2800.jfr");
            files.when(() -> Files.newOutputStream(tempFile)).thenReturn(outputStream);
            files.when(() -> Files.deleteIfExists(tempFile)).thenReturn(true);

            ArchivedRecordingDescriptor result =
                    synthesizer.synthesize(NAMESPACE, JVM_ID, new Date(1000L), new Date(2500L));

            assertSame(syntheticRec, result);
        }

        verify(mcp).downloadArchivedRecording(JVM_ID, "rec1.jfr");
        verify(mcp).downloadArchivedRecording(JVM_ID, "rec2.jfr");

        verify(mcp)
                .uploadArchivedRecording(
                        eq(JVM_ID),
                        contains("synthetic_"),
                        any(File.class),
                        argThat(
                                labels ->
                                        "true".equals(labels.get("synthetic"))
                                                && String.valueOf(start1)
                                                        .equals(labels.get("startTime"))
                                                && String.valueOf(start2 + dur2 - start1)
                                                        .equals(labels.get("duration"))
                                                && String.valueOf(start2 + dur2)
                                                        .equals(labels.get("endTime"))));
    }

    @Test
    void testSyntheticStartTimeIsEarliestEvenIfBeforeFromTimestamp()
            throws UnsatisfiableRangeException, IOException {
        long start1 = 500L;
        long dur1 = 2000L;
        long start2 = 1200L;
        long dur2 = 1000L;

        ArchivedRecordingDescriptor rec1 = recording("rec1.jfr", start1, dur1);
        ArchivedRecordingDescriptor rec2 = recording("rec2.jfr", start2, dur2);
        ArchivedRecordingDescriptor syntheticRec =
                recording("synthetic_jvmid12_500_2500.jfr", start1, start2 + dur2 - start1);

        when(mcp.listTargetArchivedRecordings(JVM_ID)).thenReturn(List.of(dir(JVM_ID, rec1, rec2)));

        when(mcp.downloadArchivedRecording(JVM_ID, "rec1.jfr"))
                .thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mcp.downloadArchivedRecording(JVM_ID, "rec2.jfr"))
                .thenReturn(new ByteArrayInputStream(new byte[0]));

        when(mcp.uploadArchivedRecording(eq(JVM_ID), anyString(), any(File.class), anyMap()))
                .thenReturn(syntheticRec);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            Path tempFile = synthesizer.tempDir.resolve("synthetic_jvmid123_500_2500.jfr");
            files.when(() -> Files.newOutputStream(tempFile)).thenReturn(outputStream);
            files.when(() -> Files.deleteIfExists(tempFile)).thenReturn(true);

            ArchivedRecordingDescriptor result =
                    synthesizer.synthesize(NAMESPACE, JVM_ID, new Date(1000L), new Date(2500L));

            assertSame(syntheticRec, result);
        }

        verify(mcp)
                .uploadArchivedRecording(
                        eq(JVM_ID),
                        anyString(),
                        any(File.class),
                        argThat(labels -> String.valueOf(start1).equals(labels.get("startTime"))));
    }

    private static ArchivedRecordingDescriptor recording(
            String name, long startTimeMs, long durationMs) {
        List<KeyValue> labels =
                List.of(
                        new KeyValue("startTime", String.valueOf(startTimeMs)),
                        new KeyValue("duration", String.valueOf(durationMs)),
                        new KeyValue("jvmId", JVM_ID));
        return new ArchivedRecordingDescriptor(
                JVM_ID,
                name,
                "/api/v4/download/encodedkey-" + name,
                "/api/v4/reports/" + name,
                new Metadata(labels),
                1024L,
                startTimeMs + durationMs);
    }

    private static ArchivedRecordingDirectory dir(
            String jvmId, ArchivedRecordingDescriptor... recs) {
        return new ArchivedRecordingDirectory("http://target", jvmId, List.of(recs));
    }
}
