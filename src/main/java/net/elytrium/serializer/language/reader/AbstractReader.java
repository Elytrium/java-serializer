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
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Serializer;
import net.elytrium.serializer.custom.ClassSerializer;
import net.elytrium.serializer.exceptions.ReflectionException;
import net.elytrium.serializer.exceptions.SerializableReadException;

public abstract class AbstractReader {

  private static final Logger LOGGER = Logger.getLogger(AbstractReader.class.getName());
  protected static final char NEW_LINE = '\n';

  private final char[] singleCharBuffer = new char[1];
  private final Queue<Character> seekBuffer = new ArrayDeque<>();
  protected final SerializerConfig config;
  protected final BufferedReader reader;

  private boolean reuseBuffer;
  private boolean seekEnabled;
  private CarriageType carriageType = CarriageType.UNKNOWN;

  public AbstractReader(SerializerConfig config, BufferedReader reader) {
    this.config = config;
    this.reader = reader;
  }

  public AbstractReader(BufferedReader reader) {
    this.config = SerializerConfig.DEFAULT;
    this.reader = reader;
  }

  public abstract void readSerializableObject(Object holder, Class<?> clazz);

  public abstract String readNodeName();

  public abstract void readBeginSerializableObject();

  public abstract void readSerializableObjectEntryJoin();

  public abstract boolean readEndSerializableObject();

  public void readNode(Object holder, Field node) {
    synchronized (this) {
      Type type = node.getGenericType();
      Class<?> clazz = node.getType();
      Deque<ClassSerializer<?, ?>> serializerStack = new ArrayDeque<>(
          Math.min(16, this.config.getRegisteredSerializers() + 1/*If first iteration and annotation serializer not cached yet.*/)
      );

      Serializer serializer = node.getAnnotation(Serializer.class);
      if (serializer != null) {
        try {
          ClassSerializer<?, ?> classSerializer = this.config.getAndCacheSerializer(serializer);
          if (clazz.isAssignableFrom(classSerializer.getToType())) {
            serializerStack.add(classSerializer);
            type = classSerializer.getFromType();
            clazz = classSerializer.getFromType();
          }
        } catch (ReflectiveOperationException e) {
          throw new ReflectionException(e);
        }
      }

      clazz = this.fillSerializerStack(serializerStack, clazz);
      if (!serializerStack.isEmpty()) {
        type = clazz;
      }

      try {
        Object value = this.readAndDeserializeByType(serializerStack, type);
        if (type == Integer.class || type == int.class) {
          node.setInt(holder, ((Long) value).intValue());
        } else if (type == Short.class || type == short.class) {
          node.setShort(holder, ((Long) value).shortValue());
        } else if (type == Byte.class || type == byte.class) {
          node.setByte(holder, ((Long) value).byteValue());
        } else if (type == Float.class || type == float.class) {
          node.setFloat(holder, ((Double) value).floatValue());
        } else {
          node.set(holder, value);
        }
      } catch (IllegalAccessException e) {
        throw new ReflectionException(e);
      }
    }
  }

  protected Class<?> fillSerializerStack(Deque<ClassSerializer<?, ?>> serializerStack, Class<?> clazz) {
    while (true) {
      ClassSerializer<?, ?> classSerializer = this.config.getRegisteredSerializer(clazz);
      if (classSerializer == null || !clazz.isAssignableFrom(classSerializer.getToType())) {
        break;
      }

      serializerStack.add(classSerializer);
      clazz = classSerializer.getFromType();

      if (classSerializer.getToType() == classSerializer.getFromType()) {
        break;
      }
    }

    return clazz;
  }

  protected Object readAndDeserializeByType(Deque<ClassSerializer<?, ?>> serializerStack, Type type) {
    Object value = this.readByType(type);
    while (!serializerStack.isEmpty()) {
      ClassSerializer<?, ?> classSerializer = serializerStack.pop();
      if (classSerializer.getFromType().isInstance(value)) {
        value = classSerializer.deserializeRaw(value);
      }
    }

    return value;
  }

  public Object readByField(Field field) {
    return this.readByType(field.getGenericType());
  }

  public Object readByType(Type type) {
    return this.readByType(null, type);
  }

