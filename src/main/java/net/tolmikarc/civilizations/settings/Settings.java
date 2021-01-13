/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.settings;

import net.tolmikarc.civilizations.permissions.PermissionGroup;
import net.tolmikarc.civilizations.permissions.PermissionType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.mineacademy.fo.remain.CompParticle;
import org.mineacademy.fo.settings.SimpleSettings;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Settings extends SimpleSettings {

	public static String ALIASES;
	public static Boolean ALL_PERMISSIONS_ENABLED;

	public static String CURRENCY_SYMBOL;
	public static Material CLAIM_TOOL;

	public static String DB_TYPE;
	public static String DB_HOST;
	public static String DB_NAME;
	public static String DB_USER;
	public static String DB_PASS;
	public static Integer DB_PORT;
	public static Integer DELETE_AFTER;


	public static Integer PVP_TOGGLE_COOLDOWN;
	public static Integer TELEPORT_COOLDOWN;
	public static Integer RESPAWN_PROTECTION;


	public static Integer MIN_DISTANCE_FROM_NEAREST_CLAIM;
	public static String COST_FORMULA;
	public static String MAX_CLAIMS_FORMULA;
	public static Integer MAX_BLOCKS_COUNT;
	public static Integer MAX_CLAIM_SIZE;
	public static String MAX_COLONIES_FORMULA;
	public static Integer COLONY_MIN_DISTANCE_FROM_NEAREST_CLAIM;
	public static String MAX_PLOTS_FORMULA;

	public static String UPKEEP_FORMULA;
	public static Double MAX_TAXES;

	public static String MAX_WARPS_FORMULA;
	public static Boolean SHOW_COORDS_IN_INFO;
	public static Boolean FLY_ENABLED;

	public static Integer POWER_MONEY_WEIGHT;
	public static Integer POWER_CITIZENS_WEIGHT;
	public static Integer POWER_BLOCKS_WEIGHT;
	public static Boolean ADD_PLAYER_POWER_TO_CIV;
	public static String POWER_LEADER_FORMULA;
	public static String POWER_OFFICIAL_FORMULA;
	public static String POWER_CITIZEN_FORMULA;
	public static Integer POWER_WAR_WIN;

	public static Integer POWER_PVP_TRANSACTION;
	public static Double MONEY_PVP_TRANSACTION;
	public static Integer POWER_RAID_BLOCK;

	public static ChatColor PRIMARY_COLOR;
	public static ChatColor SECONDARY_COLOR;

	public static Boolean TUTORIAL;

	public static Integer NOTICE_TYPE;
	public static CompParticle CLAIM_PARTICLE;
	public static CompParticle PLOT_PARTICLE;

	public static Integer BLOCKS_PER_SECONDS_REPAIR;
	public static Double REPAIR_COST_PER_BLOCK;
	public static Double SURRENDER_COST;
	public static Integer RAID_BUY_IN;
	public static Integer RAID_LENGTH;
	public static Integer RAID_COOLDOWN;
	public static Integer RAID_RATIO_ONLINE_PLAYERS;
	public static Integer RAID_RATIO_MAX_IN_RAID;
	public static Integer RAID_LIVES;
	public static Boolean RAID_BREAK_SWITCHABLES;
	public static Boolean RAID_PVP_TP_COOLDOWN;
	public static Integer RAID_TNT_COOLDOWN;
	public static Boolean RAID_ATTACKER_TELEPORT;


	public static Boolean OUTLAW_PERMISSIONS_DISABLED;
	public static Boolean OUTLAW_ENTER_DISABLED;

	public static PermissionGroup DEFAULT_GROUP;
	public static PermissionGroup OUTSIDER_GROUP;
	public static PermissionGroup ALLY_GROUP;
	public static PermissionGroup ENEMY_GROUP;

	public static Boolean RESPAWN_CIV;

	public final static Set<Material> SWITCHABLES = new HashSet<>();


	@Override
	protected int getConfigVersion() {
		return 1;
	}

	private static void init() {

		ALIASES = getString("Aliases");

		ALL_PERMISSIONS_ENABLED = getBoolean("All_Permissions_Enabled");

		CURRENCY_SYMBOL = getString("Currency_Symbol");

		CLAIM_TOOL = getMaterial("Claim_Tool").getMaterial();

		DB_TYPE = getString("Database.Type");
		DB_HOST = getString("Database.Host");
		DB_NAME = getString("Database.Name");
		DB_USER = getString("Database.Username");
		DB_PASS = getString("Database.Password");
		DB_PORT = getInteger("Database.Port");
		DELETE_AFTER = getInteger("Database.Delete_After");

		TELEPORT_COOLDOWN = getInteger("Cooldowns.Teleport");
		PVP_TOGGLE_COOLDOWN = getInteger("Cooldowns.PVP");
		RESPAWN_PROTECTION = getInteger("Cooldowns.Respawn_Protection");


		MIN_DISTANCE_FROM_NEAREST_CLAIM = getInteger("Claim.Min_Distance_From_Nearest_Claim");
		COST_FORMULA = getString("Claim.Claim_Cost");
		MAX_CLAIMS_FORMULA = getString("Claim.Max_Claim_Count");
		MAX_BLOCKS_COUNT = getInteger("Claim.Max_Total_Area");
		MAX_CLAIM_SIZE = getInteger("Claim.Max_Claim_Size");
		MAX_COLONIES_FORMULA = getString("Claim.Colony.Max_Colonies");
		COLONY_MIN_DISTANCE_FROM_NEAREST_CLAIM = getInteger("Claim.Colony.Min_Distance_From_Nearest_Claim");
		MAX_PLOTS_FORMULA = getString("Plots.Max_Plots");

		UPKEEP_FORMULA = getString("Upkeep");
		MAX_TAXES = getDouble("Tax_Cap");

		NOTICE_TYPE = getInteger("Notice_Type");
		CLAIM_PARTICLE = CompParticle.valueOf(getString("Visualizing.Claim".toUpperCase()));
		PLOT_PARTICLE = CompParticle.valueOf(getString("Visualizing.Plot".toUpperCase()));

		MAX_WARPS_FORMULA = getString("Extra_Settings.Max_Warps");
		SHOW_COORDS_IN_INFO = getBoolean("Extra_Settings.Show_Coords_In_Info");
		FLY_ENABLED = getBoolean("Extra_Settings.Fly");
		TUTORIAL = getBoolean("Extra_Settings.Tutorial");


		POWER_MONEY_WEIGHT = getInteger("Power.Money");
		POWER_CITIZENS_WEIGHT = getInteger("Power.Citizens");
		POWER_BLOCKS_WEIGHT = getInteger("Power.Blocks");
		ADD_PLAYER_POWER_TO_CIV = getBoolean("Power.Player_Power_Included");

		POWER_LEADER_FORMULA = getString("Power.Player.Leader");
		POWER_OFFICIAL_FORMULA = getString("Power.Player.Official");
		POWER_CITIZEN_FORMULA = getString("Power.Player.Citizen");

		POWER_PVP_TRANSACTION = getInteger("Power.War.PVP");
		MONEY_PVP_TRANSACTION = getDouble("War.Raid.PVP_Transaction");
		POWER_RAID_BLOCK = getInteger("Power.War.Block");
		POWER_WAR_WIN = getInteger("Power.War.Win");

		BLOCKS_PER_SECONDS_REPAIR = getInteger("War.Blocks_Per_Second_Repair");
		REPAIR_COST_PER_BLOCK = getDouble("War.Repair_Cost_Per_Block");
		SURRENDER_COST = getDouble("War.Surrender_Cost");
		RAID_BUY_IN = getInteger("War.Raid.Buy_In");
		RAID_LENGTH = getInteger("War.Raid.Length") * 60;
		RAID_COOLDOWN = getInteger("Cooldowns.Raid") * 60;
		int attackerOnline = getInteger("War.Raid.Online_Ratio.Attacker");
		int attackedOnline = getInteger("War.Raid.Online_Ratio.Attacked");
		if (attackedOnline == 0 || attackerOnline == 0)
			RAID_RATIO_ONLINE_PLAYERS = -1;
		else
			RAID_RATIO_ONLINE_PLAYERS = attackedOnline / attackerOnline;
		int attackerInRaid = getInteger("War.Raid.In_Raid_Ratio.Attacker");
		int attackedInRaid = getInteger("War.Raid.In_Raid_Ratio.Attacked");
		if (attackerInRaid == 0 || attackedInRaid == 0)
			RAID_RATIO_MAX_IN_RAID = -1;
		else
			RAID_RATIO_MAX_IN_RAID = attackedInRaid / attackerInRaid;
		RAID_LIVES = getInteger("War.Raid.Lives");
		RAID_BREAK_SWITCHABLES = getBoolean("War.Raid.Break_Switchables");
		RAID_PVP_TP_COOLDOWN = getBoolean("War.Raid.PVP_TP_Cooldown");
		RAID_TNT_COOLDOWN = getInteger("Cooldowns.TNT");
		RAID_ATTACKER_TELEPORT = getBoolean("War.Raid.Attacker_Teleport");

		OUTLAW_PERMISSIONS_DISABLED = getBoolean("Outlaw.Permissions_Disabled");
		OUTLAW_ENTER_DISABLED = getBoolean("Outlaw.Enter_Disabled");


		String defaultGroupName = getString("Permissions.Groups.Default.Name");
		String allyGroupName = getString("Permissions.Groups.Ally.Name");
		String enemyGroupName = getString("Permissions.Groups.Enemy.Name");
		String outsiderGroupName = getString("Permissions.Groups.Outsider.Name");

		Set<PermissionType> defaultGroupPermissions = new HashSet<>();
		if (getBoolean("Permissions.Groups.Default.Build")) defaultGroupPermissions.add(PermissionType.BUILD);
		if (getBoolean("Permissions.Groups.Default.Break")) defaultGroupPermissions.add(PermissionType.BREAK);
		if (getBoolean("Permissions.Groups.Default.Switch")) defaultGroupPermissions.add(PermissionType.SWITCH);
		if (getBoolean("Permissions.Groups.Default.Interact")) defaultGroupPermissions.add(PermissionType.INTERACT);

		Set<PermissionType> outsiderGroupPermissions = new HashSet<>();
		if (getBoolean("Permissions.Groups.Outsider.Build")) defaultGroupPermissions.add(PermissionType.BUILD);
		if (getBoolean("Permissions.Groups.Outsider.Break")) defaultGroupPermissions.add(PermissionType.BREAK);
		if (getBoolean("Permissions.Groups.Outsider.Switch")) defaultGroupPermissions.add(PermissionType.SWITCH);
		if (getBoolean("Permissions.Groups.Outsider.Interact")) defaultGroupPermissions.add(PermissionType.INTERACT);

		Set<PermissionType> allyGroupPermissions = new HashSet<>();
		if (getBoolean("Permissions.Groups.Ally.Build")) defaultGroupPermissions.add(PermissionType.BUILD);
		if (getBoolean("Permissions.Groups.Ally.Break")) defaultGroupPermissions.add(PermissionType.BREAK);
		if (getBoolean("Permissions.Groups.Ally.Switch")) defaultGroupPermissions.add(PermissionType.SWITCH);
		if (getBoolean("Permissions.Groups.Ally.Interact")) defaultGroupPermissions.add(PermissionType.INTERACT);

		Set<PermissionType> enemyGroupPermissions = new HashSet<>();
		if (getBoolean("Permissions.Groups.Enemy.Build")) defaultGroupPermissions.add(PermissionType.BUILD);
		if (getBoolean("Permissions.Groups.Enemy.Break")) defaultGroupPermissions.add(PermissionType.BREAK);
		if (getBoolean("Permissions.Groups.Enemy.Switch")) defaultGroupPermissions.add(PermissionType.SWITCH);
		if (getBoolean("Permissions.Groups.Enemy.Interact")) defaultGroupPermissions.add(PermissionType.INTERACT);

		DEFAULT_GROUP = new PermissionGroup(defaultGroupName, defaultGroupPermissions);
		OUTSIDER_GROUP = new PermissionGroup(outsiderGroupName, outsiderGroupPermissions);
		ALLY_GROUP = new PermissionGroup(allyGroupName, allyGroupPermissions);
		ENEMY_GROUP = new PermissionGroup(enemyGroupName, enemyGroupPermissions);

		RESPAWN_CIV = getBoolean("Respawn.Civ_Home");


		List<String> switchablesAsStrings = getStringList("Permissions.Switchable_Blocks");
		for (String string : switchablesAsStrings)
			SWITCHABLES.add(Material.valueOf(string));


		int colorPallet = getInteger("Color_Pallet");
		switch (colorPallet) {
			case 1:
				PRIMARY_COLOR = ChatColor.LIGHT_PURPLE;
				SECONDARY_COLOR = ChatColor.DARK_PURPLE;
				break;
			case 2:
				PRIMARY_COLOR = ChatColor.AQUA;
				SECONDARY_COLOR = ChatColor.BLUE;
				break;
			case 3:
				PRIMARY_COLOR = ChatColor.YELLOW;
				SECONDARY_COLOR = ChatColor.GOLD;
				break;
			case 4:
				PRIMARY_COLOR = ChatColor.GREEN;
				SECONDARY_COLOR = ChatColor.DARK_GREEN;
				break;
		}
	}

	public static void convert(String key, String value) {
		set(key, value);
	}


}
