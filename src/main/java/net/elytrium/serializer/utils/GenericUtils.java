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

package net.elytrium.serializer.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public class GenericUtils {

  public static int getParameterIndex(TypeVariable<?>[] variables, TypeVariable<?> expected) {
    for (int i = 0; i < variables.length; ++i) {
      if (variables[i].getName().equals(expected.getName())) {
        return i;
      }
    }

    return -1;
  }

  public static Type getParameterTypeFromSuperclass(Class<?> parent, Type type, Type superclass, int searchIndex) {
    Type superclassType = GenericUtils.getParameterType(parent, superclass, searchIndex);
    if (superclassType instanceof TypeVariable<?> typeVariable && type instanceof ParameterizedType parameterizedType) {
      Class<?> rawSuperclass = (Class<?>) parameterizedType.getRawType();
      int index = GenericUtils.getParameterIndex(rawSuperclass.getTypeParameters(), typeVariable);
      if (index != -1) {
        return parameterizedType.getActualTypeArguments()[index];
      }
    }

    return superclassType;
  }

  public static Type getParameterType(Class<?> parent, Type type, int index) {
    if (type == null) {
      return null;
    }

    Class<?> clazz = null;

    if (type instanceof ParameterizedType parameterizedType) {
      clazz = (Class<?>) parameterizedType.getRawType();
      if (clazz.equals(parent)) {
        return parameterizedType.getActualTypeArguments()[index];
      }
    }

    if (type instanceof Class<?> typeClazz) {
      clazz = typeClazz;
    }

    if (clazz != null) {
      Type genericSuperclass = clazz.getGenericSuperclass();
      Type superclassType = GenericUtils.getParameterTypeFromSuperclass(parent, type, genericSuperclass, index);
      if (superclassType != null) {
        return superclassType;
      }

      for (Type genericInterface : clazz.getGenericInterfaces()) {
        Type genericInterfaceType = GenericUtils.getParameterTypeFromSuperclass(parent, type, genericInterface, index);
        if (genericInterfaceType != null) {
          return genericInterfaceType;
        }
      }
    }

    return null;
  }
}
