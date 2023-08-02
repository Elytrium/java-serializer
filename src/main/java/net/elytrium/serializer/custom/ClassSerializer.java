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

package net.elytrium.serializer.custom;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class ClassSerializer<T, F> {

  private final Class<T> toClass;
  private final Class<F> fromClass;

  @SuppressWarnings("unchecked")
  protected ClassSerializer() {
    Type[] actualTypeArguments = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments();
    this.toClass = actualTypeArguments[0] instanceof Class<?>
        ? (Class<T>) actualTypeArguments[0]
        : (Class<T>) ((ParameterizedType) actualTypeArguments[0]).getRawType();
    this.fromClass = actualTypeArguments[1] instanceof Class<?>
        ? (Class<F>) actualTypeArguments[1]
        : (Class<F>) ((ParameterizedType) actualTypeArguments[1]).getRawType();
  }

  protected ClassSerializer(Class<T> toClass, Class<F> fromClass) {
    this.toClass = toClass;
    this.fromClass = fromClass;
  }

  public F serialize(T from) {
    throw new UnsupportedOperationException();
  }

  public T deserialize(F from) {
    throw new UnsupportedOperationException();
  }

  public Class<T> getToType() {
    return this.toClass;
  }

  public Class<F> getFromType() {
    return this.fromClass;
  }
}
