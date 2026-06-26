/*
 * Copyright (C) 2021-2025 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatlog.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.darkkronicle.advancedchatcore.chat.AdvancedChatScreen;
import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchatcore.chat.MessageSender;
import io.github.darkkronicle.advancedchatcore.config.ConfigStorage;
import io.github.darkkronicle.advancedchatcore.gui.ContextMenu;
import io.github.darkkronicle.advancedchatcore.util.Colors;
import io.github.darkkronicle.advancedchatcore.util.FindType;
import io.github.darkkronicle.advancedchatcore.util.SearchUtils;
import io.github.darkkronicle.advancedchatcore.util.SyncTaskQueue;
import io.github.darkkronicle.advancedchatlog.AdvancedChatLog;
import io.github.darkkronicle.advancedchatlog.ChatLogData;
import io.github.darkkronicle.advancedchatlog.config.ChatLogConfigStorage;
import io.github.darkkronicle.advancedchatlog.util.LogChatMessage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Util;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.PatternSyntaxException;

@Environment(EnvType.CLIENT)
public class ChatLogScreen extends GuiBase {

    /**
     * The px where the scroll will start
     */
    private double scrollStart = 0;

    /**
     * The px where the scroll will end
     */
    private double scrollEnd = 0;

    /**
     * The current value of scroll. This should be used to grab scroll value.
     */
    private double currentScroll = 0;

    /**
     * Last time scroll was updated. Used for smooth scroll.
     */
    private long lastScrollTime = 0;

    private ContextMenu menu = null;
    private LogChatMessage message = null;

    private List<ChatMessage.AdvancedChatLine> renderLines;
    private GuiTextFieldGeneric search = null;
    private TextFieldRunnable send = null;
    private ButtonGeneric searchType = null;
    private FindType findType = FindType.LITERAL;

    public ChatLogScreen() {
        super();
    }

    public void add(LogChatMessage message) {
        add(message.getMessage());
        if (currentScroll > 0) {
            currentScroll += message.getMessage().getLineCount() * (this.fontHeight + 2);
        }
    }

    public void add(ChatMessage message) {
        try {
            if (SearchUtils.isMatch(
                    message.getDisplayText().getString(), search.getValue(), findType)) {
                for (int i = 0; i < message.getLineCount(); i++) {
                    renderLines.add(0, message.getLines().get(i));
                }
            }
        } catch (PatternSyntaxException e) {
            // Already handled earlier.
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        setLines(ChatLogData.getInstance().getMessages());
        int width = this.mc.getWindow().getGuiScaledWidth();
        int height = this.mc.getWindow().getGuiScaledHeight();
        search = new GuiTextFieldGeneric((width / 2) - 70, 6, 141, 20, this.font);
        addTextField(
                search,
                (textField -> {
                    searchText(textField.getValue());
                    return true;
                })
        );
        searchType = new ButtonGeneric(width / 2 + 72, 6, 70, false, findType.getDisplayName());
        addButton(
                searchType,
                ((button, mouseButton) -> {
                    if (mouseButton == 0) {
                        findType = findType.cycle(true);
                    } else {
                        findType = findType.cycle(false);
                    }
                    button.setDisplayString(findType.getDisplayName());
                    searchText(search.getValue());
                }));
        send = new TextFieldRunnable(
                2,
                height - 15,
                width - 4,
                12,
                this.font,
                (textFieldRunnable -> {
                    String text = textFieldRunnable.getValue();
                    MessageSender.getInstance().sendMessage(text);
                    textFieldRunnable.setValue("");
                })
        );
        addTextField(send, null);
        send.setFocused(true);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (super.onMouseClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }
        int mouseX = (int) mouseButtonEvent.x();
        int mouseY = (int) mouseButtonEvent.y();
        int mouseButton = mouseButtonEvent.button();
        if (mouseButton == 1) {
            createContextMenu(mouseX, mouseY);
            return true;
        }
        if (menu != null && menu.onMouseClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }
        if (isShiftDown()) {
            relativeScroll(mouseY);
            return true;
        }
        Style style = getHoverStyle(mouseX, mouseY);
        if (style != null) {
            return handleTextClick(style);
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        if (super.onMouseDragged(mouseButtonEvent, deltaX, deltaY)) {
            return true;
        }
        if (isShiftDown()) {
            relativeScroll((int) mouseButtonEvent.y());
            return true;
        }
        return false;
    }

    private boolean handleTextClick(Style style) {
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            return false;
        }
        // The log viewer has no chat input, so vanilla routes a SuggestCommand click through the
        // (no-op here) Screen.insertText, and defaultHandleClickEvent's own screen handling fights
        // with us. Handle it directly: open the chat screen with the command prefilled, deferred one
        // tick via SyncTaskQueue so we are not swapping screens mid-click (which closed it instantly).
        if (clickEvent instanceof ClickEvent.SuggestCommand suggest) {
            SyncTaskQueue.getInstance().add(1,
                    () -> this.mc.setScreenAndShow(new AdvancedChatScreen(suggest.command())));
            return true;
        }
        defaultHandleClickEvent(clickEvent, this.mc, this);
        return true;
    }

    public void relativeScroll(int y) {
        // Scroll click
        int height = this.mc.getWindow().getGuiScaledHeight() - 100;
        y -= 40;
        float percent = 1 - Math.max(0, Math.min((float) y / height, 1));
        int newPix = (int) (percent * (renderLines.size() * (this.fontHeight + 2)));
        scrollEnd = newPix;
        scrollStart = newPix;
        lastScrollTime = Util.getMillis();
    }


    private void searchText(String contents) {
        if (contents.isEmpty()) {
            setLines(ChatLogData.getInstance().getMessages());
            return;
        }
        List<LogChatMessage> sorted = new ArrayList<>();
        for (LogChatMessage l : ChatLogData.getInstance().getMessages()) {
            ChatMessage m = l.getMessage();
            try {
                if (SearchUtils.isMatch(m.getDisplayText().getString(), contents, findType)) {
                    sorted.add(l);
                }
            } catch (PatternSyntaxException e) {
                sorted.clear();
                MutableComponent text = Component.literal(
                        StringUtils.translate("advancedchatlog.message.regexerror")).withStyle(
                        Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)));
                text.getSiblings().add(Component.literal(" " + e.getDescription()).withStyle(Style.EMPTY.withColor(Colors.getInstance().getColorOrWhite("gray").color())));
                ChatMessage message = ChatMessage.builder().displayText(text).originalText(text).build();
                sorted.add(new LogChatMessage(message));
                break;
            }
        }
        setLines(sorted);
    }

    private void setLines(List<LogChatMessage> messages) {
        // Don't want jank
        messages = new ArrayList<>(messages);
        if (messages.isEmpty()) {
            MutableComponent text = Component.literal(
                    StringUtils.translate("advancedchatlog.message.none")
            ).withStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)));
            messages.add(new LogChatMessage(ChatMessage.builder().displayText(text).originalText(text).build()));
        }
        renderLines = new ArrayList<>();
        for (LogChatMessage l : messages) {
            ChatMessage m = l.getMessage();
            for (int i = m.getLineCount() - 1; i >= 0; i--) {
                renderLines.add(m.getLines().get(i));
            }
        }
    }

    private void updateScroll() {
        long time = Util.getMillis();
        // Starting scroll + percent completed
        currentScroll = scrollStart + (
                (scrollEnd - scrollStart) * (1 - ((ConfigStorage.Easing) ChatLogConfigStorage.General.SCROLL_TYPE.config.getOptionListValue()).apply(
                        1 - ((float) time - lastScrollTime) / ChatLogConfigStorage.General.SCROLL_TIME.config.getIntegerValue()
                ))
        );
        int fontHeight = (this.fontHeight + 2);
        if (currentScroll < 0) {
            // Make sure we can still see at least one line
            currentScroll = 0;
            scrollEnd = 0;
            lastScrollTime = 0;
        }
        int maxY = fontHeight * (renderLines.size() - 1);
        if (currentScroll >= maxY) {
            // Make sure it stops at the top
            currentScroll = maxY;
            scrollEnd = maxY;
            lastScrollTime = 0;
        }
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        // Update the scroll variables
        scrollEnd = currentScroll + verticalAmount * 10 * ChatLogConfigStorage.General.SCROLL_MULTIPLIER.config.getDoubleValue();
        scrollStart = currentScroll;
        lastScrollTime = Util.getMillis();
        return true;
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        updateScroll();
        int height = this.mc.getWindow().getGuiScaledHeight();
        int width = this.mc.getWindow().getGuiScaledWidth();
        int lineHeight = this.fontHeight + 2;
        // 60 px top, 40 px bottom
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);

        // Scissor to keep boundaries for the half scroll
        double scale = this.mc.getWindow().getGuiScale();
        ScissorUtil.applyScissor(ctx,
                0,
                (int) (40 * scale),
                (int) (width * scale),
                (int) ((height - 70) * scale));

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            ChatMessage.AdvancedChatLine line = renderLines.get(i);
            ctx.drawString(this.font,
                    line.getText(),
                    10,
                    height - y - 40 - this.fontHeight,
                    Colors.getInstance().getColorOrWhite("white").color(),
                    true);
            y += lineHeight;
        }
        ScissorUtil.resetScissor(ctx);
        ctx.drawCenteredString(this.font,
                (scrollLine + 1) + "/" + renderLines.size(),
                width / 2,
                height - 28,
                Colors.getInstance().getColorOrWhite("white").color());
        drawHoverStyle(ctx, getHoverStyle(mouseX, mouseY), mouseX, mouseY);
        if (menu != null) {
            menu.render(ctx, mouseX, mouseY, true);
        }
    }

    /**
     * 26.2: {@code DrawContext.drawHoverEvent} no longer exists. Render the {@code SHOW_TEXT}
     * hover tooltip ourselves via {@code setTooltipForNextFrame}, mirroring vanilla's hover
     * handling for the common (text) case.
     */
    private void drawHoverStyle(GuiContext ctx, Style style, int mouseX, int mouseY) {
        if (style == null) {
            return;
        }
        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent instanceof HoverEvent.ShowText showText) {
            // MaLiLib's GuiContext render flow doesn't flush vanilla's deferred tooltip, so draw it
            // immediately via MaLiLib (mirrors how Core renders hover text).
            RenderUtils.drawHoverText(ctx, mouseX, mouseY,
                    List.of(showText.value().getString().split("\n")));
        }
    }

    public void createContextMenu(int mouseX, int mouseY) {
        LinkedHashMap<Component, ContextMenu.ContextConsumer> actions = new LinkedHashMap<>();
        message = getMessage(mouseX, mouseY);
        if (message != null) {
            MutableComponent data = Component.empty();
            try {
                data.getSiblings().add(
                        Component.literal(
                                message.getMessage().getTime().format(DateTimeFormatter.ofPattern(ConfigStorage.General.TIME_FORMAT.config.getStringValue()))
                        ).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))
                );
            } catch (IllegalArgumentException e) {
                AdvancedChatLog.LOGGER.warn("Can't format time for context menu!", e);
            }
            if (message.getMessage().getOwner() != null) {
                data.getSiblings().add(Component.literal(" - ").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
                if (message.getMessage().getOwner().getEntry().getTabListDisplayName() != null) {
                    data.getSiblings().add(message.getMessage().getOwner().getEntry().getTabListDisplayName());
                } else {
                    data.getSiblings().add(Component.literal(message.getMessage().getOwner().getEntry().getProfile().name()));
                }
            }
            if (!data.getString().isBlank()) {
                actions.put(data, (x, y) -> {
                });
            }
            actions.put(Component.literal(StringUtils.translate("advancedchatlog.context.copy")), (x, y) -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(message.getMessage().getOriginalText().getString());
                InfoUtils.printActionbarMessage("advancedchatlog.context.copied");
            });
        }
        actions.put(Component.literal(StringUtils.translate("advancedchatlog.context.clearallmessages")), (x, y) -> {
            ChatLogData.getInstance().clear();
            setLines(ChatLogData.getInstance().getMessages());
        });
        menu = new ContextMenu(mouseX, mouseY, actions, () -> menu = null);
    }

    public Style getHoverStyle(double mouseX, double mouseY) {
        int lineHeight = this.fontHeight + 2;
        int height = this.mc.getWindow().getGuiScaledHeight();
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);

        // 26.2: StringSplitter/Font no longer expose getStyleAt; use the vanilla
        // ActiveTextCollector finder, feeding each visible line at its on-screen position.
        ActiveTextCollector.ClickableStyleFinder finder =
                new ActiveTextCollector.ClickableStyleFinder(this.font, (int) mouseX, (int) mouseY);

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            ChatMessage.AdvancedChatLine line = renderLines.get(i);
            int lineY = height - y - 40 - this.fontHeight;
            finder.accept(10, lineY, line.getText());
            y += lineHeight;
        }
        return finder.result();
    }

    public LogChatMessage getMessage(double mouseX, double mouseY) {
        int lineHeight = this.fontHeight + 2;
        int height = this.mc.getWindow().getGuiScaledHeight();
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);
        // Change the perspective of mouseY from where the text started.
        mouseY = height - mouseY - 40;

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            if (y <= mouseY && y + lineHeight >= mouseY) {
                ChatMessage.AdvancedChatLine line = renderLines.get(i);
                return ChatLogData.getInstance().getLogMessage(line.getParent());
            }
            y += lineHeight;
        }
        return null;
    }


}
