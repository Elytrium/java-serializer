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

import java.util.Locale;
import java.util.function.Function;

/**
 * kebab-case
 * camelCase
 * CapitalCamelCase
 * snake_case
 * MACRO_CASE
 * COBOL-CASE
 */
public enum NameStyle {

  /**
   * kebab-case
   */
  KEBAB_CASE(value -> value.replace("_", "-").toLowerCase(Locale.ROOT), value -> value.replace("-", "_").toUpperCase(Locale.ROOT)),
  /**
   * camelCase
   */
  CAMEL_CASE(value -> NameStyle.toCamelCase(value, false), value -> {
    StringBuilder result = new StringBuilder();
    char previous = 0;
    for (char character : value.toCharArray()) {
      if ((Character.isUpperCase(character) && Character.isLowerCase(previous))
          || (Character.isAlphabetic(character) && Character.isDigit(previous))
          || (Character.isDigit(character) && Character.isAlphabetic(previous))) {
        result.append('_');
      }

      result.append(Character.toUpperCase(character));
      previous = character;
    }

    return result.toString();
  }),
  /**
   * CapitalCamelCase
   */
  CAPITAL_CAMEL_CASE(value -> NameStyle.toCamelCase(value, true), CAMEL_CASE.toMacroCase),
  /**
   * snake_case
   */
  SNAKE_CASE(value -> value.toLowerCase(Locale.ROOT), value -> value.toUpperCase(Locale.ROOT)),
  /**
   * MACRO_CASE
   */
  MACRO_CASE(value -> value, value -> value),
  /**
   * COBOL-CASE
   */
  COBOL_CASE(value -> value.replace("_", "-"), value -> value.replace("-", "_"));

  private final Function<String, String> fromMacroCase;
  private final Function<String, String> toMacroCase;

  NameStyle(Function<String, String> fromMacroCase, Function<String, String> toMacroCase) {
    this.fromMacroCase = fromMacroCase;
    this.toMacroCase = toMacroCase;
  }

  public String fromMacroCase(String fieldName) {
    return this.fromMacroCase.apply(fieldName);
  }

  public String toMacroCase(String fieldName) {
    return this.toMacroCase.apply(fieldName);
  }

  private static String toCamelCase(String value, boolean nextCharUppercase) {
    StringBuilder result = new StringBuilder();
    for (char character : value.toCharArray()) {
      if (character == '_') {
        nextCharUppercase = true;
      } else {
        result.append(nextCharUppercase ? character : Character.toLowerCase(character));
        nextCharUppercase = false;
      }
    }

    return result.toString();
  }
}
