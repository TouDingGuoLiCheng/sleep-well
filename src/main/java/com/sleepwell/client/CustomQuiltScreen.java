package com.sleepwell.client;

import com.sleepwell.client.quilt.CustomQuiltLibrary;
import com.sleepwell.client.quilt.QuiltParams;
import com.sleepwell.client.quilt.QuiltStyle;
import com.sleepwell.client.quilt.QuiltTextures;
import com.sleepwell.network.SaveQuiltStylePacket;
import com.sleepwell.network.SleepWellNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Function;

/**
 * The quilt editor: the player's own picture on the left, every number that shapes it on the right.
 * <p>
 * A custom quilt is not a fixed sheet, so this screen is where the numbers a built-in quilt keeps in its source
 * file become player choices: whole sheet or tiled, how many repeats, a third wider or not, which hand to borrow
 * and the three numbers that hand is made of.
 * <p>
 * The preview comes from {@link QuiltTextures#preview} using the same sampling the renderer uses - a preview that
 * rounded differently from the bed would be worse than none.
 */
public class CustomQuiltScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 216;
    /** The hands a player may borrow, cycled by one button instead of six that would not fit. */
    private static final String[] PRESETS = {"cotton", "down", "silk", "synthetic", "summer_cool", "thick_gingham"};
    private static final String[] PRESET_NAMES = {"棉被", "羽绒被", "蚕丝被", "化纤被", "夏凉被", "厚实格"};

    private final int entityId;
    private final List<String> files;
    private int index;
    private int presetIndex;
    private QuiltParams params;
    private int left;
    private int top;

    public CustomQuiltScreen(int entityId, String currentId, QuiltParams params) {
        super(Component.literal("被子编辑"));
        this.entityId = entityId;
        this.params = params == null ? QuiltParams.DEFAULT : params;
        CustomQuiltLibrary.rescan();
        this.files = CustomQuiltLibrary.names();
        String wanted = CustomQuiltLibrary.isCustom(currentId)
                ? currentId.substring(CustomQuiltLibrary.ID_PREFIX.length()) : null;
        this.index = Math.max(0, this.files.indexOf(wanted));
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
        int x = this.left + 122;
        int y = this.top + 24;
        int wide = 78;
        int full = wide * 2 + 4;

        addRenderableWidget(Button.builder(Component.literal(this.params.tiled ? "平铺" : "整张"), button -> {
            this.params = this.params.withTiled(!this.params.tiled);
            button.setMessage(Component.literal(this.params.tiled ? "平铺" : "整张"));
            this.rebuildWidgets();
        }).bounds(x, y, wide, 18).build());
        addRenderableWidget(Button.builder(Component.literal(this.params.wideAcross ? "加宽" : "标准"), button -> {
            this.params = this.params.withWide(!this.params.wideAcross);
            button.setMessage(Component.literal(this.params.wideAcross ? "加宽" : "标准"));
        }).bounds(x + wide + 4, y, wide, 18).build());

        y += 22;
        addRenderableWidget(Button.builder(Component.literal(this.params.dyeTinted ? "跟随床色染色" : "保持原色"),
                button -> {
                    this.params = this.params.withTint(!this.params.dyeTinted);
                    button.setMessage(Component.literal(this.params.dyeTinted ? "跟随床色染色" : "保持原色"));
                }).bounds(x, y, full, 18).build());

        y += 22;
        addRenderableWidget(new PctSlider(x, y, full, 18, (this.params.repeatU - 1.0F) / 3.0F,
                value -> "格数 " + String.format(java.util.Locale.ROOT, "%.1f", 1.0F + value * 3.0F)) {
            @Override
            protected void apply(float value) {
                float repeat = 1.0F + value * 3.0F;
                params = params.withRepeat(repeat, repeat);
            }
        });

        y += 22;
        addRenderableWidget(Button.builder(Component.literal("手感 " + PRESET_NAMES[this.presetIndex]), button -> {
            this.presetIndex = (this.presetIndex + 1) % PRESETS.length;
            QuiltStyle hand = QuiltStyle.of(PRESETS[this.presetIndex]);
            if (hand != null) {
                this.params = borrowHand(this.params, hand);
            }
            button.setMessage(Component.literal("手感 " + PRESET_NAMES[this.presetIndex]));
            this.rebuildWidgets();
        }).bounds(x, y, full, 18).build());

        y += 22;
        addRenderableWidget(new PctSlider(x, y, full, 18, this.params.loft, value -> "蓬松 " + Math.round(value * 100) + "%") {
            @Override
            protected void apply(float value) {
                params = new QuiltParams(params.tiled, params.repeatU, params.repeatV, params.wideAcross,
                        params.shell, params.underShade, params.rimShade, value, params.body, params.dyeTinted);
            }
        });
        y += 22;
        addRenderableWidget(new PctSlider(x, y, full, 18, this.params.body, value -> "挺括 " + Math.round(value * 100) + "%") {
            @Override
            protected void apply(float value) {
                params = new QuiltParams(params.tiled, params.repeatU, params.repeatV, params.wideAcross,
                        params.shell, params.underShade, params.rimShade, params.loft, value, params.dyeTinted);
            }
        });
        y += 22;
        addRenderableWidget(new PctSlider(x, y, full, 18, this.params.shell / 0.12F, value -> "厚度 " + Math.round(value * 100) + "%") {
            @Override
            protected void apply(float value) {
                params = new QuiltParams(params.tiled, params.repeatU, params.repeatV, params.wideAcross,
                        value * 0.12F, params.underShade, params.rimShade, params.loft, params.body,
                        params.dyeTinted);
            }
        });

        int bottom = this.top + PANEL_H - 24;
        addRenderableWidget(Button.builder(Component.literal("打开图片文件夹"), button -> openFolder())
                .bounds(this.left + 10, bottom, 106, 18).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> shift(-1))
                .bounds(x, bottom, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> shift(1))
                .bounds(x + 24, bottom, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), button -> apply())
                .bounds(x + 48, bottom, 54, 18).build());
        addRenderableWidget(Button.builder(Component.literal("预览"), button -> {
                    CustomQuiltLibrary.rescan();
                    this.rebuildWidgets();
                }).bounds(x + 106, bottom, 54, 18).build());
    }

    private static QuiltParams borrowHand(QuiltParams params, QuiltStyle hand) {
        return new QuiltParams(params.tiled, params.repeatU, params.repeatV, params.wideAcross, hand.shell,
                hand.underShade, hand.rimShade, hand.loft, hand.body, params.dyeTinted);
    }

    /** Same hand, current numbers kept: the three sliders stay where the player put them. */
    private void shift(int delta) {
        if (this.files.isEmpty()) {
            return;
        }
        this.index = Mth.positiveModulo(this.index + delta, this.files.size());
        this.rebuildWidgets();
    }

    private void apply() {
        if (this.files.isEmpty()) {
            this.onClose();
            return;
        }
        String id = CustomQuiltLibrary.ID_PREFIX + this.files.get(this.index);
        SleepWellNetwork.CHANNEL.sendToServer(new SaveQuiltStylePacket(this.entityId, id, this.params));
        this.onClose();
    }

    private void openFolder() {
        java.nio.file.Path folder = CustomQuiltLibrary.folder();
        try {
            java.nio.file.Files.createDirectories(folder);
        } catch (java.io.IOException ignored) {
            return;
        }
        net.minecraft.Util.getPlatform().openFile(folder.toFile());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(this.left, this.top, this.left + PANEL_W, this.top + PANEL_H, 0xF0141A2B);
        graphics.renderOutline(this.left, this.top, PANEL_W, PANEL_H, 0x88F2E27A);
        graphics.drawString(this.font, "自定义被子", this.left + 10, this.top + 7, 0xFFF2E27A, false);

        String name = this.files.isEmpty() ? "（文件夹里还没有图片）" : this.files.get(this.index);
        graphics.drawString(this.font, name, this.left + 10, this.top + 20, 0xFFF2EEE4, false);

        int boxH = 128;
        int boxW = 96;
        int px = this.left + 12;
        int py = this.top + 34;
        graphics.fill(px - 2, py - 2, px + boxW + 2, py + boxH + 2, 0xFF0B0F18);
        if (!this.files.isEmpty()) {
            QuiltStyle style = CustomQuiltLibrary.style(this.files.get(this.index), this.params);
            if (style != null) {
                com.mojang.blaze3d.vertex.PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(px, py, 0.0F);
                pose.scale(boxW / 192.0F, boxH / 256.0F, 1.0F);
                graphics.blit(QuiltTextures.preview(style), 0, 0, 0, 0, 192, 256, 192, 256);
                pose.popPose();
            }
        }
        graphics.drawString(this.font, (this.index + 1) + "/" + Math.max(1, this.files.size()),
                px, py + boxH + 6, 0xFFB9C0CC, false);
        graphics.drawString(this.font, this.params.tiled ? "平铺 x" + String.format(java.util.Locale.ROOT, "%.1f", this.params.repeatU)
                        : "整张一次", px, py + boxH + 18, 0xFF8E97A6, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A 0..1 slider whose label is the player-facing value; each one writes straight into the parameters. */
    private abstract static class PctSlider extends AbstractSliderButton {
        private final Function<Float, String> format;

        PctSlider(int x, int y, int width, int height, float value, Function<Float, String> format) {
            super(x, y, width, height, Component.empty(), Mth.clamp(value, 0.0F, 1.0F));
            this.format = format;
            updateMessage();
        }

        protected abstract void apply(float value);

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(this.format.apply((float) this.value)));
        }

        @Override
        protected void applyValue() {
            apply((float) this.value);
        }
    }
}
