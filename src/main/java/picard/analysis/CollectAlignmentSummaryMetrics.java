/*
 * The MIT License
 *
 * Copyright (c) 2020 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package picard.analysis;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamPairUtil.PairOrientation;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.util.CollectionUtil;
import htsjdk.samtools.util.Histogram;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import org.broadinstitute.barclay.help.DocumentedFeature;
import picard.PicardException;
import picard.cmdline.StandardOptionDefinitions;
import picard.cmdline.argumentcollections.ReferenceArgumentCollection;
import picard.cmdline.programgroups.DiagnosticsAndQCProgramGroup;
import picard.util.RExecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A command line tool to read a BAM file and produce standard alignment metrics that would be applicable to any alignment.
 * Metrics to include, but not limited to:
 * <ul>
 * <li>Total number of reads (total, period, no exclusions)</li>
 * <li>Total number of PF reads (PF == does not fail vendor check flag)</li>
 * <li>Number of PF noise reads (does not fail vendor check and has noise attr set)</li>
 * <li>Total aligned PF reads (any PF read that has a sequence and position)</li>
 * <li>High quality aligned PF reads (high quality == mapping quality >= 20)</li>
 * <li>High quality aligned PF bases (actual aligned bases, calculate off alignment blocks)</li>
 * <li>High quality aligned PF Q20 bases (subset of above where base quality >= 20)</li>
 * <li>Median mismatches in HQ aligned PF reads (how many aligned bases != ref on average)</li>
 * <li>Reads aligned in pairs (vs. reads aligned with mate unaligned/not present)</li>
 * <li>Read length (how to handle mixed lengths?)</li>
 * <li>Bad Cycles - how many machine cycles yielded combined no-call and mismatch rates of >= 80%</li>
 * <li>Strand balance - reads mapped to positive strand / total mapped reads</li>
 * </ul>
 * Metrics are written for the first read of a pair, the second read, and combined for the pair.
 *
 * Read pairs with a mapping quality (MAPQ) is ≥ 20 are classified as chimeric if any of the following criteria are met:
 * <ul>
 * <li>the insert size is larger than MAX_INSERT_SIZE</li>
 * <li>the ends of a pair map to different contigs</li>
 * <li>the paired end orientation is different that the expected orientation</li>
 * <li>either read in the pair contains an SA tag (chimeric alignment)</li>
 * </ul>
 *
 * @author Doug Voet (dvoet at broadinstitute dot org)
 */
@CommandLineProgramProperties(
        summary = CollectAlignmentSummaryMetrics.USAGE_SUMMARY + CollectAlignmentSummaryMetrics.USAGE_DETAILS,
        oneLineSummary = CollectAlignmentSummaryMetrics.USAGE_SUMMARY,
        programGroup = DiagnosticsAndQCProgramGroup.class
)
@DocumentedFeature
public class CollectAlignmentSummaryMetrics extends SinglePassSamProgram {
    static final String USAGE_SUMMARY = "<b>Produces a summary of alignment metrics from a SAM or BAM file.</b>  ";
    static final String USAGE_DETAILS = "This tool takes a SAM/BAM file input and produces metrics detailing the quality of the read " +
            "alignments as well as the proportion of the reads that passed machine signal-to-noise threshold quality filters. " +
            "Note that these quality filters are specific to Illumina data; for additional information, please see the corresponding " +
            "<a href='https://www.broadinstitute.org/gatk/guide/article?id=6329'>GATK Dictionary entry</a>. </p>" +
            "" +
            "<p>Note: Metrics labeled as percentages are actually expressed as fractions!</p>" +

            "<h4>Usage example:</h4>" +
            "<pre>" +
            "    java -jar picard.jar CollectAlignmentSummaryMetrics \\<br />" +
            "          R=reference_sequence.fasta \\<br />" +
            "          I=input.bam \\<br />" +
            "          O=output.txt" +
            "</pre>"+

