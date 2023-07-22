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

package net.elytrium.serializer.language.reader;

import java.io.BufferedReader;
import net.elytrium.serializer.SerializerConfig;

public class JsonReader extends YamlReader {

  public JsonReader(BufferedReader reader, SerializerConfig config) {
    super(reader, config);
  }

  public JsonReader(BufferedReader reader) {
    super(reader);
  }

  @Override
  public boolean skipComments(char marker, boolean reuse) {
    synchronized (this) {
      if (marker == '/') {
        if (this.readRaw() == '/') {
          while (true) {
            if (this.readRaw() == AbstractReader.NEW_LINE) {
              break;
            }
          }

          return true;
        } else {
          this.setReuseBuffer();
        }
      }

      // See YamlReader#skipComments(char, boolean).
      if (reuse) {
        this.setReuseBuffer();
      }

      return false;
    }
  }
}
