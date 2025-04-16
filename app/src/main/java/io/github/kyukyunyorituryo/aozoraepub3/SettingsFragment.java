package io.github.kyukyunyorituryo.aozoraepub3;

import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        EditTextPreference imageScalePref = findPreference("ImageScale");
        CheckBoxPreference scaleEnabled = findPreference("ImageScaleChecked");

        if (scaleEnabled != null && imageScalePref != null) {
            imageScalePref.setEnabled(scaleEnabled.isChecked());

            scaleEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                imageScalePref.setEnabled(enabled);
                return true;
            });
        }

        // 例: gamma_correction によって gamma_value を有効/無効化
        CheckBoxPreference gammaCheck = findPreference("Gamma");
        EditTextPreference gammaValue = findPreference("GammaValue");

        if (gammaCheck != null && gammaValue != null) {
            gammaValue.setEnabled(gammaCheck.isChecked());
            gammaCheck.setOnPreferenceChangeListener((preference, newValue) -> {
                gammaValue.setEnabled((Boolean) newValue);
                return true;
            });
        }

        SwitchPreferenceCompat switchAutoMargin =
                findPreference("AutoMargin");

        String[] dependentKeys = new String[]{
                "AutoMarginLimitH",
                "AutoMarginLimitV",
                "AutoMarginPadding",
                "AutoMarginWhiteLevel",
                "AutoMarginNombreSize"
        };

        if (switchAutoMargin != null) {
            boolean isEnabled = switchAutoMargin.isChecked();
            updateDependents(dependentKeys, isEnabled);

            switchAutoMargin.setOnPreferenceChangeListener((preference, newValue) -> {
                updateDependents(dependentKeys, (Boolean) newValue);
                return true;
            });
        }
    }

    private void updateDependents(String[] keys, boolean enabled) {
        for (String key : keys) {
            Preference pref = findPreference(key);
            if (pref != null) {
                pref.setEnabled(enabled);
            }
        }
    }
}

