package org.publichealthbioinformatics.irida.plugin.snippyphylogenomics;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.hamcrest.collection.IsMapContaining;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import ca.corefacility.bioinformatics.irida.exceptions.IridaWorkflowException;
import ca.corefacility.bioinformatics.irida.model.sample.MetadataTemplateField;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.MetadataEntry;
import ca.corefacility.bioinformatics.irida.model.sequenceFile.SingleEndSequenceFile;
import ca.corefacility.bioinformatics.irida.model.workflow.description.*;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.workflow.IridaWorkflow;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisOutputFile;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.AnalysisSubmission;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.Analysis;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;


public class SnippyPhylogenomicsPluginUpdaterTest {
    private String WORKFLOW_NAME = "snippy-phylogenomics";
    private String WORKFLOW_VERSION = "0.1.0";

    private SnippyPhylogenomicsPluginUpdater updater;

    private SampleService sampleService;
    private MetadataTemplateService metadataTemplateService;
    private IridaWorkflowsService iridaWorkflowsService;
    private IridaWorkflow iridaWorkflow;
    private IridaWorkflowDescription iridaWorkflowDescription;

    private UUID uuid = UUID.randomUUID();

    @Before
    public void setUp() throws IridaWorkflowException {
        sampleService = mock(SampleService.class);
        metadataTemplateService = mock(MetadataTemplateService.class);
        iridaWorkflowsService = mock(IridaWorkflowsService.class);
        iridaWorkflow = mock(IridaWorkflow.class);
        iridaWorkflowDescription = mock(IridaWorkflowDescription.class);

        updater = new SnippyPhylogenomicsPluginUpdater(metadataTemplateService, sampleService, iridaWorkflowsService);

        when(iridaWorkflowsService.getIridaWorkflow(uuid)).thenReturn(iridaWorkflow);
        when(iridaWorkflow.getWorkflowDescription()).thenReturn(iridaWorkflowDescription);
        when(iridaWorkflowDescription.getName()).thenReturn(WORKFLOW_NAME);
        when(iridaWorkflowDescription.getVersion()).thenReturn(WORKFLOW_VERSION);
    }

    @Test
    public void testUpdate() throws Throwable {
        ImmutableMap<String, String> expectedResults = ImmutableMap.<String, String>builder()
                // .put("snippy-single/snps", "none")
                .build();
        Path snpsSummaryFilePath = Paths.get(ClassLoader.getSystemResource("snps_summary.tsv").toURI());

        AnalysisOutputFile snpsSummaryFile = new AnalysisOutputFile(snpsSummaryFilePath, null, null, null);
        Analysis analysis = new Analysis(null, ImmutableMap.of("snps_summary", snpsSummaryFile), null, null);
        AnalysisSubmission submission = AnalysisSubmission.builder(uuid)
                .inputFiles(ImmutableSet.of(new SingleEndSequenceFile(null))).build();

        submission.setAnalysis(analysis);

        Sample sample = new Sample();
        sample.setId(0L);

        ImmutableMap<MetadataTemplateField, MetadataEntry> metadataMap = ImmutableMap
                .of(new MetadataTemplateField("", ""), new MetadataEntry("", ""));
        when(metadataTemplateService.getMetadataMap(any(Map.class))).thenReturn(metadataMap);

        updater.update(Lists.newArrayList(sample), submission);

        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);

        //this is the important bit.  Ensures the correct values got pulled from the file
        verify(metadataTemplateService).getMetadataMap(mapCaptor.capture());
        Map<String, MetadataEntry> metadata = mapCaptor.getValue();

        int found = 0;
        for (Map.Entry<String, MetadataEntry> e : metadata.entrySet()) {

            if (expectedResults.containsKey(e.getKey())) {
                String expected = expectedResults.get(e.getKey());

                MetadataEntry value = e.getValue();

                assertEquals("metadata values should match", expected, value.getValue());
                found++;
            }
        }
        assertEquals("should have found the same number of results", expectedResults.keySet().size(), found);

        // this bit just ensures the merged data got saved
        verify(sampleService).updateFields(eq(sample.getId()), mapCaptor.capture());
        Map<MetadataTemplateField, MetadataEntry> value = (Map<MetadataTemplateField, MetadataEntry>) mapCaptor
                .getValue().get("metadata");

        assertEquals(metadataMap.keySet().iterator().next(), value.keySet().iterator().next());
    }

    @Test
    public void testParseSnpsSummaryFile() throws Throwable {
        Path snpsSummaryFilePath = Paths.get(ClassLoader.getSystemResource("snps_summary.tsv").toURI());
        Map<String, String> teTyperSummary = updater.parseSnpsSummaryFile(snpsSummaryFilePath);
            assertThat(teTyperSummary, IsMapContaining.hasKey("variant-complex"));
            assertThat(teTyperSummary, IsMapContaining.hasKey("variant-del"));
            assertThat(teTyperSummary, IsMapContaining.hasKey("variant-ins"));
            assertThat(teTyperSummary, IsMapContaining.hasKey("variant-mnp"));
            assertThat(teTyperSummary, IsMapContaining.hasKey("variant-snp"));
            assertThat(teTyperSummary, IsMapContaining.hasKey("varianttotal"));
    }
}