            "<p>Please see the CollectAlignmentSummaryMetrics " +
            "<a href='http://broadinstitute.github.io/picard/picard-metric-definitions.html#AlignmentSummaryMetrics'>definitions</a> " +
            "for a complete description of the metrics produced by this tool.</p>" +
            "<hr />";

    private static final Log log = Log.getInstance(CollectAlignmentSummaryMetrics.class);
    private static final String HISTOGRAM_R_SCRIPT = "picard/analysis/readLengthDistribution.R";

    @Argument(shortName="H", doc="If Provided, file to write read-length chart pdf.", optional = true)
    public File HISTOGRAM_FILE;

    @Argument(doc="Paired-end reads above this insert size will be considered chimeric along with inter-chromosomal pairs.")
    public int MAX_INSERT_SIZE = ChimeraUtil.DEFAULT_INSERT_SIZE_LIMIT;

    @Argument(doc="Paired-end reads that do not have this expected orientation will be considered chimeric.")
    public Set<PairOrientation> EXPECTED_PAIR_ORIENTATIONS = EnumSet.copyOf(ChimeraUtil.DEFAULT_EXPECTED_ORIENTATIONS);

    @Argument(doc="List of adapter sequences to use when processing the alignment metrics.")
    public List<String> ADAPTER_SEQUENCE = AdapterUtility.DEFAULT_ADAPTER_SEQUENCE;

    @Argument(shortName="LEVEL", doc="The level(s) at which to accumulate metrics.")
    public Set<MetricAccumulationLevel> METRIC_ACCUMULATION_LEVEL = CollectionUtil.makeSet(MetricAccumulationLevel.ALL_READS);

    @Argument(shortName="BS", doc="Whether the SAM or BAM file consists of bisulfite sequenced reads.")
    public boolean IS_BISULFITE_SEQUENCED = false;

    @Argument(doc = "A flag to disable the collection of actual alignment information. " +
            "If false, tool will only count READS, PF_READS, and NOISE_READS. (For backwards compatibility).")
    public boolean COLLECT_ALIGNMENT_INFORMATION = true;

    private AlignmentSummaryMetricsCollector collector;

    protected String[] customCommandLineValidation() {
        if (HISTOGRAM_FILE != null && RExecutor.runningInGatkLiteDocker()) {
            return new String[]{"The histogram file cannot be written because it requires R, which is not available in the GATK Lite Docker image."};
        }
        if (!checkRInstallation(HISTOGRAM_FILE != null)) {
            return new String[]{"R is not installed on this machine. It is required for creating the chart."};
        }
        return super.customCommandLineValidation();
    }

    @Override
    protected void setup(final SAMFileHeader header, final File samFile) {
        IOUtil.assertFileIsWritable(OUTPUT);
        if (HISTOGRAM_FILE != null) {
            if (!METRIC_ACCUMULATION_LEVEL.contains(MetricAccumulationLevel.ALL_READS)) {
                log.error("ReadLength histogram is calculated on all reads only, but ALL_READS were not " +
                        "included in the Metric Accumulation Levels. Histogram will not be generated.");
                HISTOGRAM_FILE=null;
            } else {
                IOUtil.assertFileIsWritable(HISTOGRAM_FILE);
            }
        }

        if (header.getSequenceDictionary().isEmpty()) {
            log.warn(INPUT.getAbsoluteFile() + " has no sequence dictionary. If any reads " +
                    "in the file are aligned, then alignment summary metrics collection will fail.");
        }

        if(REFERENCE_SEQUENCE == null && COLLECT_ALIGNMENT_INFORMATION) {
            log.warn("Without a REFERENCE_SEQUENCE, metrics pertaining to mismatch rates will not be collected!");
        }

        collector = new AlignmentSummaryMetricsCollector(METRIC_ACCUMULATION_LEVEL, header.getReadGroups(), COLLECT_ALIGNMENT_INFORMATION,
                ADAPTER_SEQUENCE, MAX_INSERT_SIZE, EXPECTED_PAIR_ORIENTATIONS, IS_BISULFITE_SEQUENCED);
    }

