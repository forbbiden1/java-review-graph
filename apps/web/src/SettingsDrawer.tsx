import { useState, type ChangeEvent } from "react";
import { formatRuntimeLabel, type AppCopy } from "./i18n";
import type { LanguageMode, RuntimeInfo, UiSettings } from "./platform";

type SettingsDrawerProps = {
  copy: AppCopy;
  draftSettings: UiSettings;
  isOpen: boolean;
  onApiBaseUrlChange: (value: string) => void;
  onCancel: () => void;
  onClose: () => void;
  onLanguageChange: (language: LanguageMode) => void;
  onSave: () => void;
  onUseDefaultApiBaseUrl: () => void;
  runtime: RuntimeInfo;
};

export function SettingsDrawer({
  copy,
  draftSettings,
  isOpen,
  onApiBaseUrlChange,
  onCancel,
  onClose,
  onLanguageChange,
  onSave,
  onUseDefaultApiBaseUrl,
  runtime
}: SettingsDrawerProps) {
  const [activeSection, setActiveSection] = useState<"appearance" | "connection">("appearance");

  if (!isOpen) {
    return null;
  }

  function handleApiBaseUrlChange(event: ChangeEvent<HTMLInputElement>) {
    onApiBaseUrlChange(event.target.value);
  }

  return (
    <div className="settings-overlay" onClick={onClose}>
      <section className="settings-dialog" onClick={(event) => event.stopPropagation()}>
        <div className="dialog-title">
          <span>{copy.settings.title}</span>
          <button type="button" className="ghost-button dialog-close-button" onClick={onClose}>
            {copy.buttons.close}
          </button>
        </div>

        <div className="settings-shell">
          <nav className="settings-sidebar">
            <button
              type="button"
              className={activeSection === "appearance" ? "active" : ""}
              onClick={() => setActiveSection("appearance")}
            >
              <span>{copy.fields.language}</span>
            </button>
            <button
              type="button"
              className={activeSection === "connection" ? "active" : ""}
              onClick={() => setActiveSection("connection")}
            >
              <span>{copy.fields.apiBaseUrl}</span>
            </button>
          </nav>

          <div className="settings-content">
            {activeSection === "appearance" ? (
              <section className="settings-pane">
                <header className="settings-pane-head">
                  <h3>{copy.fields.language}</h3>
                  <p>{copy.copy.languageHint}</p>
                </header>

                <div className="settings-field">
                  <div className="settings-field-text">
                    <span className="settings-field-title">{copy.fields.language}</span>
                    <span className="settings-field-sub">{copy.copy.languageHint}</span>
                  </div>
                  <div className="language-setting-toggle" role="tablist" aria-label={copy.fields.language}>
                    <button
                      type="button"
                      className={draftSettings.language === "en" ? "active" : ""}
                      onClick={() => onLanguageChange("en")}
                    >
                      {copy.settings.english}
                    </button>
                    <button
                      type="button"
                      className={draftSettings.language === "zh" ? "active" : ""}
                      onClick={() => onLanguageChange("zh")}
                    >
                      {copy.settings.chinese}
                    </button>
                  </div>
                </div>
              </section>
            ) : null}

            {activeSection === "connection" ? (
              <section className="settings-pane">
                <header className="settings-pane-head">
                  <h3>{copy.fields.apiBaseUrl}</h3>
                  <p>{copy.copy.settingsSubtitle}</p>
                </header>

                <div className="settings-field">
                  <div className="settings-field-text">
                    <span className="settings-field-title">{copy.fields.runtime}</span>
                    <span className="settings-field-sub">{copy.copy.settingsSubtitle}</span>
                  </div>
                  <input value={formatRuntimeLabel(runtime, draftSettings.language)} readOnly />
                </div>

                <div className="settings-field">
                  <div className="settings-field-text">
                    <span className="settings-field-title">{copy.fields.apiBaseUrl}</span>
                    <span className="settings-field-sub">
                      {runtime.mode === "desktop" ? copy.copy.apiBaseUrlHintDesktop : copy.copy.apiBaseUrlHintWeb}
                    </span>
                  </div>
                  <input
                    value={draftSettings.apiBaseUrl}
                    onChange={handleApiBaseUrlChange}
                    placeholder={copy.placeholders.apiBaseUrl}
                  />
                </div>
              </section>
            ) : null}
          </div>
        </div>

        <div className="settings-actions-bar">
          <button type="button" className="secondary-button" onClick={onUseDefaultApiBaseUrl}>
            {copy.buttons.useDefault}
          </button>
          <div className="settings-actions-right">
            <button type="button" className="ghost-button" onClick={onCancel}>
              {copy.buttons.cancel}
            </button>
            <button type="button" className="primary-button" onClick={onSave}>
              {copy.buttons.saveSettings}
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
