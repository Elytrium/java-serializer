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

package net.elytrium.serializer.language.object;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.elytrium.serializer.LoadResult;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.exceptions.SerializableReadException;
import net.elytrium.serializer.exceptions.SerializableWriteException;
import net.elytrium.serializer.language.reader.AbstractReader;
import net.elytrium.serializer.language.writer.AbstractWriter;

public abstract class AbstractSerializable {

  private static final DateTimeFormatter BACKUP_DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

  private final Path serializablePath;

  private SerializerConfig config;

  protected AbstractSerializable() {
    this(SerializerConfig.DEFAULT);
  }

  protected AbstractSerializable(SerializerConfig config) {
    this(null, config);
  }

  protected AbstractSerializable(Path serializablePath, SerializerConfig config) {
    this.serializablePath = serializablePath;
    this.config = config;
  }

  public void setConfig(SerializerConfig config) {
    this.config = config;
  }

  public SerializerConfig getConfig() {
    return this.config;
  }

  public LoadResult reload() {
    if (this.serializablePath == null) {
      throw new IllegalStateException("This AbstractSerializable was constructed without serializablePath. "
          + "Either construct AbstractSerializable with serializablePath or call AbstractSerializable#reload(Path) method.");
    }

    return this.reload(this.serializablePath);
  }

  public LoadResult reload(Path path) {
    LoadResult result = this.load(path);
    switch (result) {
      case SUCCESS -> this.save(path);
      case CONFIG_NOT_EXISTS -> {
        this.save(path);
        this.load(path); // Load again, because it now exists.
      }
      case BACKUP_PREFERRED -> {
        if (this.config.isBackupOnErrors()) {
          this.backup(path);
        }

        this.save(path);
      }
      default -> throw new IllegalStateException("Invalid LoadResult.");
    }

    return result;
  }

  public LoadResult load() {
    if (this.serializablePath == null) {
      throw new IllegalStateException("This AbstractSerializable was constructed without serializablePath. "
          + "Either construct AbstractSerializable with serializablePath or call AbstractSerializable#load(Path) method.");
    }

    return this.load(this.serializablePath);
  }

  public LoadResult load(Path path) {
    Path absolutePath = path.toAbsolutePath();
    if (Files.exists(absolutePath)) {
      try {
        return this.load(Files.newBufferedReader(absolutePath)) ? LoadResult.SUCCESS : LoadResult.BACKUP_PREFERRED;
      } catch (IOException e) {
        throw new SerializableReadException(e);
      }
    } else {
      return LoadResult.CONFIG_NOT_EXISTS;
    }
  }

  public boolean load(BufferedReader reader) {
    AbstractReader abstractReader = this.getReader(reader);
    abstractReader.readSerializableObject(this, this.getClass());
    return !abstractReader.isBackupPreferred();
  }

  public void save() {
    if (this.serializablePath == null) {
      throw new IllegalStateException("This AbstractSerializable was constructed without serializablePath. "
          + "Either construct AbstractSerializable with serializablePath or call AbstractSerializable#save(Path) method.");
    }

    this.save(this.serializablePath);
  }

  public void save(Path path) {
    try {
      Path absolutePath = path.toAbsolutePath();
      Path parent = absolutePath.getParent();
      if (parent == null) {
        throw new NullPointerException("Parent path is null for " + absolutePath);
      }

      Files.createDirectories(parent);
      this.save(Files.newBufferedWriter(absolutePath));
    } catch (IOException e) {
      throw new SerializableWriteException(e);
    }
  }

  public void save(BufferedWriter writer) {
    Comment[] comments = this.getClass().getAnnotationsByType(Comment.class);
    AbstractWriter abstractWriter = this.getWriter(writer);
    abstractWriter.writeComments(comments, Comment.At.PREPEND, true);
    abstractWriter.writeSerializableObject(this, this.getClass());
    abstractWriter.writeComments(comments, Comment.At.SAME_LINE, true);
    abstractWriter.writeComments(comments, Comment.At.APPEND, true);
    try {
      writer.flush();
    } catch (IOException e) {
      throw new SerializableWriteException(e);
    }
  }

  public void backup() {
    if (this.serializablePath == null) {
      throw new IllegalStateException("This AbstractSerializable was constructed without serializablePath. "
          + "Either construct AbstractSerializable with serializablePath or call AbstractSerializable#backup(Path) method.");
    }

    this.save(this.serializablePath);
  }

  public void backup(Path path) {
    try {
      Path absolutePath = path.toAbsolutePath();
      Path parent = absolutePath.getParent();
      if (parent == null) {
        throw new NullPointerException("Parent path is null for " + absolutePath);
      }

      Files.copy(absolutePath, parent.resolve(absolutePath.getFileName() + "_backup_" + LocalDateTime.now().format(AbstractSerializable.BACKUP_DATE_PATTERN)), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new SerializableWriteException(e);
    }
  }

  protected abstract AbstractReader getReader(BufferedReader reader);

  protected abstract AbstractWriter getWriter(BufferedWriter writer);
}
