/*
 * Copyright (C) 2021 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatlog.gui;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;

public class TextFieldRunnable extends GuiTextFieldGeneric {

    private final Consumer<TextFieldRunnable> onApply;

    public TextFieldRunnable(
            int x,
            int y,
            int width,
            int height,
            Font textRenderer,
            Consumer<TextFieldRunnable> onApply) {
        super(x, y, width, height, textRenderer);
        this.onApply = onApply;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (super.keyPressed(keyEvent)) {
            return true;
        }
        if (keyEvent.input() == InputConstants.KEY_RETURN) {
            onApply.accept(this);
            return true;
        }
        return false;
    }
}
