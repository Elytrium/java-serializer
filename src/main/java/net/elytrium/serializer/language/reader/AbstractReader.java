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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.CollectionType;
import net.elytrium.serializer.annotations.Serializer;
import net.elytrium.serializer.custom.ClassSerializer;
import net.elytrium.serializer.exceptions.ReflectionException;
import net.elytrium.serializer.exceptions.SerializableReadException;
import net.elytrium.serializer.utils.GenericUtils;

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
  private boolean backupPreferred;

  public AbstractReader(SerializerConfig config, BufferedReader reader) {
    this.config = config;
    this.reader = reader;
  }

  public AbstractReader(BufferedReader reader) {
    this.config = SerializerConfig.DEFAULT;
    this.reader = reader;
  }

  public void readSerializableObject(Object holder, Class<?> clazz) {
    this.readSerializableObject(null, holder, clazz);
  }
  
  public abstract void readSerializableObject(@Nullable Field owner, Object holder, Class<?> clazz);

  public String readNodeName() {
    return this.readNodeName(null);
  }
  
  public abstract String readNodeName(@Nullable Field owner);

  public void readBeginSerializableObject() {
    this.readBeginSerializableObject(null);
  }
  
  public abstract void readBeginSerializableObject(@Nullable Field owner);

  public void readSerializableObjectEntryJoin() {
    this.readSerializableObjectEntryJoin(null);
  }
  
  public abstract void readSerializableObjectEntryJoin(@Nullable Field owner);

  public boolean readEndSerializableObject() {
    return this.readEndSerializableObject(null);
  }
  
  public abstract boolean readEndSerializableObject(@Nullable Field owner);

  public void readNode(Object holder, Field node) {
    synchronized (this) {
      Type type = node.getGenericType();
      Class<?> clazz = node.getType();
      Deque<ClassSerializer<?, Object>> serializerStack = new ArrayDeque<>(
          Math.min(16, this.config.getRegisteredSerializers() + 1/*If first iteration and annotation serializer not cached yet.*/)
      );

      Serializer serializer = node.getAnnotation(Serializer.class);
      if (serializer == null) {
        serializer = node.getType().getAnnotation(Serializer.class);
      }

      if (serializer != null) {
        try {
          ClassSerializer<?, Object> classSerializer = this.config.getAndCacheSerializer(serializer);
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
        Object value = this.readAndDeserializeByType(node, node.get(holder), type, serializerStack);
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

  protected Class<?> fillSerializerStack(Deque<ClassSerializer<?, Object>> serializerStack, Class<?> clazz) {
    while (true) {
      ClassSerializer<?, Object> classSerializer = this.config.getRegisteredSerializer(clazz);
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

  protected Object readAndDeserializeByType(@Nullable Field owner, Object holder, Type type, Deque<ClassSerializer<?, Object>> serializerStack) {
    Object value = this.readByType(owner, holder, type);
    while (!serializerStack.isEmpty()) {
      ClassSerializer<?, Object> classSerializer = serializerStack.pop();
      if (classSerializer.getFromType().isInstance(value)) {
        value = classSerializer.deserialize(value);
      }
    }

    return value;
  }

  public Object readByField(Field field) {
    return this.readByType(field, field.getGenericType());
  }

  public Object readByType(Type type) {
    return this.readByType(null, type);
  }

  public Object readByType(@Nullable Field owner, Type type) {
    // TODO Use old map value when possible while reading.
    return this.readByType(owner, null, type);
  }

  public Object readByType(@Nullable Object holder, Type type) {
    return this.readByType(null, holder, type);
  }

  public Object readByType(@Nullable Field owner, @Nullable Object holder, Type type) {
    synchronized (this) {
      if (type == Object.class) {
        return this.readGuessingType(owner);
      } else if (type instanceof ParameterizedType parameterizedType) {
        Class<?> clazz = (Class<?>) parameterizedType.getRawType();
        if (Map.class.isAssignableFrom(clazz)) {
          return this.readMap(owner,
              GenericUtils.getParameterType(Map.class, parameterizedType, 0),
              GenericUtils.getParameterType(Map.class, parameterizedType, 1));
        } else if (Collection.class.isAssignableFrom(clazz)) {
          Type collectionEntryType = GenericUtils.getParameterType(Collection.class, parameterizedType, 0);
          if (owner != null) {
            CollectionType collectionType = owner.getAnnotation(CollectionType.class);
            if (collectionType != null) {
              try {
                //noinspection unchecked
                return this.readCollection(owner,
                    (Collection<Object>) collectionType.value().getDeclaredConstructor().newInstance(),
                    collectionEntryType);
              } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new SerializableReadException(e);
              }
            } else {
              try {
                //noinspection unchecked
                return this.readCollection(owner,
                    (Collection<Object>) owner.getType().getDeclaredConstructor().newInstance(),
                    collectionEntryType);
              } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new SerializableReadException(e);
              } catch (NoSuchMethodException e) {
                // Ignoring NoSuchMethod.
              }
            }
          }
          if (Set.class.isAssignableFrom(clazz)) {
            return this.readSet(collectionEntryType);
          } else if (Queue.class.isAssignableFrom(clazz)) {
            return this.readDeque(collectionEntryType);
          } else {
            return this.readList(collectionEntryType);
          }
        } else {
          return this.readGuessingType(owner);
        }
      } else if (type instanceof Class<?> clazz) {
        if (Map.class.isAssignableFrom(clazz)) {
          return this.readMap(owner);
        } else if (List.class.isAssignableFrom(clazz)) {
          return this.readList(owner);
        } else if (String.class.isAssignableFrom(clazz)) {
          return this.readString(owner);
        } else if (Character.class.isAssignableFrom(clazz) || char.class.isAssignableFrom(clazz)) {
          return this.readCharacter(owner);
        } else if (clazz.isEnum()) {
          return this.readEnum(owner, clazz);
        } else if (Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)) {
          return this.readBoolean(owner);
        } else if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
          return this.readNumber(owner, clazz);
        } else {
          try {
            Object result;
            if (clazz.isInstance(holder)) {
              result = holder;
            } else {
              Constructor<?> constructor = clazz.getDeclaredConstructor();
              constructor.setAccessible(true);
              result = constructor.newInstance();
            }

            this.readSerializableObject(owner, result, clazz);
            return result;
          } catch (ReflectiveOperationException e) {
            Object value = this.readGuessingType(owner);
            ClassSerializer<?, Object> classSerializer = this.config.getRegisteredSerializer(clazz);
            if (classSerializer != null) {
              value = classSerializer.deserialize(value);
            }

            return value;
          }
        }
      } else {
        throw new IllegalArgumentException("Invalid type was provided: " + type);
      }
    }
  }

  public Object readGuessingType() {
    return this.readGuessingType(null);
  }
  
  public abstract Object readGuessingType(@Nullable Field owner);

  public Map<Object, Object> readMap(@Nullable Field owner) {
    return this.readMap(owner, Object.class, Object.class);
  }

  public Map<Object, Object> readMap(Type keyType, Type valueType) {
    return this.readMap(null, keyType, valueType);
  }
  
  public abstract Map<Object, Object> readMap(@Nullable Field owner, Type keyType, Type valueType);

  public <C extends Collection<Object>> C readCollection(@Nullable Field owner, C result) {
    return this.readCollection(owner, result, Object.class);
  }

  public <C extends Collection<Object>> C readCollection(C result, Type type) {
    return this.readCollection(null, result, type);
  }

  public abstract <C extends Collection<Object>> C readCollection(@Nullable Field owner, C result, Type type);

  public List<Object> readList(@Nullable Field owner) {
    return this.readList(owner, Object.class);
  }

  public List<Object> readList(Type type) {
    return this.readList(null, type);
  }
  
  public List<Object> readList(@Nullable Field owner, Type type) {
    return this.readCollection(owner, new ArrayList<>(), type);
  }

  public Set<Object> readSet(@Nullable Field owner) {
    return this.readSet(owner, Object.class);
  }

  public Set<Object> readSet(Type type) {
    return this.readSet(null, type);
  }

  public Set<Object> readSet(@Nullable Field owner, Type type) {
    return this.readCollection(owner, new HashSet<>(), type);
  }

  public Deque<Object> readDeque(@Nullable Field owner) {
    return this.readDeque(owner, Object.class);
  }

  public Deque<Object> readDeque(Type type) {
    return this.readDeque(null, type);
  }

  public Deque<Object> readDeque(@Nullable Field owner, Type type) {
    return this.readCollection(owner, new ArrayDeque<>(), type);
  }

  public String readString() {
    return this.readString(null);
  }
  
  public abstract String readString(@Nullable Field owner);

  public Character readCharacter() {
    return this.readCharacter(null);
  }
  
  public abstract Character readCharacter(@Nullable Field owner);

  @SuppressWarnings("unchecked")
  public <T extends Enum<T>> T readEnum(@Nullable Field owner, Class<?> enumClass) {
    String enumValue = this.readString(owner);
    return enumValue == null ? null : Enum.valueOf((Class<T>) enumClass, enumValue);
  }

  public Boolean readBoolean() {
    return this.readBoolean(null);
  }
  
  public abstract Boolean readBoolean(@Nullable Field owner);

  public Number readNumber(Class<?> clazz) {
    return this.readNumber(null, clazz);
  }

  public Number readNumber(@Nullable Field owner, Class<?> clazz) {
    synchronized (this) {
      boolean decimal = Float.class.isAssignableFrom(clazz) || float.class.isAssignableFrom(clazz)
          || Double.class.isAssignableFrom(clazz) || double.class.isAssignableFrom(clazz);
      try {
        // Read about casts: https://pastebin.com/wgR7zB7p
        return decimal ? (Number) this.readDouble(owner) : (Number) this.readLong(owner);
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

  public Double readDouble() {
    return this.readDouble(null);
  }
  
  public abstract Double readDouble(@Nullable Field owner);

  public Long readLong() {
    return this.readLong(null);
  }
  
  public abstract Long readLong(@Nullable Field owner);

  public void skipNode(Field node) {
    this.skipNode(node, node.getType());
  }

  public void skipNode(Class<?> clazz) {
    this.skipNode(null, clazz);
  }

  public void skipNode(@Nullable Field owner, Class<?> clazz) {
    if (Map.class.isAssignableFrom(clazz)) {
      this.skipMap(owner);
    } else if (List.class.isAssignableFrom(clazz)) {
      this.skipList(owner);
    } else if (clazz.isEnum()
        || String.class.isAssignableFrom(clazz)
        || Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)
        || Character.class.isAssignableFrom(clazz) || char.class.isAssignableFrom(clazz)
        || Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
      this.skipString(owner);
    } else {
      this.skipGuessingType(owner);
    }
  }

  public void skipMap() {
    this.skipMap(null);
  }
  
  public abstract void skipMap(@Nullable Field owner);

  public void skipList() {
    this.skipList(null);
  }
  
  public abstract void skipList(@Nullable Field owner);

  public void skipString() {
    this.skipString(null);
  }
  
  public abstract void skipString(@Nullable Field owner);

  public void skipGuessingType() {
    this.skipGuessingType(null);
  }
  
  public abstract void skipGuessingType(@Nullable Field owner);

  public boolean skipComments(char marker, boolean reuse) {
    return this.skipComments(null, marker, reuse);
  }
  
  public abstract boolean skipComments(@Nullable Field owner, char marker, boolean reuse);

  public char readRawIgnoreEmptyAndNewLines() {
    return this.readRawIgnoreEmptyAndCharacter(AbstractReader.NEW_LINE);
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
      if (this.seekEnabled || this.seekBuffer.isEmpty()) {
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

  public boolean isBackupPreferred() {
    return this.backupPreferred;
  }

  protected void setBackupPreferred() {
    this.backupPreferred = true;
  }

  private enum CarriageType {

    UNKNOWN,
    CR,
    CRLF,
    LF
  }
}
