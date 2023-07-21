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

package net.elytrium.serializer.placeholders;

public class StringPlaceholderReplacer implements PlaceholderReplacer<String> {

  @Override
  public String replace(String value, String[] placeholders, Object... values) {
    for (int i = Math.min(values.length, placeholders.length) - 1; i >= 0; --i) {
      value = value.replace(placeholders[i], String.valueOf(values[i]));
    }

    return value;
  }
}
