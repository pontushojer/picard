/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

package picard.analysis.directed;

import org.broadinstitute.barclay.help.DocumentedFeature;
import picard.util.help.HelpConstants;

/**
 * <p>Metrics generated by CollectHsMetrics for the analysis of target-capture sequencing experiments. The metrics
 * in this class fall broadly into three categories:</p>
 *
 * <ul>
 *     <li>Basic sequencing metrics that are either generated as a baseline against which to evaluate other
 *     metrics or because they are used in the calculation of other metrics.  This includes things like
 *     the genome size, the number of reads, the number of aligned reads etc.</li>
 *     <li>Metrics that are intended for evaluating the performance of the wet-lab assay that generated the data.
 *     This group includes metrics like the number of bases mapping on/off/near baits, %selected, fold 80 base
 *     penalty, hs library size and the hs penalty metrics. These metrics are calculated prior to some of the
 *     filters are applied (e.g. duplicate reads, low mapping quality reads, low base quality bases and bases
 *     overlapping in the middle of paired-end reads are all counted).
 *     </li>
 *     <li>Metrics for assessing target coverage as a proxy for how well the data is likely to perform in downstream
 *     applications like variant calling. This group includes metrics like mean target coverage, the percentage of bases
 *     reaching various coverage levels, and the percentage of bases excluded by various filters. These metrics are computed
 *     using the strictest subset of the data, after all filters have been applied.</li>
 * </ul>
 *
 * @author Tim Fennell
 */
@DocumentedFeature(groupName = HelpConstants.DOC_CAT_METRICS, summary = HelpConstants.DOC_CAT_METRICS_SUMMARY)
public class HsMetrics extends PanelMetricsBase {
    /** The name of the bait set used in the hybrid selection. */
    public String BAIT_SET;

    /** The number of bases which are localized to one or more baits. */
    public long BAIT_TERRITORY;

    /** The ratio of TARGET_TERRITORY/BAIT_TERRITORY.  A value of 1 indicates a perfect design efficiency, while a valud of 0.5 indicates that half of bases within the bait region are not within the target region. */
    public double BAIT_DESIGN_EFFICIENCY;


    /** The number of PF_BASES_ALIGNED that are mapped to the baited regions of the genome. */
    public long ON_BAIT_BASES;

    /** The number of PF_BASES_ALIGNED that are mapped to within a fixed interval containing a baited region, but not within the baited section per se. */
    public long NEAR_BAIT_BASES;

    /** The number of PF_BASES_ALIGNED that are mapped away from any baited region. */
    public long OFF_BAIT_BASES;

    /** The fraction of PF_BASES_ALIGNED located on or near a baited region (ON_BAIT_BASES + NEAR_BAIT_BASES)/PF_BASES_ALIGNED. */
    public double PCT_SELECTED_BASES;

    /** The fraction of PF_BASES_ALIGNED that are mapped away from any baited region, OFF_BAIT_BASES/PF_BASES_ALIGNED.  */
    public double PCT_OFF_BAIT;

    /** The fraction of bases on or near baits that are covered by baits, ON_BAIT_BASES/(ON_BAIT_BASES + NEAR_BAIT_BASES). */
    public double ON_BAIT_VS_SELECTED;

    /** The mean coverage of all baits in the experiment. */
    public double MEAN_BAIT_COVERAGE;

    /** The fraction of aligned, on-bait bases out of the PF bases available.
     * (NOTE: This uses duplicate reads for both numerator and denominator) */
    public double PCT_USABLE_BASES_ON_BAIT;

    /** The fraction of aligned, de-duped, on-target bases out of all the PF bases available. */
    public double PCT_USABLE_BASES_ON_TARGET;

    /** The fold by which the baited region has been amplified above genomic background. */
    public double FOLD_ENRICHMENT;

    /** The estimated number of unique molecules in the selected part of the library. */
    public Long HS_LIBRARY_SIZE;

    /**
     * The "hybrid selection penalty" incurred to get 80% of target bases to 10X. This metric
     * should be interpreted as: if I have a design with 10 megabases of target, and want to get
     * 10X coverage I need to sequence until PF_ALIGNED_BASES = 10^7 * 10 * HS_PENALTY_10X.
     */
    public double HS_PENALTY_10X;

    /**
     * The "hybrid selection penalty" incurred to get 80% of target bases to 20X. This metric
     * should be interpreted as: if I have a design with 10 megabases of target, and want to get
     * 20X coverage I need to sequence until PF_ALIGNED_BASES = 10^7 * 20 * HS_PENALTY_20X.
     */
    public double HS_PENALTY_20X;

    /**
     * The "hybrid selection penalty" incurred to get 80% of target bases to 30X. This metric
     * should be interpreted as: if I have a design with 10 megabases of target, and want to get
     * 30X coverage I need to sequence until PF_ALIGNED_BASES = 10^7 * 30 * HS_PENALTY_30X.
     */
    public double HS_PENALTY_30X;

    /**
     * The "hybrid selection penalty" incurred to get 80% of target bases to 40X. This metric
     * should be interpreted as: if I have a design with 10 megabases of target, and want to get
     * 40X coverage I need to sequence until PF_ALIGNED_BASES = 10^7 * 40 * HS_PENALTY_40X.
     */
    public double HS_PENALTY_40X;

    /**
     * The "hybrid selection penalty" incurred to get 80% of target bases to 50X. This metric
     * should be interpreted as: if I have a design with 10 megabases of target, and want to get
     * 50X coverage I need to sequence until PF_ALIGNED_BASES = 10^7 * 50 * HS_PENALTY_50X.
     */
    public double HS_PENALTY_50X;

    /**
     * The "hybrid selection penalty" incurred to get 80% of target bases to 100X. This metric
     * should be interpreted as: if I have a design with 10 megabases of target, and want to get
     * 100X coverage I need to sequence until PF_ALIGNED_BASES = 10^7 * 100 * HS_PENALTY_100X.
     */
    public double HS_PENALTY_100X;

}
