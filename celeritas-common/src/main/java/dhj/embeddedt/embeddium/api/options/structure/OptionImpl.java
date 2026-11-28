package dhj.embeddedt.embeddium.api.options.structure;

import dhj.embeddedt.embeddium.api.options.binding.GenericBinding;
import dhj.embeddedt.embeddium.api.options.binding.OptionBinding;
import dhj.embeddedt.embeddium.api.options.control.Control;
import dhj.embeddedt.embeddium.api.options.OptionIdentifier;
import dhj.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public class OptionImpl<S, T> implements Option<T> {

    private final OptionStorage<S> storage;

    private final OptionBinding<S, T> binding;
    private final Control<T> control;

    private final EnumSet<OptionFlag> flags;

    private final OptionIdentifier<T> id;
    private final TextComponent name;
    private final TextComponent tooltip;

    private final OptionImpact impact;

    /**
     * The new value the user wants to provide, or null if they have not changed the option yet.
     */
    private @Nullable T modifiedValue;

    private @Nullable Supplier<T> defaultValue;
    private @Nullable T disabledDisplayValue;

    private final BooleanSupplier enabled;

    private OptionImpl(OptionStorage<S> storage,
                       OptionIdentifier<T> id,
                       TextComponent name,
                       TextComponent tooltip,
                       OptionBinding<S, T> binding,
                       Function<OptionImpl<S, T>, Control<T>> control,
                       EnumSet<OptionFlag> flags,
                       OptionImpact impact,
                       BooleanSupplier enabled) {
        this.id = id;
        this.storage = storage;
        this.name = name;
        this.tooltip = tooltip;
        this.binding = binding;
        this.impact = impact;
        this.flags = flags;
        this.control = control.apply(this);
        this.enabled = enabled;

        this.reset();
    }

    @Override
    public OptionIdentifier<T> getId() {
        return id;
    }

    @Override
    public TextComponent getName() {
        return this.name;
    }

    @Override
    public TextComponent getTooltip() {
        return this.tooltip;
    }

    @Override
    public OptionImpact getImpact() {
        return this.impact;
    }

    @Override
    public Control<T> getControl() {
        return this.control;
    }

    @Override
    public T getValue() {
        if (this.disabledDisplayValue != null && !this.isAvailable()) {
            return this.disabledDisplayValue;
        }
        return this.modifiedValue != null ? this.modifiedValue : this.binding.getValue(this.storage.getData());
    }

    @Override
    public void setValue(T value) {
        this.modifiedValue = value;
    }

    @Override
    public void reset() {
        this.modifiedValue = null;
    }

    @Override
    public T getAppliedValue() {
        return this.binding.getValue(this.storage.getData());
    }

    @Override
    public T getDefaultValue() {
        return this.defaultValue != null ? this.defaultValue.get() : this.getAppliedValue();
    }

    @Override
    public void resetToDefault() {
        this.setValue(this.getDefaultValue());
    }

    @Override
    public OptionStorage<?> getStorage() {
        return this.storage;
    }

    @Override
    public boolean isAvailable() {
        return this.enabled.getAsBoolean();
    }

    @Override
    public boolean hasChanged() {
        return this.modifiedValue != null && !this.binding.getValue(this.storage.getData()).equals(this.modifiedValue);
    }

    @Override
    public void applyChanges() {
        if (this.modifiedValue != null) {
            this.binding.setValue(this.storage.getData(), this.modifiedValue);
            this.modifiedValue = null;
        }
    }

    @Override
    public Collection<OptionFlag> getFlags() {
        return this.flags;
    }

    public static <S, T> OptionImpl.Builder<S, T> createBuilder(@SuppressWarnings("unused") Class<T> type, OptionStorage<S> storage) {
        return new Builder<>(storage, type);
    }

    public static class Builder<S, T> {
        private final OptionStorage<S> storage;
        private final Class<T> type;
        private OptionIdentifier<T> id;
        private TextComponent name;
        private TextComponent tooltip;
        private OptionBinding<S, T> binding;
        private Function<OptionImpl<S, T>, Control<T>> control;
        private OptionImpact impact;
        private final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
        private static final BooleanSupplier ALWAYS_ENABLED = () -> true;
        private static final BooleanSupplier ALWAYS_DISABLED = () -> false;
        private BooleanSupplier enabled = ALWAYS_ENABLED;
        private Supplier<T> defaultValue;
        private T disabledDisplayValue;

        private Builder(OptionStorage<S> storage, Class<T> type) {
            this.storage = storage;
            this.type = type;
        }

        public Builder<S, T> setId(OptionIdentifier<T> id) {
            Objects.requireNonNull(id, "Id must not be null");

            this.id = id;

            return this;
        }

        public Builder<S, T> setName(TextComponent name) {
            Objects.requireNonNull(name, "Argument must not be null");

            this.name = name;

            return this;
        }

        public Builder<S, T> setTooltip(TextComponent tooltip) {
            Objects.requireNonNull(tooltip, "Argument must not be null");

            this.tooltip = tooltip;

            return this;
        }

        public Builder<S, T> setBinding(BiConsumer<S, T> setter, Function<S, T> getter) {
            Objects.requireNonNull(setter, "Setter must not be null");
            Objects.requireNonNull(getter, "Getter must not be null");

            this.binding = new GenericBinding<>(setter, getter);

            return this;
        }


        public Builder<S, T> setBinding(OptionBinding<S, T> binding) {
            Objects.requireNonNull(binding, "Argument must not be null");

            this.binding = binding;

            return this;
        }

        public Builder<S, T> setControl(Function<OptionImpl<S, T>, Control<T>> control) {
            Objects.requireNonNull(control, "Argument must not be null");

            this.control = control;

            return this;
        }

        public Builder<S, T> setImpact(OptionImpact impact) {
            this.impact = impact;

            return this;
        }

        public Builder<S, T> setDefaultValue(T defaultValue) {
            this.defaultValue = defaultValue == null ? null : () -> defaultValue;

            return this;
        }

        /** Defers capability checks until the options screen has a current graphics context. */
        public Builder<S, T> setDefaultValueSupplier(Supplier<T> source) {
            this.defaultValue = Objects.requireNonNull(source);

            return this;
        }

        public Builder<S, T> setEnabledPredicate(BooleanSupplier value) {
            this.enabled = value;

            return this;
        }

        /** Shows the effective mode while a prerequisite is disabled, preserving the stored preference. */
        public Builder<S, T> setDisabledDisplayValue(T value) {
            this.disabledDisplayValue = Objects.requireNonNull(value);
            return this;
        }

        public Builder<S, T> setEnabled(boolean value) {
            setEnabledPredicate(value ? ALWAYS_ENABLED : ALWAYS_DISABLED);

            return this;
        }

        public Builder<S, T> setFlags(OptionFlag... flags) {
            Collections.addAll(this.flags, flags);

            return this;
        }

        public OptionImpl<S, T> build() {
            if (this.id == null) {
                // FIXME enforce IDs and make nullable
                this.id = (OptionIdentifier<T>)OptionIdentifier.EMPTY;
            } else {
                if (this.name == null) {
                    this.name = TextComponent.translatable(this.id.getModId() + ".options." + this.id.getPath() + ".name");
                }

                if (this.tooltip == null) {
                    this.tooltip = TextComponent.translatable(this.id.getModId() + ".options." + this.id.getPath() + ".tooltip");
                }
            }

            Objects.requireNonNull(this.name, "Name must be specified or inferred from a specified ID");
            Objects.requireNonNull(this.tooltip, "Tooltip must be specified or inferred from a specified ID");
            Objects.requireNonNull(this.binding, "Option binding must be specified");
            Objects.requireNonNull(this.control, "Control must be specified");

            OptionImpl<S, T> impl = new OptionImpl<>(this.storage, this.id, this.name, this.tooltip, this.binding, this.control, this.flags, this.impact, this.enabled);
            impl.defaultValue = this.defaultValue;
            impl.disabledDisplayValue = this.disabledDisplayValue;
            return impl;
        }
    }
}
