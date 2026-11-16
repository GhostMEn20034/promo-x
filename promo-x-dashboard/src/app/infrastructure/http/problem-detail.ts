import { HttpErrorResponse } from '@angular/common/http';

/** One item of a problem detail's `errors`. */
export interface ProblemError {
  readonly category: 'VALIDATION' | 'BUSINESS';
  readonly source: string;
  /** The request field the problem is about, or null when it's about the whole request. */
  readonly path: string | null;
  readonly code: string;
  readonly message: string;
  readonly errorContext: unknown;
}

/** An RFC 9457 problem detail, the body of the errors promo-x-core raises itself (400, 404, 409). */
export interface ProblemDetail {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail: string;
  readonly instance: string;
  /** `VALIDATION_ERROR`, or a business or not-found code. */
  readonly code: string;
  readonly timestamp: string;
  readonly errors: readonly ProblemError[];
}

/**
 * The problem detail in a failed response, or null when the error has none, such as a 401 or 403 from Spring
 * Security, or a network failure.
 */
export function problemDetailOf(error: unknown): ProblemDetail | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body: unknown = error.error;
  return isProblemDetail(body) ? body : null;
}

function isProblemDetail(body: unknown): body is ProblemDetail {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const candidate = body as Partial<Record<keyof ProblemDetail, unknown>>;
  return typeof candidate.code === 'string' && Array.isArray(candidate.errors);
}
