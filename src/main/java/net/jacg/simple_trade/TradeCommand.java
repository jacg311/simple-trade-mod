package net.jacg.simple_trade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TradeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                literal("trade")
                        .then(literal("request")
                                .then(argument("player", GameProfileArgumentType.gameProfile())
                                        .suggests((context, builder) -> {
                                            PlayerManager manager = context.getSource().getServer().getPlayerManager();
                                            UUID senderId = context.getSource().getPlayer().getUuid();
                                            return CommandSource.suggestMatching(
                                                    manager.getPlayerList().stream()
                                                            .filter(player -> !player.getUuid().equals(senderId))
                                                            .map(player -> player.getGameProfile().getName())
                                                    , builder);
                                        })
                                        .executes(TradeCommand::executeRequest)
                                )
                        )
                        .then(literal("accept")
                                .then(argument("player", GameProfileArgumentType.gameProfile())
                                        .suggests((context, builder) -> {
                                            ServerCommandSource source = context.getSource();
                                            PlayerManager manager = source.getServer().getPlayerManager();
                                            Set<UUID> waitList = SimpleTrade.WAIT_LIST.getOrDefault(context.getSource().getPlayer().getUuid(), new HashSet<>());
                                            return CommandSource.suggestMatching(
                                                    manager.getPlayerList().stream()
                                                            .filter(serverPlayer -> waitList.contains(serverPlayer.getUuid()))
                                                            .map(player -> player.getGameProfile().getName()), builder);
                                        })
                                        .executes(TradeCommand::executeAccept)
                                )
                        )
                        .then(literal("deny")
                                .then(argument("player", GameProfileArgumentType.gameProfile())
                                        .suggests((context, builder) -> {
                                            PlayerManager manager = context.getSource().getServer().getPlayerManager();
                                            Set<UUID> waitList = SimpleTrade.WAIT_LIST.getOrDefault(context.getSource().getPlayer().getUuid(), new HashSet<>());
                                            return CommandSource.suggestMatching(
                                                    manager.getPlayerList().stream()
                                                            .filter(player -> waitList.contains(player.getUuid()))
                                                            .map(player -> player.getGameProfile().getName()), builder);
                                        })
                                        .executes(TradeCommand::executeAbort)
                                )
                        )
        );
    }

    private static int executeRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
            var profile = GameProfileArgumentType.getProfileArgument(context, "player").iterator().next();
            var source = context.getSource();

            PlayerManager manager = source.getServer().getPlayerManager();

            var player = manager.getPlayer(profile.getId());
            var sender = source.getPlayer();

            if (player == null || sender == null) {
                context.getSource().sendError(Text.literal("Invalid Player."));
                return 1;
            }

            Set<UUID> set = SimpleTrade.WAIT_LIST.getOrDefault(player.getUuid(), new HashSet<>());
            set.add(sender.getUuid());
            SimpleTrade.WAIT_LIST.put(player.getUuid(), set);

            String senderName = source.getName();
            Text text = Text.literal(senderName + " wants to trade! ")
                    .append(getText("Accept", "/trade accept ", senderName, Formatting.GREEN))
                    .append(Text.literal(" | "))
                    .append(getText("Deny", "/trade deny ", senderName, Formatting.RED));
            player.sendMessage(text);
            return 1;
    }

    private static Text getText(String text, String commandToRun, String senderName, Formatting formatting) {
        return Text.literal(text)
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandToRun + senderName))
                        .withColor(formatting)
                        .withBold(true));
    }

    private static int executeAccept(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity user = context.getSource().getPlayer();
        ServerPlayerEntity partner = context.getSource().getServer().getPlayerManager().getPlayer(GameProfileArgumentType.getProfileArgument(context, "player").iterator().next().getId());

        if (user == null || partner == null || !SimpleTrade.WAIT_LIST.getOrDefault(user.getUuid(), new HashSet<>()).contains(partner.getUuid())) {
            context.getSource().sendError(Text.literal("Player is not in your wait list."));
            return 1;
        }

        SimpleTrade.WAIT_LIST.get(user.getUuid()).remove(partner.getUuid());

        if (partner.currentScreenHandler != null && !(partner.currentScreenHandler instanceof PlayerScreenHandler)) {
            context.getSource().sendFeedback(Text.literal(partner.getGameProfile().getName() + " is currently trading. Please try again later."), false);
            partner.sendMessage(Text.literal(user.getGameProfile().getName() + " tried to initiate a trade!"));
            return 1;
        }

        TradeGui gui1 = new TradeGui(user, partner.getGameProfile());
        TradeGui gui2 = new TradeGui(partner, user.getGameProfile());
        gui1.open();
        gui2.open();

        context.getSource().sendFeedback(Text.literal("Offer accepted."), false);

        return 1;
    }

    private static int executeAbort(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var profile = GameProfileArgumentType.getProfileArgument(context, "player").iterator().next();

        var player = context.getSource().getPlayer();

        if (player != null) {
            Set<UUID> uuids = SimpleTrade.WAIT_LIST.get(context.getSource().getPlayer().getUuid());
            if (uuids != null) {
                uuids.remove(profile.getId());
                context.getSource().sendFeedback(Text.literal("Trade aborted."), false);
                return 1;
            }
        }

        context.getSource().sendError(Text.literal("Invalid Player!"));
        return 1;
    }
}
