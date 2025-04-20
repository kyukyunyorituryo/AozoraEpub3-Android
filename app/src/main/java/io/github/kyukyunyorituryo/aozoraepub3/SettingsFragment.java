package io.github.kyukyunyorituryo.aozoraepub3;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

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

        ListPreference listPref = findPreference("UserAgent");
        EditTextPreference editPref = findPreference("UserAgentCustom");

        if (listPref != null && editPref != null) {
            listPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String selected = (String) newValue;
                    boolean isCustom = "custom".equals(selected);
                    editPref.setEnabled(isCustom);
                    return true;
                }
            });

            // 初期表示時に状態を反映
            editPref.setEnabled("custom".equals(listPref.getValue()));
        }
        Preference licensePref = findPreference("oss_licenses");
        if (licensePref != null) {
            licensePref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), OssLicensesMenuActivity.class);
                intent.putExtra("title", "オープンソースライセンス"); // 任意でタイトル設定
                startActivity(intent);
                return true;
            });
        }
        Preference githubPref = findPreference("oss_github");
        if (githubPref != null) {
            githubPref.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/kyukyunyorituryo/AozoraEpub3-Android"));
                startActivity(browserIntent);
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

