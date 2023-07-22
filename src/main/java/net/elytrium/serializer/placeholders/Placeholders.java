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

  private static final Map<Integer, Placeholderable<?>> PLACEHOLDERS = new HashMap<>();

  @SuppressWarnings("unchecked")
  public static <T> T replace(T value, Object... values) {
    Placeholderable<T> placeholderable = (Placeholderable<T>) Placeholders.PLACEHOLDERS.get(System.identityHashCode(value));
    return placeholderable.replacer.replace(value, placeholderable.placeholders, values);
  }

  public static void addPlaceholders(Object value, PlaceholderReplacer<?> replacer, String... placeholders) {
    Placeholders.addPlaceholders(System.identityHashCode(value), replacer, placeholders);
  }

  public static void addPlaceholders(int hash, PlaceholderReplacer<?> replacer, String... placeholders) {
    Placeholders.PLACEHOLDERS.put(hash, new Placeholderable<>(replacer, placeholders));
  }

  public static void setPlaceholders(Object value, PlaceholderReplacer<?> fallbackReplacer, String... placeholders) {
    Placeholders.setPlaceholders(System.identityHashCode(value), fallbackReplacer, placeholders);
  }

  public static void setPlaceholders(int hash, PlaceholderReplacer<?> fallbackReplacer, String... placeholders) {
    Placeholderable<?> placeholderable = Placeholders.PLACEHOLDERS.get(hash);
    if (placeholderable == null) {
      Placeholders.addPlaceholders(hash, fallbackReplacer, placeholders);
    } else {
      placeholderable.setPlaceholders(placeholders);
    }
  }

  public static void removePlaceholders(Object value) {
    Placeholders.removePlaceholders(System.identityHashCode(value));
  }

  public static void removePlaceholders(int hash) {
    Placeholders.PLACEHOLDERS.remove(hash);
  }

  public static boolean hasPlaceholders(Object value) {
    return Placeholders.hasPlaceholders(System.identityHashCode(value));
  }

  public static boolean hasPlaceholders(int hash) {
    return Placeholders.PLACEHOLDERS.containsKey(hash);
  }

  public static String[] getPlaceholders(Object value) {
    return Placeholders.getPlaceholders(System.identityHashCode(value));
  }

  public static String[] getPlaceholders(int hash) {
    Placeholderable<?> placeholderable = Placeholders.PLACEHOLDERS.get(hash);
    if (placeholderable == null) {
      throw new IllegalStateException("Invalid input!");
    } else {
      return placeholderable.placeholders;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> PlaceholderReplacer<T> getReplacer(T value) {
    return (PlaceholderReplacer<T>) Placeholders.getReplacer(System.identityHashCode(value));
  }

  public static PlaceholderReplacer<?> getReplacer(int hash) {
    Placeholderable<?> placeholderable = Placeholders.PLACEHOLDERS.get(hash);
    if (placeholderable == null) {
      throw new IllegalStateException("Invalid input!");
    } else {
      return placeholderable.replacer;
    }
  }

  private static class Placeholderable<T> {

    private static final ThreadLocal<Matcher> EXACTLY_MATCHES;
    private static final ThreadLocal<Matcher> LOWERCASE;
    private static final ThreadLocal<Matcher> UPPERCASE;

    private final PlaceholderReplacer<T> replacer;
    private String[] placeholders;

    public Placeholderable(PlaceholderReplacer<T> replacer, String[] placeholders) {
      this.replacer = replacer;
      this.setPlaceholders(placeholders);
    }

    public void setPlaceholders(String[] placeholders) {
      this.placeholders = Stream.of(placeholders).map(Placeholders.Placeholderable::toPlaceholderName).toArray(String[]::new);
    }

    static {
      Pattern exactlyMatches = Pattern.compile("^\\{(?!_)[A-Z\\d_]+(?<!_)}$");
      EXACTLY_MATCHES = ThreadLocal.withInitial(() -> exactlyMatches.matcher(""));
      Pattern lowercase = Pattern.compile("^(?!-)[a-z\\d-]+(?<!-)$");
      LOWERCASE = ThreadLocal.withInitial(() -> lowercase.matcher(""));
      Pattern uppercase = Pattern.compile("^(?!_)[A-Z\\d_]+(?<!_)$");
      UPPERCASE = ThreadLocal.withInitial(() -> uppercase.matcher(""));
    }

    private static String toPlaceholderName(String name) {
      if (Placeholders.Placeholderable.EXACTLY_MATCHES.get().reset(name).matches()) {
        return name;
      } else if (Placeholders.Placeholderable.LOWERCASE.get().reset(name).matches()) {
        return '{' + name.toUpperCase(Locale.ROOT).replace('-', '_') + '}';
      } else if (Placeholders.Placeholderable.UPPERCASE.get().reset(name).matches()) {
        return '{' + name + '}';
      } else {
        throw new IllegalStateException("Invalid placeholder: " + name);
      }
    }
  }
}
