export class ApiError extends Error {
  status: number;
  data?: unknown;

  constructor(status: number, message: string, data?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.data = data;
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

export class ApiValidationError extends Error {
  issues: unknown;

  constructor(message: string, issues: unknown) {
    super(message);
    this.name = "ApiValidationError";
    this.issues = issues;
  }
}

export function isApiValidationError(err: unknown): err is ApiValidationError {
  return err instanceof ApiValidationError;
}