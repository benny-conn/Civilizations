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
	public static String NUMBER;
	public static String CIVILIZATION;
	public static String PLAYER;
	public static String OPTIONS;

	private static void init() {
		pathPrefix(null);
		CONFIRM = getStringColorized("Confirm");
		CANCEL = getStringColorized("Cancel");
		NUMBER = getStringColorized("Number");
		CIVILIZATION = getStringColorized("Civilization");
		PLAYER = getStringColorized("Player");
		OPTIONS = getStringColorized("Options");
	}

	public static class Warnings {
		public static String COOLDOWN_WAIT;
		public static String COOLDOWN_WAIT_MINUTES;
		public static String INVALID_SPECIFIC_ARGUMENT;
		public static String NULL_RESULT;
		public static String FAILED_TELEPORT;
		public static String TOWN_NOT_PUBLIC;
		public static String NO_CIV;
		public static String INSUFFICIENT_PLAYER_FUNDS;
		public static String INSUFFICIENT_CIV_FUNDS;
		public static String CANNOT_INVITE_OUTLAW;
		public static String CANNOT_SPECIFY_SELF;
		public static String LEAVE_CIV_AS_LEADER;
		public static String WRONG_MAP_SCALE;
		public static String MUST_HOLD_MAP;
		public static String CANNOT_CREATE_CIV;
		public static String CIV_NAME_EXISTS;
		public static String LEADER;
		public static String CANNOT_MANAGE_CIV;
		public static String CANNOT_MANAGE_PLOT;
		public static String MINIMUM;
		public static String MAXIMUM_WARPS;
		public static String RENAME_DEFAULT;
		public static String DELETE_DEFAULT;
		public static String OUTLAW_CITIZEN;
		public static String NOT_IN_CIV;
		public static String ENEMY_ALLY;
		public static String ALLY_ENEMY;
		public static String ALREADY_ENEMIES;
		public static String ALREADY_ALLIES;
		public static String NOT_ENEMY;
		public static String NOT_ALLY;
		public static String SURRENDER;
		public static String INVALID_HAND_ITEM;
		public static String NO_BANNER;
		public static String NO_BOOK;
		public static String NOT_WARRING;
		public static String MAXIMUM;


		private static void init() {
			pathPrefix("Warnings");
			COOLDOWN_WAIT = getStringColorized("Cooldown_Wait");
			COOLDOWN_WAIT_MINUTES = getStringColorized("Cooldown_Wait_Minutes");
			INVALID_SPECIFIC_ARGUMENT = getStringColorized("Invalid_Specific_Argument");
			NULL_RESULT = getStringColorized("Null_Result");
			FAILED_TELEPORT = getStringColorized("Failed_Teleport");
			TOWN_NOT_PUBLIC = getStringColorized("Town_Not_Public");
			NO_CIV = getStringColorized("No_Civ");
			INSUFFICIENT_PLAYER_FUNDS = getStringColorized("Insufficient_Player_Funds");
			INSUFFICIENT_CIV_FUNDS = getStringColorized("Insufficient_Civ_Funds");
			CANNOT_INVITE_OUTLAW = getStringColorized("Cannot_Invite_Outlaw");
			CANNOT_SPECIFY_SELF = getStringColorized("Cannot_Specify_Self");
			LEAVE_CIV_AS_LEADER = getStringColorized("Leave_Civ_As_Leader");
			WRONG_MAP_SCALE = getStringColorized("Wrong_Map_Scale");
			MUST_HOLD_MAP = getStringColorized("Must_Hold_Map");
			CANNOT_CREATE_CIV = getStringColorized("Cannot_Create_Civ");
			CIV_NAME_EXISTS = getStringColorized("Civ_Name_Exists");
			LEADER = getStringColorized("Leader");
			CANNOT_MANAGE_CIV = getStringColorized("Cannot_Manage_Civ");
			CANNOT_MANAGE_PLOT = getStringColorized("Cannot_Manage_Plot");
			MINIMUM = getStringColorized("Minimum");
			MAXIMUM_WARPS = getStringColorized("Maximum_Warps");
			RENAME_DEFAULT = getStringColorized("Rename_Default");
			DELETE_DEFAULT = getStringColorized("Delete_Default");
			OUTLAW_CITIZEN = getStringColorized("Outlaw_Citizen");
			NOT_IN_CIV = getStringColorized("Not_In_Civ");
			ENEMY_ALLY = getStringColorized("Enemy_Ally");
			ALLY_ENEMY = getStringColorized("Ally_Enemy");
			ALREADY_ENEMIES = getStringColorized("Already_Enemies");
			ALREADY_ALLIES = getStringColorized("Already_Allies");
			NOT_ENEMY = getStringColorized("Not_Enemy");
			NOT_ALLY = getStringColorized("Not_Ally");
			SURRENDER = getStringColorized("Surrender");
			INVALID_HAND_ITEM = getStringColorized("Invalid_Hand_Item");
			NO_BANNER = getStringColorized("No_Banner");
			NO_BOOK = getStringColorized("No_Book");
			NOT_WARRING = getStringColorized("Not_Warring");
			MAXIMUM = getStringColorized("Maximum");
		}

		public static class Claim {
			public static String NO_CLAIM;
			public static String STOP_VISUALIZING;
			public static String INCOMPLETE_SELECTION;
			public static String CLAIM_OVERLAP;
			public static String MAX_CLAIMS;
			public static String MAX_BLOCKS;
			public static String MAX_CLAIM_SIZE;
			public static String CIV_DISTANCE;
			public static String CONNECT_CLAIM;
			public static String CLAIM_IN_CLAIM;
			public static String STAND_IN_CLAIM;
			public static String SOLID_GROUND;
			public static String COLONY_PREREQUISITE;
			public static String COLONY_DISTANCE;
			public static String MAX_COLONIES;
			public static String PLOT_OVERLAP;
			public static String NO_PLOT;
			public static String MAX_PLOTS;
			public static String NOT_FOR_SALE;
			public static String BEYOND_BORDERS;

			private static void init() {
				pathPrefix("Warnings.Claim");
				NO_CLAIM = getStringColorized("No_Claim");
				STOP_VISUALIZING = getStringColorized("Stop_Visualizing");
				INCOMPLETE_SELECTION = getStringColorized("Incomplete_Selection");
				CLAIM_OVERLAP = getStringColorized("Claim_Overlap");
				MAX_CLAIMS = getStringColorized("Max_Claims");
				MAX_BLOCKS = getStringColorized("Max_Blocks");
				MAX_CLAIM_SIZE = getStringColorized("Max_Claim_Size");
				CIV_DISTANCE = getStringColorized("Civ_Distance");
				CONNECT_CLAIM = getStringColorized("Connect_Claim");
				CLAIM_IN_CLAIM = getStringColorized("Claim_In_Claim");
				STAND_IN_CLAIM = getStringColorized("Stand_In_Claim");
				SOLID_GROUND = getStringColorized("Solid_Ground");
				COLONY_PREREQUISITE = getStringColorized("Colony_Prerequisite");
				COLONY_DISTANCE = getStringColorized("Colony_Distance");
				MAX_COLONIES = getStringColorized("Max_Colonies");
				PLOT_OVERLAP = getStringColorized("Plot_Overlap");
				NO_PLOT = getStringColorized("No_Plot");
				MAX_PLOTS = getStringColorized("Max_Plots");
				NOT_FOR_SALE = getStringColorized("Not_For_Sale");
				BEYOND_BORDERS = getStringColorized("Beyond_Borders");
			}

		}

		public static class Raid {
			public static String NOT_ENOUGH_PLAYERS;
			public static String IN_ENEMY_LAND;
			public static String NO_LAND;
			public static String NO_WAR;
			public static String ALREADY_IN_RAID;

			private static void init() {
				pathPrefix("Warnings.Raid");
				NOT_ENOUGH_PLAYERS = getStringColorized("Not_Enough_Players");
				IN_ENEMY_LAND = getStringColorized("In_Enemy_Land");
				NO_LAND = getStringColorized("No_Land");
				NO_WAR = getStringColorized("No_War");
				ALREADY_IN_RAID = getStringColorized("Already_In_Raid");
			}

		}

	}


	private static String getStringColorized(String string) {
		return getString(string)
				.replace("{1}", Settings.PRIMARY_COLOR.toString())
				.replace("{2}", Settings.SECONDARY_COLOR.toString())
				.replace("{3}", ChatColor.RED.toString());
	}

}
