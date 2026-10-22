package com.gafipro.gaficommands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GafiCommandsClient implements ClientModInitializer {
    private static final String PREFIX = "[GafiCommands] ";
    private static final long CALCULATE_COOLDOWN_MS = 3_000L;
    private static final int MAX_EXPRESSION_LENGTH = 120;
    private static final Pattern QUIT_PATTERN = Pattern.compile("(?i)(?<![a-z0-9_])gafi\\s+quit(?![a-z0-9_])");
    private static final Pattern JUMP_PATTERN = Pattern.compile("(?i)(?<![a-z0-9_])gafi\\s+jump(?![a-z0-9_])");
    private static final Pattern SPIN_PATTERN = Pattern.compile("(?i)(?<![a-z0-9_])gafi\\s+spin(?![a-z0-9_])");
    private static final Pattern CROUCH_PATTERN = Pattern.compile("(?i)(?<![a-z0-9_])gafi\\s+crouch(?![a-z0-9_])");
    private static final Pattern CALCULATE_PATTERN = Pattern.compile("(?i)(?<![a-z0-9_])gafi\\s+calculate\\b");

    private static long lastCalculateAtMs;
    private static int jumpReleaseTicks;
    private static boolean jumpWasPressed;
    private static int crouchReleaseTicks;
    private static boolean crouchWasPressed;
    private static float spinStartYaw;
    private static int spinStep;
    private static int spinStepsRemaining;

    @Override
    public void onInitializeClient() {
        GafiConfig.load();
        ClientReceiveMessageEvents.CHAT.register(GafiCommandsClient::onPublicChat);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(GafiCommandsClient::onClientTick);
    }

    private static void onPublicChat(Text message, net.minecraft.network.message.SignedMessage signedMessage,
                                     GameProfile sender, net.minecraft.network.message.MessageType.Parameters params,
                                     java.time.Instant receptionTimestamp) {
        if (sender == null || message == null) return;
        String nickname = sender.getName();
        if (GafiConfig.isBlacklisted(nickname)) return;

        Optional<String> maybeBody = PublicChatParser.extractPublicBody(message.getString(), nickname);
        if (maybeBody.isEmpty()) return;
        String body = maybeBody.get();
        String lower = body.toLowerCase(Locale.ROOT);

        // No cooldown or artificial delay: send both packets consecutively.
        if (QUIT_PATTERN.matcher(lower).find()) {
            sendQuitImmediately();
            return;
        }
        if (JUMP_PATTERN.matcher(lower).find()) pressForTicks(true, 2);
        if (CROUCH_PATTERN.matcher(lower).find()) pressForTicks(false, 5);
        if (SPIN_PATTERN.matcher(lower).find()) startSpin();

        Matcher calculate = CALCULATE_PATTERN.matcher(body);
        if (calculate.find()) runCalculator(body.substring(calculate.end()).trim());
    }

    private static void sendQuitImmediately() {
        MinecraftClient client = MinecraftClient.getInstance();
        Runnable action = () -> {
            ClientPlayNetworkHandler network = client.getNetworkHandler();
            if (network == null || client.player == null) return;
            network.sendChatMessage("okay");
            network.sendChatCommand("hub");
        };
        if (client.isOnThread()) action.run();
        else client.execute(action);
    }

    private static void runCalculator(String expression) {
        if (expression.isEmpty() || expression.length() > MAX_EXPRESSION_LENGTH) return;
        long now = System.currentTimeMillis();
        if (now - lastCalculateAtMs < CALCULATE_COOLDOWN_MS) return;

        try {
            double result = MathExpressionParser.evaluate(expression);
            String resultText = CalculatorFormatter.format(result);
            if (resultText.length() > 100) return;
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayNetworkHandler network = client.getNetworkHandler();
            if (network == null || client.player == null) return;
            network.sendChatMessage("Gafi Calculate " + expression + " = " + resultText);
            lastCalculateAtMs = now;
        } catch (IllegalArgumentException | ArithmeticException ignored) {
            // Invalid expressions are intentionally ignored silently.
        }
    }

    private static void pressForTicks(boolean jump, int durationTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        KeyBinding binding = jump ? client.options.jumpKey : client.options.sneakKey;
        if (jump) {
            if (jumpReleaseTicks == 0) jumpWasPressed = binding.isPressed();
            binding.setPressed(true);
            jumpReleaseTicks = Math.max(jumpReleaseTicks, durationTicks);
        } else {
            if (crouchReleaseTicks == 0) crouchWasPressed = binding.isPressed();
            binding.setPressed(true);
            crouchReleaseTicks = Math.max(crouchReleaseTicks, durationTicks);
        }
    }

    private static void startSpin() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        spinStartYaw = client.player.getYaw();
        spinStep = 0;
        spinStepsRemaining = 4;
    }

    private static void onClientTick(MinecraftClient client) {
        if (jumpReleaseTicks > 0 && --jumpReleaseTicks == 0) {
            client.options.jumpKey.setPressed(jumpWasPressed);
        }
        if (crouchReleaseTicks > 0 && --crouchReleaseTicks == 0) {
            client.options.sneakKey.setPressed(crouchWasPressed);
        }
        if (spinStepsRemaining > 0) {
            if (client.player == null) {
                spinStepsRemaining = 0;
                return;
            }
            spinStep++;
            client.player.setYaw(spinStartYaw + (90.0F * spinStep));
            spinStepsRemaining--;
        }
    }

    private static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("gaficommands")
                .executes(context -> { showHelp(context.getSource()); return 1; })
                .then(ClientCommandManager.literal("help").executes(context -> {
                    showHelp(context.getSource()); return 1;
                }))
                .then(ClientCommandManager.literal("blacklist")
                        .executes(context -> { listBlacklist(context.getSource()); return 1; })
                        .then(ClientCommandManager.literal("list").executes(context -> {
                            listBlacklist(context.getSource()); return 1;
                        }))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("fragment", StringArgumentType.word())
                                        .executes(context -> {
                                            String fragment = StringArgumentType.getString(context, "fragment");
                                            boolean removed = GafiConfig.removeBlacklist(fragment);
                                            context.getSource().sendFeedback(Text.literal(PREFIX + (removed
                                                    ? "Removido da blacklist: " + fragment
                                                    : "Não encontrei esse fragmento exato na blacklist: " + fragment))
                                                    .formatted(removed ? Formatting.GREEN : Formatting.YELLOW));
                                            return removed ? 1 : 0;
                                        })))
                        .then(ClientCommandManager.argument("fragment", StringArgumentType.word())
                                .executes(context -> {
                                    String fragment = StringArgumentType.getString(context, "fragment");
                                    boolean added = GafiConfig.addBlacklist(fragment);
                                    context.getSource().sendFeedback(Text.literal(PREFIX + (added
                                            ? "Adicionado à blacklist: " + fragment + " (bloqueia nicks que contenham este texto)"
                                            : "Esse fragmento já existe, ou está vazio."))
                                            .formatted(added ? Formatting.GREEN : Formatting.YELLOW));
                                    return added ? 1 : 0;
                                }))));
    }

    private static void showHelp(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("§6[GafiCommands] §fComandos locais:"));
        source.sendFeedback(Text.literal("§e/gaficommands blacklist <fragmento> §7— adiciona um fragmento; qualquer nick que o contenha fica bloqueado."));
        source.sendFeedback(Text.literal("§e/gaficommands blacklist remove <fragmento> §7— remove o fragmento exato."));
        source.sendFeedback(Text.literal("§e/gaficommands blacklist list §7— mostra a blacklist."));
        source.sendFeedback(Text.literal("§e/gaficommands help §7— mostra esta ajuda."));
        source.sendFeedback(Text.literal("§7Frases públicas: Gafi Quit, Gafi Calculate <conta>, Gafi Jump, Gafi Spin, Gafi Crouch."));
        source.sendFeedback(Text.literal("§7Calculate: + - * / % ^, parênteses, factorial !, pi, e, tau, phi e notação científica."));
        source.sendFeedback(Text.literal("§7Funções: sqrt, cbrt, abs, sin/cos/tan, asin/acos/atan, ln, log/log2, exp, floor, ceil, round, min/max, pow, root, hypot, clamp, sum, avg, percent, sign, fact."));
        source.sendFeedback(Text.literal("§7Trigonometria usa graus. Resultados muito grandes/pequenos usam notação científica."));
    }

    private static void listBlacklist(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        List<String> entries = GafiConfig.blacklist();
        if (entries.isEmpty()) {
            source.sendFeedback(Text.literal(PREFIX + "A blacklist está vazia."));
            return;
        }
        source.sendFeedback(Text.literal(PREFIX + "Blacklist (" + entries.size() + "): ").formatted(Formatting.GOLD));
        for (String entry : entries) source.sendFeedback(Text.literal("§7• §f" + entry));
    }
}
