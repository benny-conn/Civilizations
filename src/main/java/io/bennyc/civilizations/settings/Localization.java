/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.settings;

import org.bukkit.ChatColor;
import org.mineacademy.fo.settings.SimpleLocalization;

public class Localization extends SimpleLocalization {


    // TODO finish this

    public static String CONFIRM;
    public static String CANCEL;
    public static String NUMBER;
    public static String CIVILIZATION;
    public static String PLAYER;
    public static String OPTIONS;

    private static void init() {
        setPathPrefix(null);
        CONFIRM = getStringColorized("Confirm");
        CANCEL = getStringColorized("Cancel");
        NUMBER = getStringColorized("Number");
        CIVILIZATION = getStringColorized("Civilization");
        PLAYER = getStringColorized("Player");
        OPTIONS = getStringColorized("Options");
    }

    private static String getStringColorized(String string) {
        return getString(string)
                .replace("{1}", Settings.PRIMARY_COLOR.toString())
                .replace("{2}", Settings.SECONDARY_COLOR.toString())
                .replace("{3}", ChatColor.RED.toString());
    }

    @Override
    protected int getConfigVersion() {
        return 0;
    }

    public static class Warnings {
        public static String BUILD;
        public static String BREAK;
        public static String SWITCH;
        public static String INTERACT;
        public static String PVP;
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
        public static String OUTLAW_ENTER;
        public static String OUTLAW_ACTIONS;
        public static String CANNOT_JOIN_CIV;


        private static void init() {
            setPathPrefix("Warnings");
            BUILD = getStringColorized("Build");
            BREAK = getStringColorized("Break");
            SWITCH = getStringColorized("Switch");
            INTERACT = getStringColorized("Interact");
            PVP = getStringColorized("PVP");
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
            OUTLAW_ENTER = getStringColorized("Outlaw_Enter");
            OUTLAW_ACTIONS = getStringColorized("Outlaw_Actions");
            CANNOT_JOIN_CIV = getStringColorized("Cannot_Join_Civ");
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
                setPathPrefix("Warnings.Claim");
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
            public static String TOO_MANY_PLAYERS;
            public static String NO_LIVES;
            public static String DEATH_COST;

            private static void init() {
                setPathPrefix("Warnings.Raid");
                NOT_ENOUGH_PLAYERS = getStringColorized("Not_Enough_Players");
                IN_ENEMY_LAND = getStringColorized("In_Enemy_Land");
                NO_LAND = getStringColorized("No_Land");
                NO_WAR = getStringColorized("No_War");
                ALREADY_IN_RAID = getStringColorized("Already_In_Raid");
                TOO_MANY_PLAYERS = getStringColorized("Too_Many_Players");
                NO_LIVES = getStringColorized("No_Lives");
                DEATH_COST = getStringColorized("Death_Cost");
            }

        }

    }

    public static class Notifications {

        public static String ENTER_CIV;
        public static String LEAVING_CIV;
        public static String ALLIES_TRUE;
        public static String ALLIES_FALSE;
        public static String ENEMIES_TRUE;
        public static String ENEMIES_FALSE;
        public static String WAR;
        public static String SUCCESS_COMMAND;
        public static String SUCCESS_ITEM;
        public static String SUCCESS_OBTAIN;
        public static String SUCCESS_TOGGLE;
        public static String SUCCESS_TELEPORT;
        public static String DEPOSITED;
        public static String WITHDREW;
        public static String OUTLAW_ADD;
        public static String OUTLAW_REMOVE;
        public static String SUCCESS_REPAIR;
        public static String SURRENDERED;
        public static String VISUALIZE_START;
        public static String VISUALIZE_END;
        public static String ACCEPTED_INVITE;
        public static String INFO;
        public static String ENTERED_CHAT;
        public static String LEFT_CHAT;
        public static String CIV_CREATION;
        public static String DENIED_INVITE;
        public static String FLIGHT;
        public static String INVITE_RECEIVED;
        public static String TUTORIAL;
        public static String SELECT_PRIMARY;
        public static String SELECT_SECONDARY;


        private static void init() {
            setPathPrefix("Notifications");
            ENTER_CIV = getStringColorized("Enter_Civ");
            LEAVING_CIV = getStringColorized("Leaving_Civ");
            ALLIES_TRUE = getStringColorized("Allies_True");
            ALLIES_FALSE = getStringColorized("Allies_False");
            ENEMIES_TRUE = getStringColorized("Enemies_True");
            ENEMIES_FALSE = getStringColorized("Enemies_False");
            WAR = getStringColorized("War");
            SUCCESS_COMMAND = getStringColorized("Success_Command");
            SUCCESS_ITEM = getStringColorized("Success_Item");
            SUCCESS_OBTAIN = getStringColorized("Success_Obtain");
            SUCCESS_TOGGLE = getStringColorized("Success_Toggle");
            SUCCESS_TELEPORT = getStringColorized("Success_Teleport");
            DEPOSITED = getStringColorized("Deposited");
            WITHDREW = getStringColorized("Withdrew");
            OUTLAW_ADD = getStringColorized("Outlaw_Add");
            OUTLAW_REMOVE = getStringColorized("Outlaw_Remove");
            SUCCESS_REPAIR = getStringColorized("Success_Repair");
            SURRENDERED = getStringColorized("Surrendered");
            VISUALIZE_START = getStringColorized("Visualize_Start");
            VISUALIZE_END = getStringColorized("Visualize_End");
            ACCEPTED_INVITE = getStringColorized("Accepted_Invite");
            INFO = getStringColorized("Info");
            ENTERED_CHAT = getStringColorized("Entered_Chat");
            LEFT_CHAT = getStringColorized("Left_Chat");
            CIV_CREATION = getStringColorized("Civ_Creation");
            DENIED_INVITE = getStringColorized("Denied_Invite");
            FLIGHT = getStringColorized("Flight");
            INVITE_RECEIVED = getStringColorized("Invite_Received");
            TUTORIAL = getStringColorized("Tutorial");
            SELECT_PRIMARY = getStringColorized("Select_Primary");
            SELECT_SECONDARY = getStringColorized("Select_Secondary");

        }
    }

}
