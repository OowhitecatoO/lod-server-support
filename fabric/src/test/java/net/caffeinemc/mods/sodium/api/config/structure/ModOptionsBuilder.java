package net.caffeinemc.mods.sodium.api.config.structure;

import net.minecraft.resources.ResourceLocation;

public interface ModOptionsBuilder {
    ModOptionsBuilder setIcon(ResourceLocation icon);

    ModOptionsBuilder addPage(PageBuilder page);
}
