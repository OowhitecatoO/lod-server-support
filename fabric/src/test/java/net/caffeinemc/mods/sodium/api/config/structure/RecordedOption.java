package net.caffeinemc.mods.sodium.api.config.structure;

import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** RECORDING base for the two stateful builders LSS uses. */
public abstract class RecordedOption<V> implements StatefulOptionBuilder<V> {
    public final ResourceLocation id;
    public Component name;
    public Component tooltip;
    public OptionImpact impact;
    public V defaultValue;
    public Consumer<V> setter;
    public Supplier<V> getter;
    public StorageEventHandler storageHandler;
    public Function<ConfigState, Boolean> enabledProvider;
    public ResourceLocation[] dependencies = new ResourceLocation[0];

    RecordedOption(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public StatefulOptionBuilder<V> setName(Component name) {
        this.name = name;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setTooltip(Component tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public OptionBuilder setEnabledProvider(Function<ConfigState, Boolean> provider, ResourceLocation... dependencies) {
        this.enabledProvider = provider;
        this.dependencies = dependencies;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setStorageHandler(StorageEventHandler handler) {
        this.storageHandler = handler;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setImpact(OptionImpact impact) {
        this.impact = impact;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setDefaultValue(V value) {
        this.defaultValue = value;
        return this;
    }

    @Override
    public StatefulOptionBuilder<V> setBinding(Consumer<V> setter, Supplier<V> getter) {
        this.setter = setter;
        this.getter = getter;
        return this;
    }
}
