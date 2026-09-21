const LOCAL_DATE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,9})?)?$/;

const SWEDISH_DATE_TIME_FORMATTER = new Intl.DateTimeFormat("sv-SE", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "Europe/Stockholm",
});

export function formatDateTime(timestamp: string | null): string {
  if (!timestamp) {
    return "Ej utförd";
  }

  const date = parseBackendDateTime(timestamp);
  if (!date) {
    return "-";
  }

  return SWEDISH_DATE_TIME_FORMATTER.format(date);
}

export function getDateTimeSortValue(timestamp: string | null): number | null {
  if (!timestamp) {
    return null;
  }

  return parseBackendDateTime(timestamp)?.getTime() ?? null;
}

function parseBackendDateTime(timestamp: string): Date | null {
  const match = LOCAL_DATE_TIME_PATTERN.exec(timestamp);
  if (match) {
    const [, year, month, day, hour, minute, second] = match;

    return new Date(
      Date.UTC(
        Number(year),
        Number(month) - 1,
        Number(day),
        Number(hour),
        Number(minute),
        Number(second ?? "0"),
      ),
    );
  }

  const date = new Date(timestamp);
  return Number.isNaN(date.getTime()) ? null : date;
}
