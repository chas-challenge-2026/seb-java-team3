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