  public Object readByType(Object holder, Type type) {
    synchronized (this) {
      if (type == Object.class) {
        return this.readGuessingType();
      } else if (type instanceof ParameterizedType parameterizedType) {
        Class<?> clazz = (Class<?>) parameterizedType.getRawType();
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        return Map.class.isAssignableFrom(clazz) ? this.readMap(actualTypeArguments[0], actualTypeArguments[1])
            : List.class.isAssignableFrom(clazz) ? this.readList(actualTypeArguments[0])
            : this.readGuessingType();
      } else if (type instanceof Class<?> clazz) {
        if (Map.class.isAssignableFrom(clazz)) {
          return this.readMap();
        } else if (List.class.isAssignableFrom(clazz)) {
          return this.readList();
        } else if (String.class.isAssignableFrom(clazz)) {
          return this.readString();
        } else if (Character.class.isAssignableFrom(clazz) || char.class.isAssignableFrom(clazz)) {
          return this.readCharacter();
        } else if (clazz.isEnum()) {
          return this.readEnum(clazz);
        } else if (Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)) {
          return this.readBoolean();
        } else if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
          return this.readNumber(clazz);
        } else {
          try {
            Object result = clazz.isInstance(holder) ? holder : clazz.getDeclaredConstructor().newInstance();
            this.readSerializableObject(result, clazz);
            return result;
          } catch (ReflectiveOperationException e) {
            Object value = this.readGuessingType();
            ClassSerializer<?, ?> classSerializer = this.config.getRegisteredSerializer(clazz);
            if (classSerializer != null) {
              value = classSerializer.deserializeRaw(value);
            }

            return value;
          }
        }
      } else {
        throw new IllegalArgumentException("Invalid type was provided: " + type);
      }
    }
  }

  public abstract Object readGuessingType();

  public Map<Object, Object> readMap() {
    return this.readMap(Object.class, Object.class);
  }

  public abstract Map<Object, Object> readMap(Type keyType, Type valueType);

  public List<Object> readList() {
    return this.readList(Object.class);
  }

  public abstract List<Object> readList(Type type);

  public abstract String readString();

  public abstract Character readCharacter();

  @SuppressWarnings("unchecked")
  public <T extends Enum<T>> T readEnum(Class<?> enumClass) {
    return Enum.valueOf((Class<T>) enumClass, this.readString());
  }

  public abstract Boolean readBoolean();

  public Number readNumber(Class<?> clazz) {
    synchronized (this) {
      boolean decimal = Float.class.isAssignableFrom(clazz) || float.class.isAssignableFrom(clazz)
          || Double.class.isAssignableFrom(clazz) || double.class.isAssignableFrom(clazz);
      try {
        // Read about casts: https://pastebin.com/wgR7zB7p
        return decimal ? (Number) this.readDouble() : (Number) this.readLong();
      } catch (NumberFormatException e) {
        if (this.config.isSafeMode()) {
          AbstractReader.LOGGER.log(Level.WARNING, "Can't read number due to exception caught, overwriting the value by 0", e);
          return decimal ? (Number) 0.0 : (Number) 0L;
        } else {
          throw new RuntimeException(e);
        }
      }
    }
  }

  public abstract Double readDouble();

  public abstract Long readLong();

  public void skipNode(Field node) {
    this.skipNode(node.getType());
  }

  public void skipNode(Class<?> clazz) {
    if (Map.class.isAssignableFrom(clazz)) {
      this.skipMap();
    } else if (List.class.isAssignableFrom(clazz)) {
      this.skipList();
    } else if (clazz.isEnum()
        || String.class.isAssignableFrom(clazz)
        || Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)
        || Character.class.isAssignableFrom(clazz) || char.class.isAssignableFrom(clazz)
        || Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
      this.skipString();
    } else {
      this.skipGuessingType();
    }
  }

  public abstract void skipMap();

  public abstract void skipList();

  public abstract void skipString();

  public abstract void skipGuessingType();

  public abstract boolean skipComments(char marker, boolean reuse);

  public char readRawIgnoreEmptyAndNewLines() {
    return this.readRawIgnoreEmptyAndCharacter(NEW_LINE);
  }

  public char readRawIgnoreEmptyAndCharacter(char marker) {
    char readMarker;
    do {
      readMarker = this.readRawIgnoreEmpty();
    } while (readMarker == marker);
    return readMarker;
  }

  public char readRawIgnoreEmpty() {
    char marker;
    int type;
    do {
      marker = this.readRaw();
      type = Character.getType(marker);
    } while (type == Character.SPACE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR || marker == '\t');
    return marker;
  }

  public char readRaw() {
    if (this.reuseBuffer) {
      this.reuseBuffer = false;
    } else {
      if (this.seekEnabled || this.seekBuffer.size() == 0) {
        try {
          if (this.reader.read(this.singleCharBuffer, 0, 1) < 1) {
            this.singleCharBuffer[0] = 0;
          }
        } catch (IOException e) {
          throw new SerializableReadException(e);
        }

        if (this.seekEnabled) {
          this.seekBuffer.add(this.singleCharBuffer[0]);
        }
      } else {
        this.singleCharBuffer[0] = this.seekBuffer.poll();
      }
    }

    return switch (this.singleCharBuffer[0]) {
      case '\r' -> switch (this.carriageType) {
        case LF -> throw new IllegalStateException("Caught a Carriage Return in LF mode");
        case CRLF -> this.readRaw();
        case CR -> AbstractReader.NEW_LINE;
        case UNKNOWN -> {
          char nextChar = this.readRaw();
          if (nextChar == '\n') {
            this.carriageType = CarriageType.CRLF;
          } else {
            this.carriageType = CarriageType.CR;
            this.seekBuffer.add(nextChar);
          }

          yield AbstractReader.NEW_LINE;
        }
      };
      case '\n' -> switch (this.carriageType) {
        case CR -> throw new IllegalStateException("Caught a Line Feed in CR mode");
        case CRLF, LF -> AbstractReader.NEW_LINE;
        case UNKNOWN -> {
          this.carriageType = CarriageType.LF;
          yield AbstractReader.NEW_LINE;
        }
      };
      default -> this.singleCharBuffer[0];
    };
  }

  public void replaceSingleCharBuffer(char replacement) {
    this.singleCharBuffer[0] = replacement;
  }

  public void setReuseBuffer() {
    this.reuseBuffer = true;
  }

  public boolean isReuseBuffer() {
    return this.reuseBuffer;
  }

  public void setSeek() {
    this.seekEnabled = true;
  }

  public void setSeekFromMarker(char marker) {
    this.seekBuffer.add(marker);
    this.seekEnabled = true;
  }

  public void unsetSeek() {
    this.seekEnabled = false;
  }

  public void clearSeek() {
    this.seekBuffer.clear();
    this.seekEnabled = false;
  }

  private enum CarriageType {

    UNKNOWN,
    CR,
    CRLF,
    LF
  }
}
