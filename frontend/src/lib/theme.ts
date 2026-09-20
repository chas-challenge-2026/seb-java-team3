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
