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

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class Placeholders {

  private static final Map<Integer, Placeholderable<?, ?>> PLACEHOLDERS = new HashMap<>();

  public static <T, P, R> R replace(T value, Object... values) {
    return Placeholders.replaceFor(value, value, values);
  }

  public static <H, T, P, R> R replaceFor(H holder, T value, Object... values) {
    var placeholderable = (Placeholderable<T, P>) Placeholders.PLACEHOLDERS.get(System.identityHashCode(holder));
    if (holder instanceof Collection<?> collection) {
      List<T> list = new ArrayList<>(collection.size());
      for (Object entry : collection) {
        list.add(placeholderable.replacer.replace((T) entry, placeholderable.placeholders, values));
      }

      return (R) list;
    } else {
      return (R) placeholderable.replacer.replace(value, placeholderable.placeholders, values);
    }
  }

  public static void addPlaceholders(Object value, PlaceholderReplacer<?, ?> replacer, String... placeholders) {
    Placeholders.addPlaceholders(value, replacer, true, placeholders);
  }

  public static void addPlaceholders(Object value, PlaceholderReplacer<?, ?> replacer, boolean wrapWithBraces, String... placeholders) {
    Placeholders.addPlaceholders(System.identityHashCode(value), replacer, wrapWithBraces, placeholders);
  }

  public static void addPlaceholders(int hash, PlaceholderReplacer<?, ?> replacer, String... placeholders) {
    Placeholders.addPlaceholders(hash, replacer, true, placeholders);
  }

  public static void addPlaceholders(int hash, PlaceholderReplacer<?, ?> replacer, boolean wrapWithBraces, String... placeholders) {
    Placeholders.PLACEHOLDERS.put(hash, new Placeholderable<>(replacer, placeholders, wrapWithBraces));
  }

  public static void setPlaceholders(Object value, PlaceholderReplacer<?, ?> fallbackReplacer, String... placeholders) {
    Placeholders.setPlaceholders(System.identityHashCode(value), fallbackReplacer, placeholders);
  }

  public static void setPlaceholders(int hash, PlaceholderReplacer<?, ?> fallbackReplacer, String... placeholders) {
    Placeholders.setPlaceholders(hash, fallbackReplacer, true, placeholders);
  }

  public static void setPlaceholders(int hash, PlaceholderReplacer<?, ?> fallbackReplacer, boolean wrapWithBraces, String... placeholders) {
    Placeholderable<?, ?> placeholderable = Placeholders.PLACEHOLDERS.get(hash);
    if (placeholderable == null) {
      if (fallbackReplacer != null) {
        Placeholders.addPlaceholders(hash, fallbackReplacer, placeholders);
      }
    } else {
      placeholderable.setPlaceholders(placeholders, wrapWithBraces);
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

  public static <P> P[] getPlaceholders(Object value) {
    return Placeholders.getPlaceholders(System.identityHashCode(value));
  }

  public static <P> P[] getPlaceholders(int hash) {
    var placeholderable = (Placeholderable<?, P>) Placeholders.PLACEHOLDERS.get(hash);
    if (placeholderable == null) {
      throw new IllegalStateException("Invalid input!");
    } else {
      return placeholderable.placeholders;
    }
  }

  public static <T> PlaceholderReplacer<T, ?> getReplacer(T value) {
    return Placeholders.getReplacer(System.identityHashCode(value));
  }

  @SuppressWarnings("unchecked")
  public static <T> PlaceholderReplacer<T, ?> getReplacer(int hash) {
    var placeholderable = (Placeholderable<T, ?>) Placeholders.PLACEHOLDERS.get(hash);
    if (placeholderable == null) {
      throw new IllegalStateException("Invalid input!");
    } else {
      return placeholderable.replacer;
    }
  }

  private static class Placeholderable<T, P> {

    private final PlaceholderReplacer<T, P> replacer;
    private final Class<P> placeholdersClass;
    private P[] placeholders;

    private Placeholderable(PlaceholderReplacer<T, P> replacer, String[] placeholders, boolean wrapWithBraces) {
      this.replacer = replacer;
      this.placeholdersClass = this.determinePlaceholdersClass();
      this.setPlaceholders(placeholders, wrapWithBraces);
    }

    // https://cdn.discordapp.com/attachments/593589868777439233/1135283936726106153/3.png
    private Class<P> determinePlaceholdersClass() {
      for (Type interfaceType : this.replacer.getClass().getGenericInterfaces()) {
        if (interfaceType instanceof ParameterizedType type && type.getRawType() == PlaceholderReplacer.class) {
          Type placeholderType = type.getActualTypeArguments()[1];
          return placeholderType instanceof Class<?> clazz ? (Class<P>) clazz : (Class<P>) ((ParameterizedType) placeholderType).getRawType();
        }
      }

      throw new IllegalStateException();
    }

    private void setPlaceholders(String[] placeholders, boolean wrapWithBraces) {
      this.placeholders = Stream.of(placeholders)
          .map(placeholder -> wrapWithBraces && !(placeholder.startsWith("{") && placeholder.endsWith("}")) ? '{' + placeholder + '}' : placeholder)
          .toArray(length -> (P[]) Array.newInstance(this.placeholdersClass, length));
    }
  }
}
