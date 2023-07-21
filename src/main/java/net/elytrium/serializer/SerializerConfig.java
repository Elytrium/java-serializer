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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.elytrium.serializer.annotations.Serializer;
import net.elytrium.serializer.custom.ClassSerializer;
import net.elytrium.serializer.custom.ClassSerializerCollection;
import net.elytrium.serializer.placeholders.PlaceholderReplacer;

public class SerializerConfig {

  public static final SerializerConfig DEFAULT = new SerializerConfig(new HashMap<>(), NameStyle.CAMEL_CASE,
      NameStyle.KEBAB_CASE, false, false, System.lineSeparator());

  private final Map<Class<? extends ClassSerializer<?, ?>>, ClassSerializer<?, ?>> cachedSerializers = new HashMap<>();
  private final Map<Class<?>, ClassSerializer<?, ?>> registeredSerializers;
  private final Map<Class<? extends PlaceholderReplacer<?>>, PlaceholderReplacer<?>> cachedReplacers = new HashMap<>();
  private final NameStyle fieldNameStyle;
  private final NameStyle nodeNameStyle;
  private final boolean safeMode;
  private final boolean allowUnicode;
  private final String lineSeparator;

  private SerializerConfig(Map<Class<?>, ClassSerializer<?, ?>> registeredSerializers, NameStyle fieldNameStyle,
                           NameStyle nodeNameStyle, boolean safeMode, boolean allowUnicode, String lineSeparator) {
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
    return field.length() > 0 && Character.isDigit(field.charAt(0))
        ? this.toNodeName('"' + field + '"')
        : this.fieldNameStyle == this.nodeNameStyle ? field : this.nodeNameStyle.fromMacroCase(this.fieldNameStyle.toMacroCase(field));
  }

  /**
   * Converts the config node field to the class field name format.
   */
  public String toFieldName(String field) {
    return this.fieldNameStyle == this.nodeNameStyle ? field : this.fieldNameStyle.fromMacroCase(this.nodeNameStyle.toMacroCase(field));
  }

  public ClassSerializer<?, ?> getAndCacheSerializer(Serializer serializer) throws ReflectiveOperationException {
    Class<? extends ClassSerializer<?, ?>> serializerClass = serializer.value();
    ClassSerializer<?, ?> configSerializer = this.cachedSerializers.get(serializerClass);
    if (configSerializer == null) {
      configSerializer = serializerClass.getDeclaredConstructor().newInstance();
      this.cachedSerializers.put(serializerClass, configSerializer);
    }

    return configSerializer;
  }

  public PlaceholderReplacer<?> getAndCacheReplacer(Class<? extends PlaceholderReplacer<?>> replacerClass) throws ReflectiveOperationException {
    PlaceholderReplacer<?> replacer = this.cachedReplacers.get(replacerClass);
    if (replacer == null) {
      replacer = replacerClass.getDeclaredConstructor().newInstance();
      this.cachedReplacers.put(replacerClass, replacer);
    }

    return replacer;
  }

  @Nullable
  public ClassSerializer<?, ?> getRegisteredSerializer(Class<?> to) {
    while (to != null && to != Object.class) {
      ClassSerializer<?, ?> serializer = this.registeredSerializers.get(to);
      if (serializer == null) {
        for (Class<?> classInterface : to.getInterfaces()) {
          serializer = this.registeredSerializers.get(classInterface);
          if (serializer != null) {
            return serializer;
          }
        }

        to = to.getSuperclass();
      } else {
        return serializer;
      }
    }

    return this.registeredSerializers.get(to);
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

    private final Map<Class<?>, ClassSerializer<?, ?>> registeredSerializers = new HashMap<>();

    private NameStyle fieldNameStyle = NameStyle.CAMEL_CASE;
    private NameStyle nodeNameStyle = NameStyle.KEBAB_CASE;
    private boolean safeMode = false;
    private boolean allowUnicode = false;
    private String lineSeparator = "\n";

    public Builder registerSerializer(ClassSerializer<?, ?> serializer) {
      this.registeredSerializers.put(serializer.getToType(), serializer);
      return this;
    }

    public Builder registerSerializer(ClassSerializerCollection serializers) {
      serializers.serializers().forEach(serializer -> this.registeredSerializers.put(serializer.getToType(), serializer));
      return this;
    }

    public Builder registerSerializer(Collection<ClassSerializer<?, ?>> serializers) {
      serializers.forEach(serializer -> this.registeredSerializers.put(serializer.getToType(), serializer));
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

    public Builder setLineSeparator(String lineSeparator) {
      this.lineSeparator = lineSeparator;
      return this;
    }

    public Builder setAllowUnicode(boolean allowUnicode) {
      this.allowUnicode = allowUnicode;
      return this;
    }

    public SerializerConfig build() {
      return new SerializerConfig(this.registeredSerializers, this.fieldNameStyle, this.nodeNameStyle,
          this.safeMode, this.allowUnicode, this.lineSeparator);
    }
  }
}
