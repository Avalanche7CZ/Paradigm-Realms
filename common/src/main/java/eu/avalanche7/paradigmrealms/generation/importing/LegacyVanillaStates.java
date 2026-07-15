package eu.avalanche7.paradigmrealms.generation.importing;

final class LegacyVanillaStates {
    private LegacyVanillaStates() {}
    static String resolve(int id, int data) {
        return switch (id) {
            case 0 -> "minecraft:air"; case 1 -> switch (data & 7) { case 1 -> "minecraft:granite"; case 2 -> "minecraft:polished_granite";
                case 3 -> "minecraft:diorite"; case 4 -> "minecraft:polished_diorite"; case 5 -> "minecraft:andesite"; case 6 -> "minecraft:polished_andesite"; default -> "minecraft:stone"; };
            case 2 -> "minecraft:grass_block"; case 3 -> switch (data & 3) { case 1 -> "minecraft:coarse_dirt"; case 2 -> "minecraft:podzol"; default -> "minecraft:dirt"; };
            case 4 -> "minecraft:cobblestone"; case 5 -> wood(data, "planks"); case 6 -> wood(data, "sapling"); case 7 -> "minecraft:bedrock";
            case 8, 9 -> "minecraft:water[level=" + (data & 15) + "]"; case 10, 11 -> "minecraft:lava[level=" + (data & 15) + "]"; case 12 -> (data & 1) == 1 ? "minecraft:red_sand" : "minecraft:sand";
            case 13 -> "minecraft:gravel"; case 14 -> "minecraft:gold_ore"; case 15 -> "minecraft:iron_ore"; case 16 -> "minecraft:coal_ore";
            case 17 -> oldLog(data, false); case 18 -> wood(data, "leaves"); case 19 -> (data & 1) == 1 ? "minecraft:wet_sponge" : "minecraft:sponge";
            case 20 -> "minecraft:glass"; case 21 -> "minecraft:lapis_ore"; case 22 -> "minecraft:lapis_block"; case 23 -> "minecraft:dispenser";
            case 24 -> switch (data & 3) { case 1 -> "minecraft:chiseled_sandstone"; case 2 -> "minecraft:cut_sandstone"; default -> "minecraft:sandstone"; };
            case 25 -> "minecraft:note_block"; case 27 -> poweredRail("powered_rail", data); case 28 -> poweredRail("detector_rail", data); case 30 -> "minecraft:cobweb";
            case 31 -> (data & 3) == 2 ? "minecraft:fern" : "minecraft:short_grass"; case 32 -> "minecraft:dead_bush";
            case 35 -> color(data) + "_wool"; case 37 -> "minecraft:dandelion"; case 38 -> flower(data); case 39 -> "minecraft:brown_mushroom";
            case 40 -> "minecraft:red_mushroom"; case 41 -> "minecraft:gold_block"; case 42 -> "minecraft:iron_block"; case 43 -> doubleSlab(data); case 44 -> slab(data);
            case 45 -> "minecraft:bricks"; case 46 -> "minecraft:tnt"; case 47 -> "minecraft:bookshelf"; case 48 -> "minecraft:mossy_cobblestone";
            case 49 -> "minecraft:obsidian"; case 50 -> "minecraft:torch"; case 51 -> "minecraft:fire"; case 52 -> "minecraft:spawner";
            case 53 -> stairs("oak_stairs", data); case 54 -> facingBlock("chest", data); case 56 -> "minecraft:diamond_ore"; case 57 -> "minecraft:diamond_block";
            case 58 -> "minecraft:crafting_table"; case 60 -> "minecraft:farmland"; case 61, 62 -> facingBlock("furnace", data); case 65 -> facingBlock("ladder", data);
            case 59 -> "minecraft:wheat[age=" + Math.min(data & 15, 7) + "]";
            case 66 -> rail(data); case 67 -> stairs("cobblestone_stairs", data); case 69 -> "minecraft:lever"; case 70 -> "minecraft:stone_pressure_plate";
            case 72 -> "minecraft:oak_pressure_plate"; case 73, 74 -> "minecraft:redstone_ore"; case 76 -> "minecraft:redstone_torch";
            case 77 -> "minecraft:stone_button"; case 78 -> "minecraft:snow"; case 79 -> "minecraft:ice"; case 80 -> "minecraft:snow_block";
            case 81 -> "minecraft:cactus"; case 82 -> "minecraft:clay"; case 83 -> "minecraft:sugar_cane"; case 85 -> "minecraft:oak_fence";
            case 86 -> "minecraft:carved_pumpkin"; case 87 -> "minecraft:netherrack"; case 88 -> "minecraft:soul_sand"; case 89 -> "minecraft:glowstone";
            case 91 -> "minecraft:jack_o_lantern"; case 95 -> color(data) + "_stained_glass";
            case 96 -> trapdoor(data);
            case 98 -> switch (data & 3) { case 1 -> "minecraft:mossy_stone_bricks"; case 2 -> "minecraft:cracked_stone_bricks"; case 3 -> "minecraft:chiseled_stone_bricks"; default -> "minecraft:stone_bricks"; };
            case 101 -> "minecraft:iron_bars"; case 102 -> "minecraft:glass_pane"; case 103 -> "minecraft:melon"; case 106 -> "minecraft:vine";
            case 105 -> "minecraft:melon_stem[age=" + Math.min(data & 15, 7) + "]";
            case 107 -> fenceGate("oak_fence_gate", data); case 108 -> stairs("brick_stairs", data); case 109 -> stairs("stone_brick_stairs", data);
            case 110 -> "minecraft:mycelium"; case 111 -> "minecraft:lily_pad"; case 112 -> "minecraft:nether_bricks";
            case 113 -> "minecraft:nether_brick_fence"; case 114 -> stairs("nether_brick_stairs", data); case 116 -> "minecraft:enchanting_table";
            case 118 -> "minecraft:cauldron"; case 120 -> "minecraft:end_portal_frame"; case 121 -> "minecraft:end_stone";
            case 123, 124 -> "minecraft:redstone_lamp"; case 125 -> wood(data, "planks"); case 126 -> woodSlab(data); case 128 -> stairs("sandstone_stairs", data);
            case 127 -> "minecraft:cocoa";
            case 129 -> "minecraft:emerald_ore"; case 130 -> facingBlock("ender_chest", data); case 132 -> tripwire(data); case 133 -> "minecraft:emerald_block";
            case 134 -> stairs("spruce_stairs", data); case 135 -> stairs("birch_stairs", data); case 136 -> stairs("jungle_stairs", data);
            case 138 -> "minecraft:beacon"; case 139 -> (data & 1) == 1 ? "minecraft:mossy_cobblestone_wall" : "minecraft:cobblestone_wall";
            case 140 -> "minecraft:flower_pot";
            case 141 -> "minecraft:carrots[age=" + Math.min(data & 15, 7) + "]";
            case 143 -> button("oak_button", data);
            case 144 -> "minecraft:skeleton_skull";
            case 145 -> "minecraft:anvil"; case 146 -> "minecraft:trapped_chest"; case 152 -> "minecraft:redstone_block";
            case 153 -> "minecraft:nether_quartz_ore"; case 154 -> "minecraft:hopper";
            case 155 -> switch (data & 3) { case 1 -> "minecraft:chiseled_quartz_block"; case 2 -> "minecraft:quartz_pillar"; default -> "minecraft:quartz_block"; };
            case 156 -> stairs("quartz_stairs", data); case 157 -> poweredRail("activator_rail", data); case 158 -> facingBlock("dropper", data);
            case 159 -> color(data) + "_terracotta"; case 160 -> color(data) + "_stained_glass_pane";
            case 161 -> "minecraft:" + ((data & 1) == 0 ? "acacia" : "dark_oak") + "_leaves";
            case 162 -> oldLog(data, true);
            case 165 -> "minecraft:slime_block"; case 168 -> switch (data & 3) { case 1 -> "minecraft:prismarine_bricks"; case 2 -> "minecraft:dark_prismarine"; default -> "minecraft:prismarine"; };
            case 169 -> "minecraft:sea_lantern"; case 170 -> "minecraft:hay_block"; case 171 -> color(data) + "_carpet";
            case 172 -> "minecraft:terracotta"; case 173 -> "minecraft:coal_block"; case 174 -> "minecraft:packed_ice";
            case 179 -> switch (data & 3) { case 1 -> "minecraft:chiseled_red_sandstone"; case 2 -> "minecraft:cut_red_sandstone"; default -> "minecraft:red_sandstone"; };
            case 188 -> "minecraft:spruce_fence"; case 189 -> "minecraft:birch_fence";
            case 190 -> "minecraft:jungle_fence"; case 191 -> "minecraft:dark_oak_fence";
            case 192 -> "minecraft:acacia_fence";
            default -> null;
        };
    }
    private static String wood(int data, String suffix) { String type = switch (data & 7) { case 1 -> "spruce"; case 2 -> "birch"; case 3 -> "jungle"; case 4 -> "acacia"; case 5 -> "dark_oak"; default -> "oak"; }; return "minecraft:" + type + "_" + suffix; }
    private static String color(int data) { return "minecraft:" + switch (data & 15) { case 0 -> "white"; case 1 -> "orange"; case 2 -> "magenta"; case 3 -> "light_blue"; case 4 -> "yellow"; case 5 -> "lime"; case 6 -> "pink"; case 7 -> "gray"; case 8 -> "light_gray"; case 9 -> "cyan"; case 10 -> "purple"; case 11 -> "blue"; case 12 -> "brown"; case 13 -> "green"; case 14 -> "red"; default -> "black"; }; }
    private static String flower(int data) { return switch (data & 15) { case 1 -> "minecraft:blue_orchid"; case 2 -> "minecraft:allium"; case 3 -> "minecraft:azure_bluet"; case 4 -> "minecraft:red_tulip"; case 5 -> "minecraft:orange_tulip"; case 6 -> "minecraft:white_tulip"; case 7 -> "minecraft:pink_tulip"; case 8 -> "minecraft:oxeye_daisy"; default -> "minecraft:poppy"; }; }
    private static String slab(int data) { String block = switch (data & 7) { case 1 -> "sandstone_slab"; case 2 -> "petrified_oak_slab"; case 3 -> "cobblestone_slab"; case 4 -> "brick_slab"; case 5 -> "stone_brick_slab"; case 6 -> "nether_brick_slab"; case 7 -> "quartz_slab"; default -> "smooth_stone_slab"; }; return "minecraft:" + block + "[type=" + ((data & 8) != 0 ? "top" : "bottom") + "]"; }
    private static String doubleSlab(int data) { return "minecraft:" + switch (data & 7) { case 1 -> "sandstone"; case 2 -> "oak_planks"; case 3 -> "cobblestone"; case 4 -> "bricks"; case 5 -> "stone_bricks"; case 6 -> "nether_bricks"; case 7 -> "quartz_block"; default -> "smooth_stone"; }; }
    private static String woodSlab(int data) { return wood(data, "slab") + "[type=" + ((data & 8) != 0 ? "top" : "bottom") + "]"; }
    private static String oldLog(int data, boolean newerTypes) { String type = newerTypes ? ((data & 1) == 0 ? "acacia" : "dark_oak") : switch (data & 3) { case 1 -> "spruce"; case 2 -> "birch"; case 3 -> "jungle"; default -> "oak"; }; int orientation = data & 12; String suffix = orientation == 12 ? "wood" : "log"; String axis = orientation == 4 ? "x" : orientation == 8 ? "z" : "y"; return "minecraft:" + type + "_" + suffix + "[axis=" + axis + "]"; }
    private static String stairs(String block, int data) { String facing = switch (data & 3) { case 0 -> "east"; case 1 -> "west"; case 2 -> "south"; default -> "north"; }; return "minecraft:" + block + "[facing=" + facing + ",half=" + ((data & 4) != 0 ? "top" : "bottom") + ",shape=straight]"; }
    private static String horizontal(int data) { return switch (data & 3) { case 0 -> "south"; case 1 -> "west"; case 2 -> "north"; default -> "east"; }; }
    private static String facingBlock(String block, int data) { String facing = switch (data & 7) { case 0 -> "down"; case 1 -> "up"; case 2 -> "north"; case 3 -> "south"; case 4 -> "west"; case 5 -> "east"; default -> "north"; }; return "minecraft:" + block + "[facing=" + facing + "]"; }
    private static String stairsRailShape(int data) { return switch (data & 7) { case 1 -> "east_west"; case 2 -> "ascending_east"; case 3 -> "ascending_west"; case 4 -> "ascending_north"; case 5 -> "ascending_south"; default -> "north_south"; }; }
    private static String poweredRail(String block, int data) { return "minecraft:" + block + "[powered=" + ((data & 8) != 0) + ",shape=" + stairsRailShape(data) + "]"; }
    private static String rail(int data) { String shape = switch (data & 15) { case 1 -> "east_west"; case 2 -> "ascending_east"; case 3 -> "ascending_west"; case 4 -> "ascending_north"; case 5 -> "ascending_south"; case 6 -> "south_east"; case 7 -> "south_west"; case 8 -> "north_west"; case 9 -> "north_east"; default -> "north_south"; }; return "minecraft:rail[shape=" + shape + "]"; }
    private static String trapdoor(int data) { return "minecraft:oak_trapdoor[facing=" + horizontal(data) + ",half=" + ((data & 8) != 0 ? "top" : "bottom") + ",open=" + ((data & 4) != 0) + "]"; }
    private static String fenceGate(String block, int data) { return "minecraft:" + block + "[facing=" + horizontal(data) + ",open=" + ((data & 4) != 0) + "]"; }
    private static String tripwire(int data) { return "minecraft:tripwire[attached=" + ((data & 4) != 0) + ",disarmed=" + ((data & 8) != 0) + ",powered=" + ((data & 1) != 0) + "]"; }
    private static String button(String block, int data) { return "minecraft:" + block + "[powered=" + ((data & 8) != 0) + "]"; }
}
