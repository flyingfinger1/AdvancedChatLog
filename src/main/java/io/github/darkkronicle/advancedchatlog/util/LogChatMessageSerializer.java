/*
 * Copyright (C) 2021-2025 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatlog.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchatcore.interfaces.IJsonSave;
import io.github.darkkronicle.advancedchatlog.config.ChatLogConfigStorage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

@Environment(EnvType.CLIENT)
public class LogChatMessageSerializer implements IJsonSave<LogChatMessage> {

    private DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public LogChatMessageSerializer() {}

    /**
     * 26.2: Component is no longer (de)serialised through a registered Gson {@code TypeAdapter}.
     * The canonical path is {@link ComponentSerialization#CODEC} together with
     * {@link JsonOps#INSTANCE}, which yields/consumes a Gson {@link JsonElement}.
     */
    private static JsonElement textToJson(Component text) {
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, text).getOrThrow();
    }

    private static Component jsonToText(JsonElement element) {
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
    }

    private Style cleanStyle(Style style) {
        if (!ChatLogConfigStorage.General.CLEAN_SAVE.config.getBooleanValue()) {
            return style;
        }
        style = style.withClickEvent(null);
        style = style.withHoverEvent(null);
        style = style.withInsertion(null);
        return style;
    }

    private Component transfer(Component text) {
        // Using the built in serializer LiteralText is required
        MutableComponent base = Component.empty();
        for (Component t : text.getSiblings()) {
            MutableComponent newT = Component.literal(t.getString()).withStyle(cleanStyle(t.getStyle()));
            base.getSiblings().add(newT);
        }
        return base;
    }

    @Override
    public LogChatMessage load(JsonObject obj) {
        LocalDateTime dateTime = LocalDateTime.from(formatter.parse(obj.get("time").getAsString()));
        LocalDate date = dateTime.toLocalDate();
        LocalTime time = dateTime.toLocalTime();
        Component display = jsonToText(obj.get("display"));
        Component original = jsonToText(obj.get("original"));
        int stacks = obj.get("stacks").getAsByte();
        ChatMessage message =
                ChatMessage.builder()
                        .time(time)
                        .displayText(display)
                        .originalText(original)
                        .build();
        LogChatMessage log = new LogChatMessage(message, date);
        return log;
    }

    @Override
    public JsonObject save(LogChatMessage message) {
        JsonObject json = new JsonObject();
        ChatMessage chat = message.getMessage();
        LocalDateTime dateTime = LocalDateTime.of(message.getDate(), chat.getTime());
        json.addProperty("time", formatter.format(dateTime));
        json.addProperty("stacks", chat.getStacks());
        json.add("display", textToJson(transfer(chat.getDisplayText())));
        json.add("original", textToJson(transfer(chat.getOriginalText())));
        return json;
    }
}
