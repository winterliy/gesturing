package dev.ledge.gesturing.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public final class GesturingGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
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

        player.setPos(start);
        helper.setBlock(floor, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        run(helper, player, "sit");
        helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/sit should support bottom slabs");
        run(helper, player, "stand");
        player.setPos(start);
        helper.setBlock(floor, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        run(helper, player, "sit");
        helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/sit should support top slabs");
        run(helper, player, "stand");
        player.setPos(start);
        helper.setBlock(floor, Blocks.STONE.defaultBlockState());
        run(helper, player, "sit");
        helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/sit should support full blocks");
        run(helper, player, "stand");

        Direction[] facings = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        float[] yaws = {180f, 0f, -90f, 90f};
        for (int index = 0; index < facings.length; index++) {
            player.setPos(start);
            helper.setBlock(floor, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, facings[index]));
            run(helper, player, "sit");
            helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/sit should support directional blocks");
            helper.assertValueEqual(player.getVehicle().getYRot(), yaws[index], "seat should follow directional block facing");
            run(helper, player, "stand");
        }

        player.setPos(start);
        helper.setBlock(floor, Blocks.AIR.defaultBlockState());
        run(helper, player, "sit");
        helper.assertTrue(player.getVehicle() == null, "/sit should fail safely when there is no support block");
        helper.setBlock(floor, Blocks.WATER.defaultBlockState());
        player.setPos(start);
        run(helper, player, "sit");
        helper.assertTrue(player.getVehicle() == null, "/sit should fail safely over liquid");
        run(helper, player, "lay diagonal");
        helper.assertValueEqual(player.getPose(), originalPose, "invalid /lay direction should leave the player unchanged");
        run(helper, player, "stand");
        helper.assertValueEqual(player.getPose(), originalPose, "extra /stand should be safe when no gesture is active");

        helper.setBlock(floor, Blocks.STONE.defaultBlockState());
        String[][] directions = {{"east", "-90.0"}, {"west", "90.0"}, {"south", "0.0"}, {"north", "180.0"}};
        for (String[] direction : directions) {
            player.setPos(start);
            run(helper, player, "lay " + direction[0]);
            helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/lay should seat the player under the pose copy");
            helper.assertTrue(player.isInvisible(), "/lay should hide the real player behind the pose copy");
            helper.assertFalse(player.isSleeping(), "/lay must not put the real player to sleep");
            helper.assertValueEqual(player.getYRot(), Float.parseFloat(direction[1]), "/lay " + direction[0] + " should face that direction");
            run(helper, player, "stand");
            helper.assertTrue(player.getVehicle() == null, "/stand should leave the seat after /lay");
            helper.assertFalse(player.isInvisible(), "/stand should show the real player again");
            helper.assertValueEqual(player.getY(), start.y(), "/stand should return the player to the original height");
        }

        player.setPos(start);
        run(helper, player, "lay");
        helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/lay without a direction should use the facing direction");
        run(helper, player, "stand");

        player.setPos(start);
        run(helper, player, "crawl");
        helper.assertValueEqual(player.getPose(), Pose.SWIMMING, "/crawl should set the swimming pose");
        run(helper, player, "stand");
        helper.assertValueEqual(player.getPose(), originalPose, "/stand should restore pose after /crawl");

        for (String command : new String[]{"bellyflop", "legsup", "spin"}) {
            player.setPos(start);
            run(helper, player, command);
            helper.assertTrue(player.getVehicle() instanceof SeatEntity, "/" + command + " should seat the player under the pose copy");
            helper.assertTrue(player.isInvisible(), "/" + command + " should hide the real player");
            run(helper, player, "stand");
            helper.assertTrue(player.getVehicle() == null && !player.isInvisible(), "/stand should end /" + command);
            helper.assertValueEqual(player.getY(), start.y(), "/stand should restore height after /" + command);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void activePosesStopOnMovementSneakDamageAndDisconnect(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE.defaultBlockState());
        player.setPos(start);
        Pose originalPose = player.getPose();

        run(helper, player, "crawl");
        player.setPos(start.add(1.0, 0.0, 0.0));
        helper.runAfterDelay(1, () -> {
            helper.assertValueEqual(player.getPose(), originalPose, "movement should end a gesture");

            run(helper, player, "spin");
            player.teleportTo(start.x(), start.y(), start.z());
            helper.runAfterDelay(1, () -> {
                helper.assertTrue(player.getVehicle() == null && !player.isInvisible(), "teleporting should end a gesture");

                run(helper, player, "bellyflop");
                player.setShiftKeyDown(true);
                helper.runAfterDelay(1, () -> {
                    helper.assertTrue(player.getVehicle() == null && !player.isInvisible(), "sneaking should end a gesture");
                    player.setShiftKeyDown(false);

                    run(helper, player, "spin");
                    net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.invoker()
                            .allowDamage(player, helper.getLevel().damageSources().generic(), 1.0f);
                    helper.assertTrue(player.getVehicle() == null && !player.isInvisible(), "damage should end a gesture");

                    run(helper, player, "legsup");
                    net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                            .onPlayDisconnect(player.connection, helper.getLevel().getServer());
                    helper.assertTrue(player.getVehicle() == null && !player.isInvisible(), "disconnect should end the gesture");
                    helper.succeed();
                });
            });
        });
    }

    private static void run(GameTestHelper helper, ServerPlayer player, String command) {
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(player.createCommandSourceStack(), command);
    }
}
