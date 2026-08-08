import { httpClient } from './httpClient';
import type { ApiResponse } from '../types/common';

/**
 * Website audit API client.
 *
 * <p>Note the nullability throughout: a null score or metric means QPilot could not measure it, and the
 * reason will be in `unavailableChecks`. The UI must render those as "Not available" rather than
 * defaulting them to a number, because a default is indistinguishable from a measurement.
 */

export interface TlsInfo {
  protocol?: string;
  cipherSuite?: string;
  certificateSubject?: string;
  certificateIssuer?: string;
  certificateExpiresAt?: string;
  daysUntilExpiry?: number;
  certificateValid?: boolean;
  certificateError?: string;
}

export interface RedirectHop {
  hop: number;
  url: string;
  statusCode: number;
  location: string;
}

export interface PageMetadata {
  title?: string;
  titleLength?: number;
  metaDescription?: string;
  metaDescriptionLength?: number;
  langAttribute?: string;
  charset?: string;
  hasViewportMeta?: boolean;
  h1Count?: number;
  firstH1?: string;
  imageCount?: number;
  imagesMissingAlt?: number;
  scriptCount?: number;
  stylesheetCount?: number;
  inlineScriptCount?: number;
  totalLinkCount?: number;
  internalLinkCount?: number;
  externalLinkCount?: number;
  htmlSizeBytes?: number;
}

/** Every score is nullable: null means "not measured", never zero. */
export interface AuditScores {
  security?: number;
  seo?: number;
  accessibility?: number;
  bestPractices?: number;
  performance?: number;
  /** Per-score explanation of exactly which checks produced the number. */
  breakdown: Record<string, string[]>;
}

export interface HeaderCheck {
  name: string;
  value?: string;
  present: boolean;
  assessment?: string;
  severity: string;
}

export interface CookieCheck {
  name: string;
  secure: boolean;
  httpOnly: boolean;
  sameSite?: string;
  hasExpiry: boolean;
  assessment: string;
}

export interface LinkCheck {
  url: string;
  internal: boolean;
  /** Null when the request failed outright — `error` then carries the reason. */
  statusCode?: number;
  statusText?: string;
  responseTimeMs?: number;
  broken: boolean;
  error?: string;
}

export interface SeoChecks {
  hasTitle?: boolean;
  titleLengthOptimal?: boolean;
  hasMetaDescription?: boolean;
  metaDescriptionLengthOptimal?: boolean;
  hasSingleH1?: boolean;
  hasCanonical?: boolean;
  canonicalUrl?: string;
  hasOpenGraph?: boolean;
  hasStructuredData?: boolean;
  robotsIndexable?: boolean;
  issues: string[];
}

export interface AccessibilityChecks {
  hasLangAttribute?: boolean;
  imagesMissingAlt?: number;
  totalImages?: number;
  inputsMissingLabel?: number;
  totalInputs?: number;
  hasSkipLink?: boolean;
  hasPageTitle?: boolean;
  emptyLinkCount?: number;
  emptyButtonCount?: number;
  issues: string[];
  /** States plainly that these are the automatable checks only, not full WCAG conformance. */
  note: string;
}

export interface RobotsInfo {
  robotsTxtFound?: boolean;
  pathAllowed?: boolean;
  sitemapDeclared?: boolean;
  sitemapUrl?: string;
  note: string;
}

export interface AuditRecommendation {
  category: string;
  severity: string;
  title: string;
  detail: string;
}

export interface WebsiteAuditResponse {
  targetUrl: string;
  finalUrl?: string;
  /** False means the audit ran and found the target unreachable — a real result, not an error. */
  reachable: boolean;
  failureReason?: string;
  responseCode?: number;
  responseStatusText?: string;
  responseTimeMs?: number;
  dnsLookupMs?: number;
  tlsHandshakeMs?: number;
  contentLengthBytes?: number;
  contentType?: string;
  httpProtocolVersion?: string;
  httpsEnabled?: boolean;
  tls?: TlsInfo;
  redirectChain: RedirectHop[];
  page?: PageMetadata;
  scores: AuditScores;
  securityHeaders: HeaderCheck[];
  allResponseHeaders: HeaderCheck[];
  cookies: CookieCheck[];
  links: LinkCheck[];
  seo?: SeoChecks;
  accessibility?: AccessibilityChecks;
  detectedTechnologies: string[];
  robots?: RobotsInfo;
  recommendations: AuditRecommendation[];
  /** Checks that could not run, each with its reason. Surfaced as "Not available" in the UI. */
  unavailableChecks: string[];
  auditDurationMs: number;
  auditedAt: string;
}

export async function runWebsiteAudit(
  targetUrl: string,
  checkLinks = false,
  maxLinksToCheck = 15,
): Promise<WebsiteAuditResponse> {
  const { data } = await httpClient.post<ApiResponse<WebsiteAuditResponse>>('/website/audit', {
    targetUrl,
    checkLinks,
    maxLinksToCheck,
  });
  return data.data as WebsiteAuditResponse;
}
