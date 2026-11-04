export type Theme = "light" | "dark";

const themeStorageKey = "seb-theme";

// localStorage kan kasta (blockerad lagring, privat läge) – då gäller ljust tema
export function getSavedTheme(): Theme {
  try {
    return window.localStorage.getItem(themeStorageKey) === "dark" ? "dark" : "light";
  } catch {
    return "light";
  }
}

export function applyTheme(theme: Theme, persist = false) {
  document.documentElement.dataset.theme = theme;

  if (persist) {
    try {
      window.localStorage.setItem(themeStorageKey, theme);
    } catch {
      // Kan inte sparas – valet gäller bara den här sidvisningen
    }
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
