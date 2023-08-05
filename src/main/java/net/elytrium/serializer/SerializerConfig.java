/*
 * Copyright (C) 2023 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.serializer;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.elytrium.serializer.annotations.Serializer;
import net.elytrium.serializer.custom.ClassSerializer;
import net.elytrium.serializer.placeholders.PlaceholderReplacer;

public class SerializerConfig {

  public static final SerializerConfig DEFAULT = new SerializerConfig(
      new HashMap<>(),
      new HashMap<>(),
      NameStyle.CAMEL_CASE,
      NameStyle.KEBAB_CASE,
      false,
      false,
      System.lineSeparator()
  );

  private final Map<Class<? extends PlaceholderReplacer<?, ?>>, PlaceholderReplacer<?, ?>> cachedReplacers = new HashMap<>();
  private final Map<Class<? extends ClassSerializer<?, ?>>, ClassSerializer<?, ?>> cachedSerializers = new HashMap<>();
  private final Map<Class<?>, PlaceholderReplacer<?, ?>> registeredReplacers;
  private final Map<Class<?>, ClassSerializer<?, ?>> registeredSerializers;
  private final NameStyle fieldNameStyle;
  private final NameStyle nodeNameStyle;
  private final boolean safeMode;
  private final boolean allowUnicode;
  private final String lineSeparator;

  private SerializerConfig(Map<Class<?>, PlaceholderReplacer<?, ?>> registeredReplacers, Map<Class<?>, ClassSerializer<?, ?>> registeredSerializers,
      NameStyle fieldNameStyle, NameStyle nodeNameStyle, boolean safeMode, boolean allowUnicode, String lineSeparator) {
    this.registeredReplacers = registeredReplacers;
    this.registeredSerializers = registeredSerializers;
    this.fieldNameStyle = fieldNameStyle;
    this.nodeNameStyle = nodeNameStyle;
    this.safeMode = safeMode;
    this.allowUnicode = allowUnicode;
    this.lineSeparator = lineSeparator;
  }

  /**
   * Converts the class field name to the config node field format.
   */
  public String toNodeName(String field) {
    return this.toNodeName(field, null, null);
  }

  /**
   * Converts the class field name to the config node field format.
   */
  public String toNodeName(String field, NameStyle overriddenFieldNameStyle, NameStyle overriddenNodeNameStyle) {
    if (overriddenFieldNameStyle == null) {
      overriddenFieldNameStyle = this.fieldNameStyle;
    }

    if (overriddenNodeNameStyle == null) {
      overriddenNodeNameStyle = this.nodeNameStyle;
    }

    return !field.isEmpty() && Character.isDigit(field.charAt(0))
        ? this.toNodeName('"' + field + '"')
        : overriddenFieldNameStyle == overriddenNodeNameStyle ? field : overriddenNodeNameStyle.fromMacroCase(overriddenFieldNameStyle.toMacroCase(field));
  }

  /**
   * Converts the config node field to the class field name format.
   */
  public String toFieldName(String field) {
    return this.toFieldName(field, null, null);
  }

  /**
   * Converts the config node field to the class field name format.
   */
  public String toFieldName(String field, NameStyle overriddenFieldNameStyle, NameStyle overriddenNodeNameStyle) {
    if (overriddenFieldNameStyle == null) {
      overriddenFieldNameStyle = this.fieldNameStyle;
    }

    if (overriddenNodeNameStyle == null) {
      overriddenNodeNameStyle = this.nodeNameStyle;
    }

    return overriddenFieldNameStyle == overriddenNodeNameStyle
        ? field
        : overriddenFieldNameStyle.fromMacroCase(overriddenNodeNameStyle.toMacroCase(field));
  }

  public PlaceholderReplacer<?, ?> getAndCacheReplacer(Class<? extends PlaceholderReplacer<?, ?>> replacerClass) throws ReflectiveOperationException {
    PlaceholderReplacer<?, ?> replacer = this.cachedReplacers.get(replacerClass);
    if (replacer == null) {
      Constructor<? extends PlaceholderReplacer<?, ?>> constructor = replacerClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      replacer = constructor.newInstance();
      this.cachedReplacers.put(replacerClass, replacer);
    }

    return replacer;
  }

  @SuppressWarnings("unchecked")
  public <T, F> ClassSerializer<T, F> getAndCacheSerializer(Serializer serializer) throws ReflectiveOperationException {
    Class<? extends ClassSerializer<?, ?>> serializerClass = serializer.value();
    var configSerializer = (ClassSerializer<T, F>) this.cachedSerializers.get(serializerClass);
    if (configSerializer == null) {
      Constructor<? extends ClassSerializer<?, ?>> constructor = serializerClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      configSerializer = (ClassSerializer<T, F>) constructor.newInstance();
      this.cachedSerializers.put(serializerClass, configSerializer);
    }

    return configSerializer;
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public <T, P> PlaceholderReplacer<T, P> getRegisteredReplacer(Class<?> clazz) {
    while (clazz != null && clazz != Object.class) {
      var serializer = (PlaceholderReplacer<T, P>) this.registeredReplacers.get(clazz);
      if (serializer == null) {
        for (Class<?> classInterface : clazz.getInterfaces()) {
          serializer = (PlaceholderReplacer<T, P>) this.registeredReplacers.get(classInterface);
          if (serializer != null) {
            return serializer;
          }
        }

        clazz = clazz.getSuperclass();
      } else {
        return serializer;
      }
    }

    return (PlaceholderReplacer<T, P>) this.registeredReplacers.get(clazz);
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public <T, F> ClassSerializer<T, F> getRegisteredSerializer(Class<?> clazz) {
    while (clazz != null && clazz != Object.class) {
      var serializer = (ClassSerializer<T, F>) this.registeredSerializers.get(clazz);
      if (serializer == null) {
        for (Class<?> classInterface : clazz.getInterfaces()) {
          serializer = (ClassSerializer<T, F>) this.registeredSerializers.get(classInterface);
          if (serializer != null) {
            return serializer;
          }
        }

        clazz = clazz.getSuperclass();
      } else {
        return serializer;
      }
    }

    return (ClassSerializer<T, F>) this.registeredSerializers.get(clazz);
  }

  public int getRegisteredSerializers() {
    return this.cachedSerializers.size() + this.registeredSerializers.size();
  }

  public boolean isSafeMode() {
    return this.safeMode;
  }

  public boolean isAllowUnicode() {
    return this.allowUnicode;
  }

  public String getLineSeparator() {
    return this.lineSeparator;
  }

  public static class Builder {

    private final Map<Class<?>, PlaceholderReplacer<?, ?>> registeredReplacers = new HashMap<>();
    private final Map<Class<?>, ClassSerializer<?, ?>> registeredSerializers = new HashMap<>();

    private NameStyle fieldNameStyle = NameStyle.CAMEL_CASE;
    private NameStyle nodeNameStyle = NameStyle.KEBAB_CASE;
    private boolean safeMode = false;
    private boolean allowUnicode = false;
    private String lineSeparator = System.lineSeparator();

    public Builder registerSerializer(Collection<ClassSerializer<?, ?>> serializers) {
      serializers.forEach(serializer -> this.registeredSerializers.put(serializer.getToType(), serializer));
      return this;
    }

    public Builder registerSerializer(ClassSerializer<?, ?> serializer) {
      this.registeredSerializers.put(serializer.getToType(), serializer);
      return this;
    }

    public Builder registerReplacer(Collection<PlaceholderReplacer<?, ?>> replacers) {
      replacers.forEach(this::registerReplacer);
      return this;
    }

    public Builder registerReplacer(PlaceholderReplacer<?, ?> serializer) {
      Class<?> valueClass = null;
      for (Type interfaceType : serializer.getClass().getGenericInterfaces()) {
        if (interfaceType instanceof ParameterizedType type && type.getRawType() == PlaceholderReplacer.class) {
          Type placeholderType = type.getActualTypeArguments()[0];
          valueClass = placeholderType instanceof Class<?> clazz ? clazz : (Class<?>) ((ParameterizedType) placeholderType).getRawType();
        }
      }

      if (valueClass == null) {
        throw new IllegalStateException(
            "Failed to determine value class, please use SerializerConfig.Builder#registerPlaceholder(Class, PlaceholderReplacer)"
        );
      }

      return this.registerReplacer(valueClass, serializer);
    }

    public Builder registerReplacer(Map<Class<?>, PlaceholderReplacer<?, ?>> replacers) {
      replacers.forEach(this::registerReplacer);
      return this;
    }

    public Builder registerReplacer(Class<?> valueClass, PlaceholderReplacer<?, ?> serializer) {
      this.registeredReplacers.put(valueClass, serializer);
      return this;
    }

    public Builder setFieldNameStyle(NameStyle fieldNameStyle) {
      this.fieldNameStyle = fieldNameStyle;
      return this;
    }

    public Builder setNodeNameStyle(NameStyle nodeNameStyle) {
      this.nodeNameStyle = nodeNameStyle;
      return this;
    }

    public Builder setSafeMode(boolean safeMode) {
      this.safeMode = safeMode;
      return this;
    }

    public Builder setAllowUnicode(boolean allowUnicode) {
      this.allowUnicode = allowUnicode;
      return this;
    }

    public Builder setLineSeparator(String lineSeparator) {
      this.lineSeparator = lineSeparator;
      return this;
    }

    public SerializerConfig build() {
      return new SerializerConfig(
          this.registeredReplacers,
          this.registeredSerializers,
          this.fieldNameStyle,
          this.nodeNameStyle,
          this.safeMode,
          this.allowUnicode,
          this.lineSeparator
      );
    }
  }
}
