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

package net.elytrium.serializer.language.object;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.language.reader.AbstractReader;
import net.elytrium.serializer.language.reader.YamlReader;
import net.elytrium.serializer.language.writer.AbstractWriter;
import net.elytrium.serializer.language.writer.YamlWriter;

public class YamlSerializable extends AbstractSerializable {

  private final Map<Field, YamlWriter.StringStyle> stringStyleMap = new HashMap<>();

  private String singleIndent;

  public YamlSerializable() {
    super();
  }

  public YamlSerializable(SerializerConfig config) {
    super(config);
  }

  public YamlSerializable(Path serializablePath, SerializerConfig config) {
    super(serializablePath, config);
  }

  @Override
  protected AbstractReader getReader(BufferedReader reader) {
    return new YamlReader(reader, this.getConfig(), this);
  }

  @Override
  protected AbstractWriter getWriter(BufferedWriter writer) {
    YamlWriter yamlWriter = new YamlWriter(this.getConfig(), writer, this);
    if (this.singleIndent != null) {
      yamlWriter.setSingleIndent(this.singleIndent);
    }

    return yamlWriter;
  }

  public void setSingleIndent(String singleIndent) {
    this.singleIndent = singleIndent;
  }

  public void saveStringStyle(Field field, YamlWriter.StringStyle style) {
    this.stringStyleMap.put(field, style);
  }

  public YamlWriter.StringStyle getStringStyle(Field field) {
    return this.stringStyleMap.get(field);
  }
}
