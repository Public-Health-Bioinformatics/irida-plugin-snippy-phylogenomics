package org.publichealthbioinformatics.irida.plugin.snippyphylogenomics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import ca.corefacility.bioinformatics.irida.exceptions.IridaWorkflowNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.PostProcessingException;
import ca.corefacility.bioinformatics.irida.model.sample.MetadataTemplateField;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.MetadataEntry;
import ca.corefacility.bioinformatics.irida.model.workflow.IridaWorkflow;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.type.AnalysisType;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.AnalysisSubmission;
import ca.corefacility.bioinformatics.irida.pipeline.results.updater.AnalysisSampleUpdater;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;

/**
 * This implements a class used to perform post-processing on the analysis
 * pipeline results to extract information to write into the IRIDA metadata
 * tables. Please see
 * <https://github.com/phac-nml/irida/blob/development/src/main/java/ca/corefacility/bioinformatics/irida/pipeline/results/AnalysisSampleUpdater.java>
 * or the README.md file in this project for more details.
 */
public class SnippyPhylogenomicsPluginUpdater implements AnalysisSampleUpdater {

	private final MetadataTemplateService metadataTemplateService;
	private final SampleService sampleService;
	private final IridaWorkflowsService iridaWorkflowsService;

	/**
	 * Builds a new {@link SnippyPhylogenomicsPluginUpdater} with the given services.
	 * 
	 * @param metadataTemplateService The metadata template service.
	 * @param sampleService           The sample service.
	 * @param iridaWorkflowsService   The irida workflows service.
	 */
	public SnippyPhylogenomicsPluginUpdater(MetadataTemplateService metadataTemplateService, SampleService sampleService,
									 IridaWorkflowsService iridaWorkflowsService) {
		this.metadataTemplateService = metadataTemplateService;
		this.sampleService = sampleService;
		this.iridaWorkflowsService = iridaWorkflowsService;
	}

	/**
	 * Code to perform the actual update of the {@link Sample}s passed in the
	 * collection.
	 * 
	 * @param samples  A collection of {@link Sample}s that were passed to this
	 *                 pipeline.
	 * @param analysisSubmission The {@link AnalysisSubmission} object corresponding to this
	 *                 analysis pipeline.
	 */
	@Override
	public void update(Collection<Sample> samples, AnalysisSubmission analysisSubmission) throws PostProcessingException {
		if (samples == null) {
			throw new IllegalArgumentException("samples is null");
		} else if (analysisSubmission == null) {
			throw new IllegalArgumentException("analysisSubmission is null");
		} else if (samples.size() != 1) {
			// In this particular pipeline, only one sample should be run at a time so I
			// verify that the collection of samples I get has only 1 sample
			throw new IllegalArgumentException(
					"samples size=" + samples.size() + " is not 1 for analysisSubmission=" + analysisSubmission.getId());
		}

		// extract the 1 and only sample (if more than 1, would have thrown an exception
		// above)
		final Sample sample = samples.iterator().next();


		try {
			Map<String, MetadataEntry> metadataEntries = new HashMap<>();

			// get information about the workflow (e.g., version and name)
			IridaWorkflow iridaWorkflow = iridaWorkflowsService.getIridaWorkflow(analysisSubmission.getWorkflowId());
			String workflowVersion = iridaWorkflow.getWorkflowDescription().getVersion();
			String workflowName = iridaWorkflow.getWorkflowDescription().getName();

			Map<MetadataTemplateField, MetadataEntry> metadataMap = metadataTemplateService
					.getMetadataMap(metadataEntries);

			// merges with existing sample metadata
			sample.mergeMetadata(metadataMap);

			// does an update of the sample metadata
			sampleService.updateFields(sample.getId(), ImmutableMap.of("metadata", sample.getMetadata()));
		} catch (IridaWorkflowNotFoundException e) {
			throw new PostProcessingException("Could not find workflow for id=" + analysisSubmission.getWorkflowId(), e);
		}
	}

	/**
	 * Parses out values from the hash file into a {@link List <Map>} linking 'gene_name' to 'detection_status'.
	 *
	 * @param snpsSummaryFilePath The {@link Path} to the file containing the SNP summary data
	 *
	 * <pre>
	 * DateTime	2019-12-17T23:31:01
	 * ReadFiles	/export/galaxy-central/database/files/000/dataset_204.dat /export/galaxy-central/database/files/000/dataset_205.dat
	 * Reference	/export/galaxy-central/database/files/000/dataset_203.dat
	 * ReferenceSize	5333942
	 * Software	snippy 4.4.5
	 * Variant-COMPLEX	827
	 * Variant-DEL	113
	 * Variant-INS	181
	 * Variant-MNP	85
	 * Variant-SNP	6834
	 * VariantTotal	8040
	 * </pre>
	 *
	 * @return An {@link Map<String, String>} linking several varaint summary stats.
	 * @throws IOException             If there was an error reading the file.
	 * @throws PostProcessingException If there was an error parsing the file.
	 */
	@VisibleForTesting
	Map<String, String> parseSnpsSummaryFile(Path snpsSummaryFilePath) throws IOException, PostProcessingException {
		Map<String, String> snpsSummary = new HashMap<>();

		BufferedReader snpsSummaryReader = new BufferedReader(new FileReader(snpsSummaryFilePath.toFile()));

		for (String line = snpsSummaryReader.readLine(); line != null; line = snpsSummaryReader.readLine()) {
			String[] record = line.split("\t");
			String name = record[0].toLowerCase();
			String value = record[1];
			snpsSummary.put(name, value);
		}

		snpsSummaryReader.close();

		return snpsSummary;
	}

	/**
	 * The {@link AnalysisType} this {@link AnalysisSampleUpdater} corresponds to.
	 * 
	 * @return The {@link AnalysisType} this {@link AnalysisSampleUpdater}
	 *         corresponds to.
	 */
	@Override
	public AnalysisType getAnalysisType() {
		return SnippyPhylogenomicsPlugin.SNIPPY_PHYLOGENOMICS;
	}
}
