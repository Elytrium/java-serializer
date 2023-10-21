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
import java.nio.file.Path;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.language.reader.AbstractReader;
import net.elytrium.serializer.language.reader.JsonReader;
import net.elytrium.serializer.language.writer.AbstractWriter;
import net.elytrium.serializer.language.writer.JsonWriter;

public class JsonSerializable extends AbstractSerializable {

  private String singleIndent;

  public JsonSerializable() {
  }

  public JsonSerializable(SerializerConfig config) {
    super(config);
  }

  public JsonSerializable(Path serializablePath, SerializerConfig config) {
    super(serializablePath, config);
  }

  @Override
  protected AbstractReader getReader(BufferedReader reader) {
    return new JsonReader(reader, this.getConfig());
  }

  @Override
  protected AbstractWriter getWriter(BufferedWriter writer) {
    JsonWriter jsonWriter = new JsonWriter(writer, this.getConfig());
    if (this.singleIndent != null) {
      jsonWriter.setSingleIndent(this.singleIndent);
    }

    return jsonWriter;
  }

  public void setSingleIndent(String singleIndent) {
    this.singleIndent = singleIndent;
  }
}
