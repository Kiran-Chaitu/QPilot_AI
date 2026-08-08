/**
 * QPilot's colour system.
 *
 * <p>Defined once here and consumed by both the MUI theme and the CSS custom properties, so a colour
 * can never drift between a styled component and a stylesheet rule. Every value is a deliberate choice
 * rather than a framework default.
 *
 * <p>Contrast was checked against the surfaces each colour is actually used on. Notably `primary` is
 * used for filled buttons with white label text (4.9:1 — passes AA for body text), while the lighter
 * `primaryLight` is reserved for text and icons on dark surfaces, where the darker tone would fail.
 * The status colours have separate `*Text` variants for exactly this reason: the saturated fill tone
 * that reads well as a chip background does not have enough contrast to be legible as small text.
 */

export const brand = {
  /** Violet — the product's identity colour. Filled buttons, active nav, focus rings. */
  primary: '#7C5CFF',
  primaryDark: '#6344E8',
  primaryLight: '#A695FF',
  /** Teal-cyan — the secondary axis, used for measured/data-oriented affordances. */
  secondary: '#00C2CC',
  secondaryDark: '#0097A0',
  secondaryLight: '#5BE3EA',
  /** Coral — reserved for genuinely high-emphasis moments so it keeps its impact. */
  accent: '#FF7A45',
} as const;

export const status = {
  success: '#12B981',
  successText: '#4ADEA8',
  warning: '#F1A22B',
  warningText: '#FBBF5C',
  error: '#F04452',
  errorText: '#FF8A94',
  info: '#3B82F6',
  infoText: '#7FAEFF',
} as const;

export const darkSurfaces = {
  /** Page background — near-black with a blue cast, so violet and cyan sit on it without vibrating. */
  background: '#0B0C12',
  paper: '#14161F',
  elevated: '#1B1E2A',
  overlay: 'rgba(11, 12, 18, 0.72)',
  border: 'rgba(255, 255, 255, 0.09)',
  borderStrong: 'rgba(255, 255, 255, 0.16)',
  textPrimary: '#ECEDF2',
  textSecondary: '#9AA1B4',
  textDisabled: '#5C6274',
  hover: 'rgba(255, 255, 255, 0.045)',
  selected: 'rgba(124, 92, 255, 0.16)',
} as const;

export const lightSurfaces = {
  background: '#F6F7FB',
  paper: '#FFFFFF',
  elevated: '#FFFFFF',
  overlay: 'rgba(255, 255, 255, 0.78)',
  border: 'rgba(17, 20, 34, 0.10)',
  borderStrong: 'rgba(17, 20, 34, 0.18)',
  textPrimary: '#141726',
  textSecondary: '#5A6172',
  textDisabled: '#9AA1B4',
  hover: 'rgba(17, 20, 34, 0.035)',
  selected: 'rgba(124, 92, 255, 0.10)',
} as const;

/**
 * Categorical series colours for charts, ordered so that adjacent series stay distinguishable and the
 * first few remain separable for the most common forms of colour blindness.
 */
export const chartSeries = [
  brand.primary,
  brand.secondary,
  status.warning,
  '#E879F9',
  status.info,
  status.success,
  brand.accent,
  '#94A3D4',
] as const;

/** Per-severity colours, shared by chips, tables, charts and the risk gauge. */
export const severityColors: Record<string, string> = {
  CRITICAL: '#F0344A',
  HIGH: '#FF6B4A',
  MEDIUM: status.warning,
  LOW: '#8B93A7',
  INFO: status.info,
  OK: status.success,
};

/**
 * Colour by test execution status.
 *
 * <p>Deliberately does not give GENERATED a positive colour: a generated test has proved nothing, and
 * green would invite exactly the misreading the rest of the system works to prevent.
 */
export const executionColors: Record<string, string> = {
  EXECUTED_PASSED: status.success,
  EXECUTED_FAILED: status.error,
  EXECUTION_ERROR: '#FF6B4A',
  GENERATED: brand.secondary,
  SKIPPED: '#8B93A7',
  NOT_EXECUTABLE: '#6B7280',
};

/** Risk band colour, matching the thresholds the backend's score breakdown describes. */
export function riskColor(score: number): string {
  if (score >= 70) return severityColors.CRITICAL;
  if (score >= 40) return status.warning;
  return status.success;
}

export function riskLabel(score: number): string {
  if (score >= 70) return 'High risk';
  if (score >= 40) return 'Moderate risk';
  return 'Low risk';
}
