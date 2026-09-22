export type Theme = "light" | "dark";

const themeStorageKey = "seb-theme";

export function getSavedTheme(): Theme {
  return window.localStorage.getItem(themeStorageKey) === "dark" ? "dark" : "light";
}

export function applyTheme(theme: Theme, persist = false) {
  document.documentElement.dataset.theme = theme;

  if (persist) {
    window.localStorage.setItem(themeStorageKey, theme);
  }
}

type ViewTransitionDocument = Document & {
  startViewTransition?: (callback: () => void) => unknown;
};

export function transitionTheme(theme: Theme) {
  const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const viewTransitionDocument = document as ViewTransitionDocument;

  if (prefersReducedMotion || !viewTransitionDocument.startViewTransition) {
    applyTheme(theme, true);
    return;
  }

  viewTransitionDocument.startViewTransition(() => applyTheme(theme, true));
}
