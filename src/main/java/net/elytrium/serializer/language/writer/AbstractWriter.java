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

package net.elytrium.serializer.language.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.annotations.OverrideNameStyle;
import net.elytrium.serializer.annotations.Serializer;
import net.elytrium.serializer.annotations.Transient;
import net.elytrium.serializer.custom.ClassSerializer;
import net.elytrium.serializer.exceptions.ReflectionException;
import net.elytrium.serializer.exceptions.SerializableWriteException;

public abstract class AbstractWriter {

  protected static final char NEW_LINE = '\n';

  protected final SerializerConfig config;
  protected final BufferedWriter writer;

  private boolean first = true;

  protected AbstractWriter(SerializerConfig config, BufferedWriter writer) {
    this.config = config;
    this.writer = writer;
  }

  protected AbstractWriter(BufferedWriter writer) {
    this.config = SerializerConfig.DEFAULT;
    this.writer = writer;
  }

  private boolean isFieldVisible(Field field) {
    int modifiers = field.getModifiers();
    return !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)
        && field.getAnnotation(Transient.class) == null && field.getType().getAnnotation(Transient.class) == null;
  }

  public void writeSerializableObject(Object value, Class<?> clazz) {
    this.writeSerializableObject(null, value, clazz);
  }

  public void writeSerializableObject(@Nullable Field owner, Object value, Class<?> clazz) {
    synchronized (this) {
      boolean first = false;
      if (this.first) {
        first = true;
        this.first = false;
      }

      Field[] fields = clazz.getDeclaredFields();
      boolean empty = true;
      int counter = 0;
      int lastVisibleFieldIndex = 0;
      for (int i = fields.length - 1; i >= 0; --i) {
        if (this.isFieldVisible(fields[i])) {
          lastVisibleFieldIndex = i + 1;
          break;
        }
      }

      for (Field field : fields) {
        ++counter;
        if (this.isFieldVisible(field)) {
          try {
            field.setAccessible(true);
          } catch (Exception e) {
            continue;
          }

          try {
            NewLine newLines = field.getAnnotation(NewLine.class);
            if (newLines == null) {
              newLines = field.getType().getAnnotation(NewLine.class);
            }

            if (newLines != null) {
              for (int i = newLines.amount() - 1; i >= 1; --i) {
                this.writeRaw(this.config.getLineSeparator());
              }

              this.writeLine();
            }

            Object nodeValue = field.get(value);
            Serializer serializer = field.getAnnotation(Serializer.class);
            if (serializer == null) {
              serializer = field.getType().getAnnotation(Serializer.class);
            }

            if (serializer != null) {
              nodeValue = this.config.getAndCacheSerializer(serializer).serialize(nodeValue);
            }

            if (nodeValue != null) {
              nodeValue = this.serializeValue(nodeValue);
            }

            OverrideNameStyle overrideNameStyle = field.getAnnotation(OverrideNameStyle.class);
            if (overrideNameStyle == null) {
              overrideNameStyle = field.getType().getAnnotation(OverrideNameStyle.class);
            }

            if (empty) {
              empty = false;
              this.writeBeginMap(owner);
            }

            this.writeMapEntry(field,
                overrideNameStyle == null
                    ? this.config.toNodeName(field.getName())
                    : this.config.toNodeName(field.getName(), overrideNameStyle.field(), overrideNameStyle.node()),
                nodeValue,
                counter != lastVisibleFieldIndex,
                Stream.of(field.getType().getAnnotationsByType(Comment.class), field.getAnnotationsByType(Comment.class))
                    .flatMap(Stream::of)
                    .toArray(Comment[]::new)
            );
          } catch (ReflectiveOperationException e) {
            throw new ReflectionException(e);
          }
        }
      }

      if (empty) {
        this.writeEmptyMap();
      } else {
        this.writeEndMap(owner);
      }

      if (first) {
        this.writeLine();
      }
    }
  }

  private Object serializeValue(Object nodeValue) {
    ClassSerializer<Object, ?> classSerializer;
    while ((classSerializer = this.config.getRegisteredSerializer(nodeValue.getClass())) != null) {
      nodeValue = classSerializer.serialize(nodeValue);
      if (classSerializer.getToType() == classSerializer.getFromType()) {
        break;
      }
    }

    return nodeValue;
  }

  public void writeMapEntry(String nodeName, Object node, boolean shouldJoin, Comment[] comments) {
    this.writeMapEntry(null, nodeName, node, shouldJoin, comments);
  }

  public void writeMapEntry(@Nullable Field owner, String nodeName, Object node, boolean shouldJoin, Comment[] comments) {
    synchronized (this) {
      this.writeComments(owner, comments, Comment.At.PREPEND, true);
      this.writeNodeName(owner, nodeName);
      this.writeNode(owner, node, null);
      if (shouldJoin) {
        this.writeMapPreCommentEntryJoin(owner);
      }
      this.writeComments(owner, comments, Comment.At.SAME_LINE, true);
      if (shouldJoin) {
        this.writeMapPostCommentEntryJoin(owner);
      }
      this.writeMapEntryEnd(owner);
      this.writeComments(owner, comments, Comment.At.APPEND, shouldJoin);
    }
  }

  public void writeComments(Comment[] comments, Comment.At currentPosition, boolean shouldJoin) {
    this.writeComments(null, comments, currentPosition, shouldJoin);
  }

  public void writeComments(@Nullable Field owner, Comment[] comments, Comment.At currentPosition, boolean shouldJoin) {
    synchronized (this) {
      if (comments != null && comments.length != 0) {
        for (int i = 0; i < comments.length - 1; ++i) {
          if (currentPosition == comments[i].at()) {
            this.writeComment(owner, comments[i], true);
          }
        }

        if (currentPosition == comments[comments.length - 1].at()) {
          this.writeComment(owner, comments[comments.length - 1], shouldJoin);
        }
      }
    }
  }

  public void writeComment(Comment comment, boolean shouldJoin) {
    this.writeComment(null, comment, shouldJoin);
  }

  public void writeComment(@Nullable Field owner, Comment comment, boolean shouldJoin) {
    synchronized (this) {
      for (CommentValue line : comment.value()) {
        if (line.type() == CommentValue.Type.NEW_LINE) {
          this.writeCommentEnd(owner, comment.at());
        } else {
          if (!shouldJoin) {
            this.writeLine();
          }
          this.writeCommentStart(owner, comment.at());
          this.writeCommentValueIndent(owner, comment.at(), this.getCommentValueIndent(comment, line));
          this.writeRaw(line.value());
          if (shouldJoin) {
            this.writeCommentEnd(owner, comment.at());
          }
        }
      }
    }
  }

  private int getCommentValueIndent(Comment comment, CommentValue value) {
    if (value.commentValueIndent() != -1) {
      return value.commentValueIndent();
    }

    if (comment.commentValueIndent() != -1) {
      return comment.commentValueIndent();
    }

    return this.config.getCommentValueIndent();
  }

  public void writeCommentStart(Comment.At at) {
    this.writeCommentStart(null, at);
  }
  
  public abstract void writeCommentStart(@Nullable Field owner, Comment.At at);

  public void writeCommentValueIndent(Comment.At at, int indent) {
    this.writeCommentValueIndent(null, at, indent);
  }
  
  public abstract void writeCommentValueIndent(@Nullable Field owner, Comment.At at, int indent);

  public void writeCommentEnd(Comment.At at) {
    this.writeCommentEnd(null, at);
  }
  
  public abstract void writeCommentEnd(@Nullable Field owner, Comment.At at);

  public void writeNodeName(String nodeName) {
    this.writeNodeName(null, nodeName);
  }
  
  public abstract void writeNodeName(@Nullable Field owner, String nodeName);

  public void writeNode(Object value, Comment[] comments) {
    this.writeNode(null, value, comments);
  }

  @SuppressWarnings("unchecked")
  public void writeNode(@Nullable Field owner, Object value, Comment[] comments) {
    synchronized (this) {
      if (value == null) {
        this.writeRaw("null");
      } else {
        value = this.serializeValue(value);
        if (value instanceof Map) {
          this.writeMap(owner, (Map<Object, Object>) value, comments);
        } else if (value instanceof Collection<?>) {
          this.writeList(owner, (Collection<Object>) value, comments);
        } else if (value instanceof String) {
          this.writeString(owner, (String) value);
        } else if (value instanceof Character) {
          this.writeCharacter(owner, (Character) value);
        } else if (value instanceof Enum) {
          this.writeEnum(owner, (Enum<?>) value);
        } else if (value instanceof Boolean || value instanceof Number || value.getClass().isPrimitive()) {
          this.writeRaw(value.toString());
        } else {
          this.writeSerializableObject(owner, value, value.getClass());
        }
      }
    }
  }

  public void writeMap(Map<Object, Object> value, Comment[] comments) {
    this.writeMap(null, value, comments);
  }

  public void writeMap(@Nullable Field owner, Map<Object, Object> value, Comment[] comments) {
    synchronized (this) {
      if (value.isEmpty()) {
        this.writeEmptyMap(owner);
        this.writeComments(owner, comments, Comment.At.SAME_LINE, true);
      } else {
        this.writeBeginMap(owner);
        this.writeComments(owner, comments, Comment.At.SAME_LINE, true);

        Set<Map.Entry<Object, Object>> entries = value.entrySet();
        int counter = 0;
        int entriesAmount = entries.size();
        for (Map.Entry<Object, Object> entry : entries) {
          this.writeMapEntry(null, entry.getKey().toString(), entry.getValue(), ++counter != entriesAmount, null);
        }

        this.writeEndMap(owner);
      }
    }
  }

  public void writeEmptyMap() {
    this.writeEmptyMap(null);
  }
  
  public abstract void writeEmptyMap(@Nullable Field owner);

  public void writeBeginMap() {
    this.writeBeginMap(null);
  }
  
  public abstract void writeBeginMap(@Nullable Field owner);

  public void writeMapPreCommentEntryJoin() {
    this.writeMapPreCommentEntryJoin(null);
  }
  
  public abstract void writeMapPreCommentEntryJoin(@Nullable Field owner);

  public void writeMapPostCommentEntryJoin() {
    this.writeMapPostCommentEntryJoin(null);
  }
  
  public abstract void writeMapPostCommentEntryJoin(@Nullable Field owner);

  public void writeMapEntryEnd() {
    this.writeMapEntryEnd(null);
  }
  
  public abstract void writeMapEntryEnd(@Nullable Field owner);

  public void writeEndMap() {
    this.writeEndMap(null);
  }
  
  public abstract void writeEndMap(@Nullable Field owner);

  public void writeList(Collection<Object> value, Comment[] comments) {
    this.writeList(null, value, comments);
  }

  public void writeList(@Nullable Field owner, Collection<Object> value, Comment[] comments) {
    synchronized (this) {
      if (value.isEmpty()) {
        this.writeEmptyList(owner);
        this.writeComments(owner, comments, Comment.At.SAME_LINE, true);
      } else {
        this.writeBeginList(owner);
        this.writeComments(owner, comments, Comment.At.SAME_LINE, true);

        int counter = 0;
        int entriesAmount = value.size();
        for (Object entry : value) {
          this.writeListEntry(owner, entry);
          if (++counter != entriesAmount) {
            this.writeListEntryJoin(owner);
          }

          this.writeListEntryEnd(owner);
        }

        this.writeEndList(owner);
      }
    }
  }

  public void writeEmptyList() {
    this.writeEmptyList(null);
  }
  
  public abstract void writeEmptyList(@Nullable Field owner);

  public void writeBeginList() {
    this.writeBeginList(null);
  }
  
  public abstract void writeBeginList(@Nullable Field owner);

  public void writeListEntry(Object entry) {
    this.writeListEntry(null, entry);
  }
  
  public abstract void writeListEntry(@Nullable Field owner, Object entry);

  public void writeListEntryJoin() {
    this.writeListEntryJoin(null);
  }
  
  public abstract void writeListEntryJoin(@Nullable Field owner);

  public void writeListEntryEnd() {
    this.writeListEntryEnd(null);
  }
  
  public abstract void writeListEntryEnd(@Nullable Field owner);

  public void writeEndList() {
    this.writeEndList(null);
  }
  
  public abstract void writeEndList(@Nullable Field owner);

  public abstract void writeLine();

  public void writeString(String value) {
    this.writeString(null, value);
  }
  
  public abstract void writeString(@Nullable Field owner, String value);

  public void writeCharacter(char value) {
    this.writeCharacter(null, value);
  }
  
  public abstract void writeCharacter(@Nullable Field owner, char value);

  public void writeEnum(Enum<?> value) {
    this.writeEnum(null, value);
  }

  public void writeEnum(@Nullable Field owner, Enum<?> value) {
    synchronized (this) {
      this.writeRaw(value.name());
    }
  }

  public void writeBoolean(boolean value) {
    this.writeBoolean(null, value);
  }

  public void writeBoolean(@Nullable Field owner, boolean value) {
    synchronized (this) {
      this.writeRaw(String.valueOf(value));
    }
  }

  public void writeNumber(Number value) {
    this.writeNumber(null, value);
  }

  public void writeNumber(@Nullable Field owner, Number value) {
    synchronized (this) {
      this.writeRaw(value.toString());
    }
  }

  public void writeRaw(String value) {
    try {
      this.writer.write(value);
    } catch (IOException e) {
      throw new SerializableWriteException(e);
    }
  }

  public void writeRaw(char value) {
    try {
      this.writer.write(value);
    } catch (IOException e) {
      throw new SerializableWriteException(e);
    }
  }

  public void flush() {
    try {
      this.writer.flush();
    } catch (IOException e) {
      throw new SerializableWriteException(e);
    }
  }
}