    @Override protected void acceptRead(final SAMRecord rec, final ReferenceSequence ref) {
        collector.acceptRecord(rec, ref);
    }

    @Override protected void finish() {
        collector.finish();

        final MetricsFile<AlignmentSummaryMetrics, Integer> file = getMetricsFile();
        collector.addAllLevelsToFile(file);

        final AlignmentSummaryMetricsCollector.GroupAlignmentSummaryMetricsPerUnitMetricCollector allReadsGroupCollector =
                (AlignmentSummaryMetricsCollector.GroupAlignmentSummaryMetricsPerUnitMetricCollector) collector.getAllReadsCollector();

        if (allReadsGroupCollector != null) {
            addAllHistogramToMetrics(file, "PAIRED_TOTAL_LENGTH_COUNT", allReadsGroupCollector.pairCollector);
            addAlignedHistogramToMetrics(file, "PAIRED_ALIGNED_LENGTH_COUNT", allReadsGroupCollector.pairCollector);
            addAllHistogramToMetrics(file, "UNPAIRED_TOTAL_LENGTH_COUNT", allReadsGroupCollector.unpairedCollector);
            addAlignedHistogramToMetrics(file, "UNPAIRED_ALIGNED_LENGTH_COUNT", allReadsGroupCollector.unpairedCollector);
        }

        file.write(OUTPUT);

        if (HISTOGRAM_FILE != null) {
            if(file.getNumHistograms() == 0 || file.getAllHistograms().stream().allMatch(Histogram::isEmpty)) {
                log.warn("No Read length histograms to plot.");
            } else {
                final List<String> plotArgs = new ArrayList<>();
                Collections.addAll(plotArgs, OUTPUT.getAbsolutePath(), HISTOGRAM_FILE.getAbsolutePath().replaceAll("%", "%%"), INPUT.getName());

                final int rResult = RExecutor.executeFromClasspath(HISTOGRAM_R_SCRIPT, plotArgs.toArray(new String[0]));
                if (rResult != 0) {
                    throw new PicardException("R script " + HISTOGRAM_R_SCRIPT + " failed with return code " + rResult);
                }
            }
        }

    }

    private static void addAllHistogramToMetrics(final MetricsFile<AlignmentSummaryMetrics, Integer> file, final String label, final AlignmentSummaryMetricsCollector.IndividualAlignmentSummaryMetricsCollector metricsCollector) {
        if (metricsCollector != null) {
            addHistogramToMetrics(file, label, metricsCollector.getReadHistogram());
        }
    }

    private static void addAlignedHistogramToMetrics(final MetricsFile<AlignmentSummaryMetrics, Integer> file, final String label, final AlignmentSummaryMetricsCollector.IndividualAlignmentSummaryMetricsCollector metricsCollector) {
        if (metricsCollector != null) {
            addHistogramToMetrics(file, label, metricsCollector.getAlignedReadHistogram());
        }
    }

    private static void addHistogramToMetrics(final MetricsFile<AlignmentSummaryMetrics, Integer> file, final String label, final Histogram<Integer> readHistogram) {
        readHistogram.setBinLabel("READ_LENGTH");
        readHistogram.setValueLabel(label);
        file.addHistogram(readHistogram);
    }

    // overridden to make it visible on the commandline and to change the doc.
    @Override
    protected ReferenceArgumentCollection makeReferenceArgumentCollection() {
        return new CollectAlignmentRefArgCollection();
    }

    public static class CollectAlignmentRefArgCollection implements ReferenceArgumentCollection {
        @Argument(shortName = StandardOptionDefinitions.REFERENCE_SHORT_NAME,
                doc = "Reference sequence file. Note that while this argument isn't required, without it a small subset (MISMATCH-related) of the metrics cannot be calculated. " +
                        "Note also that if a reference sequence is provided, it must be accompanied by a sequence dictionary.",
                optional = true)
        public File REFERENCE_SEQUENCE = Defaults.REFERENCE_FASTA;

        @Override
        public File getReferenceFile() {
            return REFERENCE_SEQUENCE;
        };
    }
}
