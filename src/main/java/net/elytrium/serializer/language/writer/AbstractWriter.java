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
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.annotations.Serializer;
import net.elytrium.serializer.annotations.Transient;
import net.elytrium.serializer.custom.ClassSerializer;
import net.elytrium.serializer.exceptions.ReflectionException;
import net.elytrium.serializer.exceptions.SerializableWriteException;

public abstract class AbstractWriter {

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

  public void writeSerializableObject(Object value, Class<?> clazz) {
    synchronized (this) {
      boolean first = false;
      if (this.first) {
        first = true;
        this.first = false;
      }

      Field[] fields = clazz.getFields();
      if (fields.length == 0) {
        this.writeEmptyMap();
      } else {
        this.writeBeginMap();
        int counter = 0;
        for (Field field : fields) {
          ++counter;
          int modifiers = field.getModifiers();
          if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)
              && field.getAnnotation(Transient.class) == null && field.getType().getAnnotation(Transient.class) == null) {
            try {
              NewLine newLines = field.getAnnotation(NewLine.class);
              if (newLines != null) {
                for (int i = newLines.amount() - 1; i >= 1; --i) {
                  this.writeRaw(this.config.getLineSeparator());
                }

                this.writeLine();
              }

              Object nodeValue = field.get(value);
              Serializer serializer = field.getAnnotation(Serializer.class);
              if (serializer != null) {
                nodeValue = this.config.getAndCacheSerializer(serializer).serializeRaw(nodeValue);
              }

              if (nodeValue != null) {
                nodeValue = this.serializeValue(nodeValue);
              }

              this.writeMapEntry(this.config.toNodeName(field.getName()), nodeValue, counter != fields.length, field.getAnnotationsByType(Comment.class));
            } catch (ReflectiveOperationException e) {
              throw new ReflectionException(e);
            }
          }
        }

        this.writeEndMap();
      }

      if (first) {
        this.writeLine();
      }
    }
  }

  private Object serializeValue(Object nodeValue) {
    ClassSerializer<?, ?> classSerializer;
    while ((classSerializer = this.config.getRegisteredSerializer(nodeValue.getClass())) != null) {
      nodeValue = classSerializer.serializeRaw(nodeValue);
      if (classSerializer.getToType() == classSerializer.getFromType()) {
        break;
      }
    }

    return nodeValue;
  }

  public void writeMapEntry(String nodeName, Object node, boolean shouldJoin, Comment[] comments) {
    synchronized (this) {
      this.writeComments(comments, Comment.At.PREPEND, true);
      this.writeNodeName(nodeName);
      this.writeNode(node, null);
      if (shouldJoin) {
        this.writeMapPreCommentEntryJoin();
      }
      this.writeComments(comments, Comment.At.SAME_LINE, true);
      if (shouldJoin) {
        this.writeMapPostCommentEntryJoin();
      }
      this.writeMapEntryEnd();
      this.writeComments(comments, Comment.At.APPEND, shouldJoin);
    }
  }

  public void writeComments(Comment[] comments, Comment.At currentPosition, boolean shouldJoin) {
    synchronized (this) {
      if (comments != null && comments.length != 0) {
        for (int i = 0; i < comments.length - 1; ++i) {
          if (currentPosition == comments[i].at()) {
            this.writeComment(comments[i], true);
          }
        }

        if (currentPosition == comments[comments.length - 1].at()) {
          this.writeComment(comments[comments.length - 1], shouldJoin);
        }
      }
    }
  }

  public void writeComment(Comment comment, boolean shouldJoin) {
    synchronized (this) {
      for (CommentValue line : comment.value()) {
        if (line.type() == CommentValue.Type.NEW_LINE) {
          this.writeCommentEnd(comment.at());
        } else {
          if (!shouldJoin) {
            this.writeLine();
          }
          this.writeCommentStart(comment.at());
          this.writeRaw(line.value());
          if (shouldJoin) {
            this.writeCommentEnd(comment.at());
          }
        }
      }
    }
  }

  public abstract void writeCommentStart(Comment.At at);

  public abstract void writeCommentEnd(Comment.At at);

  public abstract void writeNodeName(String nodeName);

  @SuppressWarnings("unchecked")
  public void writeNode(Object value, Comment[] comments) {
    synchronized (this) {
      if (value == null) {
        this.writeRaw("null");
      } else {
        value = this.serializeValue(value);
        if (value instanceof Map) {
          this.writeMap((Map<Object, Object>) value, comments);
        } else if (value instanceof List) {
          this.writeList((List<Object>) value, comments);
        } else if (value instanceof String) {
          this.writeString((String) value);
        } else if (value instanceof Character) {
          this.writeCharacter((Character) value);
        } else if (value instanceof Enum) {
          this.writeEnum((Enum<?>) value);
        } else if (value instanceof Boolean || value instanceof Number || value.getClass().isPrimitive()) {
          this.writeRaw(value.toString());
        } else {
          this.writeSerializableObject(value, value.getClass());
        }
      }
    }
  }

  public void writeMap(Map<Object, Object> value, Comment[] comments) {
    synchronized (this) {
      if (value.size() == 0) {
        this.writeEmptyMap();
        this.writeComments(comments, Comment.At.SAME_LINE, true);
      } else {
        this.writeBeginMap();
        this.writeComments(comments, Comment.At.SAME_LINE, true);

        Set<Map.Entry<Object, Object>> entries = value.entrySet();
        int counter = 0;
        int entriesAmount = entries.size();
        for (Map.Entry<Object, Object> entry : entries) {
          this.writeMapEntry(entry.getKey().toString(), entry.getValue(), ++counter != entriesAmount, null);
        }

        this.writeEndMap();
      }
    }
  }

  public abstract void writeEmptyMap();

  public abstract void writeBeginMap();

  public abstract void writeMapPreCommentEntryJoin();

  public abstract void writeMapPostCommentEntryJoin();

  public abstract void writeMapEntryEnd();

  public abstract void writeEndMap();

  public void writeList(List<Object> value, Comment[] comments) {
    synchronized (this) {
      if (value.size() == 0) {
        this.writeEmptyList();
        this.writeComments(comments, Comment.At.SAME_LINE, true);
      } else {
        this.writeBeginList();
        this.writeComments(comments, Comment.At.SAME_LINE, true);

        int counter = 0;
        int entriesAmount = value.size();
        for (Object entry : value) {
          this.writeListEntry(entry);
          if (++counter != entriesAmount) {
            this.writeListEntryJoin();
          }

          this.writeListEntryEnd();
        }

        this.writeEndList();
      }
    }
  }

  public abstract void writeEmptyList();

  public abstract void writeBeginList();

  public abstract void writeListEntry(Object entry);

  public abstract void writeListEntryJoin();

  public abstract void writeListEntryEnd();

  public abstract void writeEndList();

  public abstract void writeLine();

  public abstract void writeString(String value);

  public abstract void writeCharacter(char value);

  public void writeEnum(Enum<?> value) {
    synchronized (this) {
      this.writeRaw(value.name());
    }
  }

  public void writeBoolean(boolean value) {
    synchronized (this) {
      this.writeRaw(String.valueOf(value));
    }
  }

  public void writeNumber(Number value) {
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
