package lv.id.bonne.vaulthuntersextrarules.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lv.id.bonne.vaulthuntersextrarules.VaultHuntersExtraRules;
import lv.id.bonne.vaulthuntersextrarules.data.WorldSettings;
import lv.id.bonne.vaulthuntersextrarules.data.storage.PlayerSettings;
import lv.id.bonne.vaulthuntersextrarules.gamerule.Locality;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.server.command.EnumArgument;

import java.util.Optional;

public class ExtraRulesCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> baseLiteral = Commands.literal("extra_rules");

        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");

        LiteralArgumentBuilder<CommandSourceStack> local_allowed = Commands.literal("local_allowed").requires(stack -> stack.hasPermission(2));

        VaultHuntersExtraRules.extraGameRules.forEach((key, pair) -> {
            GameRules.Type<?> type = pair.getFirst();
            set.then(Commands.literal(key.getId()).executes(ctx -> queryRule(ctx.getSource(), key)
                    ).then(type.createArgument("value").executes(ctx -> setRule(ctx, key, type))
                    ).then(Commands.literal("default").executes(ctx -> defaultRule(ctx, key))));

            local_allowed.then(Commands.literal(key.getId()).executes(ctx -> queryAllowed(ctx.getSource(), key)
                    ).then(Commands.argument("value", EnumArgument.enumArgument(Locality.class)).executes(ctx -> setAllowed(ctx, key))));
        });



        dispatcher.register(baseLiteral.then(set).then(local_allowed));
    }

    private static int setAllowed(CommandContext<CommandSourceStack> ctx, GameRules.Key<?> ruleKey) {
        CommandSourceStack stack = ctx.getSource();
        WorldSettings settings = WorldSettings.get(stack.getLevel());

        Locality locality = ctx.getArgument("value", Locality.class);

        settings.setGameRuleLocality(ruleKey, locality);

        stack.sendSuccess(new TranslatableComponent("commands.gamerule.set", ruleKey.getId(), locality), true);

        return 1;
    }

    private static int queryAllowed(CommandSourceStack stack, GameRules.Key<?> ruleKey) throws CommandSyntaxException {
        WorldSettings settings = WorldSettings.get(stack.getLevel());

        Locality locality = settings.getGameRuleLocality(ruleKey);

        stack.sendSuccess(new TranslatableComponent("commands.gamerule.query", ruleKey.getId(), locality), false);

        return 1;
    }

    static <T extends GameRules.Value<T>> int setRule(CommandContext<CommandSourceStack> ctx, GameRules.Key<T> ruleKey, GameRules.Type<?> ruleType) throws CommandSyntaxException {
        CommandSourceStack stack = ctx.getSource();
        if (WorldSettings.get(stack.getLevel()).getGameRuleLocality(ruleKey) == Locality.SERVER) {
            stack.sendFailure(new TextComponent("Per-player configuration is not enabled for this gamerule!"));
        }

        WorldSettings settings = WorldSettings.get(stack.getLevel());
        ServerPlayer player = stack.getPlayerOrException();
        PlayerSettings playerSettings = settings.getPlayerSettings(player.getUUID());

        Optional<T> tOptional = playerSettings.get(ruleKey);
        if (tOptional.isPresent()) {
            tOptional.get().setFromArgument(ctx, "value");
            stack.sendSuccess(new TranslatableComponent("commands.gamerule.set", ruleKey.getId(), tOptional.get().toString()), true);
            return tOptional.get().getCommandResult();
        }

        GameRules.Value<T> value = (GameRules.Value<T>) ruleType.createRule();
        value.setFromArgument(ctx, "value");

        playerSettings.put(ruleKey, value);

        stack.sendSuccess(new TranslatableComponent("commands.gamerule.set", ruleKey.getId(), value.toString()), true);
        return value.getCommandResult();
    }

    static <T extends GameRules.Value<T>> int defaultRule(CommandContext<CommandSourceStack> ctx, GameRules.Key<T> ruleKey) throws CommandSyntaxException {
        CommandSourceStack stack = ctx.getSource();

        WorldSettings settings = WorldSettings.get(stack.getLevel());
        ServerPlayer player = stack.getPlayerOrException();
        PlayerSettings playerSettings = settings.getPlayerSettings(player.getUUID());

        playerSettings.remove(ruleKey);

        stack.sendSuccess(new TranslatableComponent("commands.gamerule.set", ruleKey.getId(), "default"), true);
        return 0;
    }

    static <T extends GameRules.Value<T>> int queryRule(CommandSourceStack stack, GameRules.Key<T> ruleKey) throws CommandSyntaxException {
        if (WorldSettings.get(stack.getLevel()).getGameRuleLocality(ruleKey) == Locality.SERVER) {
            stack.sendFailure(new TextComponent("Per-player configuration is not enabled for this gamerule!"));
        }

        WorldSettings settings = WorldSettings.get(stack.getLevel());
        ServerPlayer player = stack.getPlayerOrException();
        PlayerSettings playerSettings = settings.getPlayerSettings(player.getUUID());

        Optional<T> tOptional = playerSettings.get(ruleKey);
        if (tOptional.isPresent()) {
            stack.sendSuccess(new TranslatableComponent("commands.gamerule.query", ruleKey.getId(), tOptional.get().toString()), false);
            return tOptional.get().getCommandResult();
        }

        stack.sendSuccess(new TranslatableComponent("commands.gamerule.query", ruleKey.getId(), "default"), false);

        return 0;
    }
}
