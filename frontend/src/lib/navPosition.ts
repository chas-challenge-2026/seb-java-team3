// "side": navbaren ligger i sidan på bred skärm och flyttas överst på smal skärm.
// "top": navbaren ligger alltid överst.
export type NavPosition = "side" | "top";

const navPositionStorageKey = "seb-nav-position";

// localStorage kan kasta (blockerad lagring, privat läge) – då gäller standardläget
export function getSavedNavPosition(): NavPosition {
  try {
    return window.localStorage.getItem(navPositionStorageKey) === "top" ? "top" : "side";
  } catch {
    return "side";
  }
}

// CSS läser data-nav på <html>
export function applyNavPosition(position: NavPosition, persist = false) {
  if (position === "top") {
    document.documentElement.dataset.nav = "top";
  } else {
    delete document.documentElement.dataset.nav;
  }

  if (persist) {
    try {
      window.localStorage.setItem(navPositionStorageKey, position);
    } catch {
      // Kan inte sparas – valet gäller bara den här sidvisningen
    }
  }
}
