package com.testforge.backend.analysis.scan;

import java.util.List;

/**
 * Complete output of one deterministic scan over a project's real files.
 *
 * @param metrics            counts measured from disk
 * @param findings           evidence-backed security/quality findings
 * @param riskScore          0-100, computed by the documented weighted formula
 * @param riskReasons        plain-English drivers of the score
 * @param scoreBreakdown     the arithmetic behind {@code riskScore}, one line item per contribution
 * @param testedSurfacePercent measured proportion of the test surface that has tests
 * @param testedSurfaceBasis exactly what {@code testedSurfacePercent} measured
 * @param coverageGaps       concrete untested endpoints/areas identified by name
 * @param summary            factual narrative assembled from the measured numbers
 * @param observations       notable structural facts worth flagging
 */
public record StaticScanResult(
        ScanMetrics metrics,
        List<ScanFinding> findings,
        int riskScore,
        List<String> riskReasons,
        List<String> scoreBreakdown,
        int testedSurfacePercent,
        String testedSurfaceBasis,
        List<String> coverageGaps,
        String summary,
        List<String> observations
) {
}
