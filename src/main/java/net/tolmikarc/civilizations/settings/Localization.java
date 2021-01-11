/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.settings;

import org.mineacademy.fo.settings.SimpleLocalization;

public class Localization extends SimpleLocalization {
	@Override
	protected int getConfigVersion() {
		return 0;
	}

	public static String CONFIRM;
	public static String CANCEL;

	private static void init() {
		CONFIRM = getString("Confirm");
		CANCEL = getString("Cancel");
	}
}
