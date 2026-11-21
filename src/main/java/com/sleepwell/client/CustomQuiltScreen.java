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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * The quilt editor: the player's own picture on the left with the cut frame over it, the numbers on the right.
 * <p>
 * The picture is never stretched. The frame has the cloth's own aspect, and dragging it picks which part of the
 * picture is used; the mouse wheel tightens or widens it. What sits inside the frame is exactly what the quilt
 * sampling will use, because both the preview and the texture go through the same cut.
 */
public class CustomQuiltScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 216;
    private static final String[] PRESETS = {"cotton", "down", "silk", "synthetic", "summer_cool", "thick_gingham"};
    private static final String[] PRESET_NAMES = {"棉被", "羽绒被", "蚕丝被", "化纤被", "夏凉被", "厚实格"};

    private final int entityId;
    /** What the maid wears right now, so the name field can start from the name already given to that picture. */
    private final String currentId;
    private final List<String> files;
    private net.minecraft.client.gui.components.EditBox nameBox;
    private String quiltName;
    private int index;
    private int presetIndex;
    private QuiltParams params;
    private int left;
    private int top;

    // Where the frame sits on screen, and how big the picture is drawn under it: drag maths needs both.
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;
    private float drawW = 1.0F;
    private float drawH = 1.0F;
    private boolean dragging;
    /** True while the picture with its frame is shown; false once the finished quilt preview takes over. */
    private boolean framing = true;

    public CustomQuiltScreen(int entityId, String currentId, QuiltParams params) {
        super(Component.literal("被子编辑"));
        this.entityId = entityId;
        this.currentId = currentId == null ? "" : currentId;
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
        int y = this.top + 22;
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
            this.rebuildWidgets();
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
                this.params = this.params.withHand(hand.shell, hand.underShade, hand.rimShade, hand.loft,
                        hand.body);
            }
            button.setMessage(Component.literal("手感 " + PRESET_NAMES[this.presetIndex]));
            this.rebuildWidgets();
        }).bounds(x, y, full, 18).build());

        y += 22;
        addRenderableWidget(new PctSlider(x, y, full, 18, this.params.loft, value -> "蓬松 " + Math.round(value * 100) + "%") {
            @Override
            protected void apply(float value) {
                params = params.withLoft(value);
            }
        });
        y += 22;
        addRenderableWidget(new PctSlider(x, y, full, 18, this.params.body, value -> "挺括 " + Math.round(value * 100) + "%") {
            @Override
            protected void apply(float value) {
                params = params.withBody(value);
            }
        });
        y += 22;
        addRenderableWidget(new PctSlider(x, y, full, 18, this.params.shell / 0.12F, value -> "厚度 " + Math.round(value * 100) + "%") {
            @Override
            protected void apply(float value) {
                params = params.withShell(value * 0.12F);
            }
        });

        int bottom = this.top + PANEL_H - 24;
        addRenderableWidget(Button.builder(Component.literal("打开文件夹"), button -> openFolder())
                .bounds(this.left + 10, bottom, 68, 18).build());
        addRenderableWidget(Button.builder(Component.literal("重置裁剪"),
                button -> {
                    this.params = this.params.withCrop(0.5F, 0.5F, 0.0F);
                    this.rebuildWidgets();
                }).bounds(this.left + 12, this.top + PANEL_H - 46, 46, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal(this.framing ? "确定取景" : "重新取景"), button -> {
                    this.framing = !this.framing;
                    button.setMessage(Component.literal(this.framing ? "确定取景" : "重新取景"));
                    this.rebuildWidgets();
                }).bounds(this.left + 62, this.top + PANEL_H - 46, 54, 18).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> shift(-1))
                .bounds(x, bottom, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> shift(1))
                .bounds(x + 24, bottom, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), button -> apply())
                .bounds(x + 48, bottom, 54, 18).build());
        addRenderableWidget(Button.builder(Component.literal("刷新"), button -> {
                    CustomQuiltLibrary.rescan();
                    this.rebuildWidgets();
                }).bounds(x + 106, bottom, 54, 18).build());

        // The name is what the wall will list, so it sits right above the crop buttons, not buried in a dialog.
        if (this.nameBox != null) {
            this.quiltName = this.nameBox.getValue();
        }
        if (this.quiltName == null) {
            this.quiltName = suggestedName();
        }
        this.nameBox = new net.minecraft.client.gui.components.EditBox(this.font, this.left + 10,
                this.top + 152, 104, 16, Component.literal("被子名称"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setHint(Component.literal("被子名称"));
        this.nameBox.setValue(this.quiltName);
        addRenderableWidget(this.nameBox);
    }

    /** A starting name: the name this picture already has, else the file's own name without its extension. */
    private String suggestedName() {
        if (CustomQuiltLibrary.isCustom(this.currentId)) {
            String known = ClientQuiltCatalog.nameOf(
                    this.currentId.substring(CustomQuiltLibrary.ID_PREFIX.length()));
            if (!known.isEmpty()) {
                return known;
            }
        }
        if (this.files.isEmpty()) {
            return "自定义被子";
        }
        String name = this.files.get(this.index);
        int dot = name.lastIndexOf('.');
        return com.sleepwell.quilt.SavedQuilt.clean(dot > 0 ? name.substring(0, dot) : name);
    }

    private Path currentFile() {
        return this.files.isEmpty() ? null : CustomQuiltLibrary.path(this.files.get(this.index));
    }

    private void shift(int delta) {
        if (this.files.isEmpty()) {
            return;
        }
        this.index = Mth.positiveModulo(this.index + delta, this.files.size());
        this.params = this.params.withCrop(0.5F, 0.5F, 0.0F);
        this.rebuildWidgets();
    }

    private void apply() {
        if (this.files.isEmpty()) {
            this.onClose();
            return;
        }
        Path file = currentFile();
        String name = com.sleepwell.quilt.SavedQuilt.clean(this.nameBox == null ? "" : this.nameBox.getValue());
        // Publish first: the id every other client sees is the picture's content hash, not a local file name.
        String hash = file == null ? "" : QuiltImageCache.publish(file);
        if (hash.isEmpty()) {
            // The picture could not be read or was too heavy: it still works on this client alone.
            String id = CustomQuiltLibrary.ID_PREFIX + this.files.get(this.index);
            SleepWellNetwork.CHANNEL.sendToServer(new SaveQuiltStylePacket(this.entityId, id, this.params));
            this.onClose();
            return;
        }
        // Saving is two things: the quilt joins the wall under its name, and the maid puts it on.
        SleepWellNetwork.CHANNEL.sendToServer(
                new com.sleepwell.network.QuiltCatalogAddPacket(hash, name, this.params.save()));
        SleepWellNetwork.CHANNEL.sendToServer(
                new SaveQuiltStylePacket(this.entityId, "custom:" + hash, this.params));
        this.onClose();
    }

    private void openFolder() {
        Path folder = CustomQuiltLibrary.folder();
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
        graphics.drawString(this.font, name, this.left + 10, this.top + 19, 0xFFF2EEE4, false);

        float aspect = QuiltParams.frameAspect(this.params);
        // Height first so the box never reaches the crop buttons, width follows the cloth's shape.
        this.boxH = 116;
        this.boxW = Math.max(40, Math.min(104, Math.round(this.boxH * aspect)));
        this.boxX = this.left + 12;
        this.boxY = this.top + 34;

        graphics.fill(this.boxX - 1, this.boxY - 1, this.boxX + this.boxW + 1, this.boxY + this.boxH + 1,
                0xFF0B0F18);
        Path file = currentFile();
        if (!this.framing && !this.files.isEmpty()) {
            // The finished sheet: the same sampling the bed will use, tiling and crop included.
            QuiltStyle style = CustomQuiltLibrary.style(this.files.get(this.index), this.params);
            if (style != null) {
                int[] sheet = QuiltTextures.dimensions(style, "");
                com.mojang.blaze3d.vertex.PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(this.boxX, this.boxY, 0.0F);
                pose.scale(this.boxW / (float) sheet[0], this.boxH / (float) sheet[1], 1.0F);
                graphics.blit(QuiltTextures.preview(style), 0, 0, 0, 0, sheet[0], sheet[1], sheet[0], sheet[1]);
                pose.popPose();
            }
        }
        if (this.framing && file != null) {
            ResourceLocation source = QuiltTextures.sourceTexture(file);
            if (source != null) {
                int[] size = QuiltTextures.pictureSize(file);
                float fracW = this.params.effectiveCropW(size[0], size[1]);
                float fracH = this.params.effectiveCropH(size[0], size[1]);
                this.drawW = this.boxW / fracW;
                this.drawH = this.boxH / fracH;
                float drawX = this.boxX + this.boxW * 0.5F - this.params.cropX * this.drawW;
                float drawY = this.boxY + this.boxH * 0.5F - this.params.cropY * this.drawH;
                com.mojang.blaze3d.vertex.PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(drawX, drawY, 0.0F);
                pose.scale(this.drawW / size[0], this.drawH / size[1], 1.0F);
                graphics.blit(source, 0, 0, 0, 0, size[0], size[1], size[0], size[1]);
                pose.popPose();
            }
        }
        // The frame: everything outside it is what the quilt will not use.
        if (this.framing) {
            graphics.fill(this.boxX, this.boxY, this.boxX + this.boxW, this.boxY + this.boxH, 0x22141A2B);
            graphics.renderOutline(this.boxX, this.boxY, this.boxW, this.boxH, 0xEEF2E27A);
        }

        graphics.drawString(this.font, (this.index + 1) + "/" + Math.max(1, this.files.size()),
                this.left + PANEL_W - 34, this.top + 7, 0xFFB9C0CC, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void dragTo(double mouseX, double mouseY) {
        Path file = currentFile();
        if (file == null) {
            return;
        }
        int[] size = QuiltTextures.pictureSize(file);
        float fracW = this.params.effectiveCropW(size[0], size[1]);
        float fracH = this.params.effectiveCropH(size[0], size[1]);
        float centreX = Mth.clamp((float) ((mouseX - this.boxX) / this.drawW), fracW * 0.5F, 1.0F - fracW * 0.5F);
        float centreY = Mth.clamp((float) ((mouseY - this.boxY) / this.drawH), fracH * 0.5F, 1.0F - fracH * 0.5F);
        this.params = this.params.withCrop(centreX, centreY, this.params.cropW);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.framing && button == 0 && mouseX >= this.boxX && mouseX < this.boxX + this.boxW
                && mouseY >= this.boxY && mouseY < this.boxY + this.boxH) {
            this.dragging = true;
            dragTo(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging) {
            dragTo(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.framing && mouseX >= this.boxX && mouseX < this.boxX + this.boxW
                && mouseY >= this.boxY && mouseY < this.boxY + this.boxH) {
            Path file = currentFile();
            if (file != null) {
                int[] size = QuiltTextures.pictureSize(file);
                float auto = this.params.effectiveCropW(size[0], size[1]);
                float width = Mth.clamp(auto * (float) Math.pow(0.9D, delta), 0.1F, 1.0F);
                this.params = this.params.withCrop(this.params.cropX, this.params.cropY, width);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
