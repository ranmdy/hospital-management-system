export type SafetyVerdict = 'PASS' | 'VETO' | 'UNKNOWN';

export interface CheckResult {
  check: string;
  verdict: SafetyVerdict;
  reason: string;
  data?: Record<string, unknown>;
}

export interface SafetyReport {
  tokenAddress: string;
  overall: SafetyVerdict;
  checks: CheckResult[];
  timestamp: number;
}
