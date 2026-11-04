import { useState } from "react";
import Modal from "../overlay/Modal";
import Styles from "./SettingsModal.module.css";
import { getSavedTheme, transitionTheme, type Theme } from "../../../lib/theme";
import { applyNavPosition, getSavedNavPosition, type NavPosition } from "../../../lib/navPosition";

interface SettingsModalProps {
  onClose: () => void;
}

const themeOptions: { value: Theme; label: string }[] = [
  { value: "light", label: "Ljust" },
  { value: "dark", label: "Mörkt" },
];

const SettingsModal = ({ onClose }: SettingsModalProps) => {
  const [theme, setTheme] = useState<Theme>(getSavedTheme);
  const [navPosition, setNavPosition] = useState<NavPosition>(getSavedNavPosition);

  const handleThemeChange = (next: Theme) => {
    transitionTheme(next);
    setTheme(next);
  };

  const handleNavPositionChange = (alwaysTop: boolean) => {
    const next: NavPosition = alwaysTop ? "top" : "side";
    applyNavPosition(next, true);
    setNavPosition(next);
  };

  return (
    <Modal title="Inställningar" onClose={onClose}>
      <div className={Styles.settings}>
        <fieldset className={Styles.group}>
          <legend>Utseende</legend>
          <div className={Styles.options}>
            {themeOptions.map((option) => (
              <label key={option.value} className={Styles.option}>
                <input
                  type="radio"
                  name="theme"
                  value={option.value}
                  checked={theme === option.value}
                  onChange={() => handleThemeChange(option.value)}
                />
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className={Styles.group}>
          <legend>Navigeringsfält</legend>
          <label className={Styles.option}>
            <input
              type="checkbox"
              checked={navPosition === "top"}
              onChange={(event) => handleNavPositionChange(event.target.checked)}
            />
            Placera alltid överst
          </label>
          <p className={Styles.hint}>
            Som standard ligger navigeringsfältet i sidan och flyttas överst på mindre skärmar.
          </p>
        </fieldset>
      </div>
    </Modal>
  );
};

export default SettingsModal;
