package com.tian.ye.mymenuitemtest;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by Ye Tian on 08/07/2015.
 */
public class SettingActivity extends PreferenceActivity {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings);

        }
    }
