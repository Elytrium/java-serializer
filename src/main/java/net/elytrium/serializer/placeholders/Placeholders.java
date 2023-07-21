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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Placeholders {

  private static final Matcher EXACTLY_MATCHES = Pattern.compile("^\\{(?!_)[A-Z\\d_]+(?<!_)}$").matcher("");
  private static final Matcher LOWERCASE = Pattern.compile("^(?!-)[a-z\\d-]+(?<!-)$").matcher("");
  private static final Matcher UPPERCASE = Pattern.compile("^(?!_)[A-Z\\d_]+(?<!_)$").matcher("");

  private static final Map<Integer, String[]> PLACEHOLDERS = new HashMap<>();
  private static final Map<Integer, PlaceholderReplacer<?>> REPLACERS = new HashMap<>();

  public static <T> T replace(T value, Object... values) {
    String[] placeholders = Placeholders.getPlaceholders(value);
    PlaceholderReplacer<T> replacer = Placeholders.getReplacer(value);

    return replacer.replace(value, placeholders, values);
  }

  public static void addPlaceholders(Object value, PlaceholderReplacer<?> replacer, String... placeholders) {
    Placeholders.addPlaceholders(System.identityHashCode(value), replacer, placeholders);
  }

  public static void addPlaceholders(int hash, PlaceholderReplacer<?> replacer, String... placeholders) {
    PLACEHOLDERS.put(hash, Stream.of(placeholders).map(Placeholders::toPlaceholderName).toArray(String[]::new));
    REPLACERS.put(hash, replacer);
  }

  public static void setPlaceholders(Object value, String... placeholders) {
    Placeholders.setPlaceholders(System.identityHashCode(value), placeholders);
  }

  public static void setPlaceholders(int hash, String... placeholders) {
    PLACEHOLDERS.put(hash, Stream.of(placeholders).map(Placeholders::toPlaceholderName).toArray(String[]::new));
  }

  public static void removePlaceholders(Object value) {
    Placeholders.removePlaceholders(System.identityHashCode(value));
  }

  public static void removePlaceholders(int hash) {
    PLACEHOLDERS.remove(hash);
    REPLACERS.remove(hash);
  }

  public static boolean hasPlaceholders(Object value) {
    return Placeholders.hasPlaceholders(System.identityHashCode(value));
  }

  public static boolean hasPlaceholders(int hash) {
    return PLACEHOLDERS.containsKey(hash);
  }

  public static String[] getPlaceholders(Object value) {
    return Placeholders.getPlaceholders(System.identityHashCode(value));
  }

  public static String[] getPlaceholders(int hash) {
    String[] placeholders = PLACEHOLDERS.get(hash);
    if (placeholders == null) {
      throw new IllegalStateException("Invalid input");
    } else {
      return placeholders;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> PlaceholderReplacer<T> getReplacer(T value) {
    return (PlaceholderReplacer<T>) Placeholders.getReplacer(System.identityHashCode(value));
  }

  public static PlaceholderReplacer<?> getReplacer(int hash) {
    PlaceholderReplacer<?> placeholders = REPLACERS.get(hash);
    if (placeholders == null) {
      throw new IllegalStateException("Invalid input");
    } else {
      return placeholders;
    }
  }

  private static String toPlaceholderName(String name) {
    if (EXACTLY_MATCHES.reset(name).matches()) {
      return name;
    } else if (LOWERCASE.reset(name).matches()) {
      return '{' + name.toUpperCase(Locale.ROOT).replace('-', '_') + '}';
    } else if (UPPERCASE.reset(name).matches()) {
      return '{' + name + '}';
    } else {
      throw new IllegalStateException("Invalid placeholder: " + name);
    }
  }
}
