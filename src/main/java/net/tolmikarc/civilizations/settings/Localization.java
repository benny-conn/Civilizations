/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.settings;

import org.bukkit.ChatColor;
import org.mineacademy.fo.settings.SimpleLocalization;

public class Localization extends SimpleLocalization {
	@Override
	protected int getConfigVersion() {
		return 0;
	}

	public static String CONFIRM;
	public static String CANCEL;

	private static void init() {
		pathPrefix(null);
		CONFIRM = getStringColorized("Confirm");
		CANCEL = getStringColorized("Cancel");
	}

	private static String getStringColorized(String string) {
		return getString(string)
				.replace("{1}", Settings.PRIMARY_COLOR.toString())
				.replace("{2}", Settings.SECONDARY_COLOR.toString())
				.replace("{3}", ChatColor.RED.toString());
	}

}
