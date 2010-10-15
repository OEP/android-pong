package org.oep.pong;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PongPreferencesActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		addPreferencesFromResource(R.xml.preferences);
	}
}