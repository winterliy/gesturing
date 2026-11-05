package dev.ledge.gesturing.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class GesturingGameTests {
    @GameTest
    @SuppressWarnings("removal")
    public void commandsSeatAndRestorePose(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 0, 1);
        helper.setBlock(floor, Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH)
                .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.setPos(start);
        Pose originalPose = player.getPose();

        run(helper, player, "sit");
        helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/sit should put the player on a seat entity");
        run(helper, player, "stand");
        helper.assertTrue(player.getVehicle() == null, "/stand should dismount the seat");
        helper.assertValueEqual(player.getPose(), originalPose, "/stand should restore the original pose");

        run(helper, player, "sit toggle");
        helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/sit toggle should start sitting");
        run(helper, player, "sit toggle");
        helper.assertTrue(player.getVehicle() == null, "/sit toggle should stop sitting");

        String[][] directions = {{"east", "-90.0"}, {"west", "90.0"}, {"south", "0.0"}, {"north", "180.0"}};
        for (String[] direction : directions) {
            run(helper, player, "lay " + direction[0]);
            helper.assertValueEqual(player.getPose(), Pose.SLEEPING, "/lay should set the sleeping pose");
            helper.assertValueEqual(player.getYRot(), Float.parseFloat(direction[1]), "/lay " + direction[0] + " should face that direction");
            run(helper, player, "stand");
            helper.assertValueEqual(player.getPose(), originalPose, "/stand should restore pose after /lay");
        }

        run(helper, player, "crawl");
        helper.assertValueEqual(player.getPose(), Pose.SWIMMING, "/crawl should set the swimming pose");
        run(helper, player, "stand");
        helper.assertValueEqual(player.getPose(), originalPose, "/stand should restore pose after /crawl");

        run(helper, player, "bellyflop");
        helper.assertValueEqual(player.getPose(), Pose.SWIMMING, "/bellyflop should set the swimming pose");
        run(helper, player, "stand");
        run(helper, player, "legsup");
        helper.assertValueEqual(player.getPose(), Pose.SLEEPING, "/legsup should set the sleeping pose");
        run(helper, player, "stand");
        run(helper, player, "spin");
        helper.assertValueEqual(player.getPose(), Pose.SPIN_ATTACK, "/spin should set the spin attack pose");
        run(helper, player, "stand");
        helper.succeed();
    }

    private static void run(GameTestHelper helper, ServerPlayer player, String command) {
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(player.createCommandSourceStack(), command);
    }
}
