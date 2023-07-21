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
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;

public class YamlWriter extends AbstractWriter {

  private boolean first = true;
  private boolean tempDisableNewLine;

  private String singleIndent = "  ";
  private String currentIndent = "";
  private boolean waitingForEntryValue;

  public YamlWriter(SerializerConfig config, BufferedWriter writer) {
    super(config, writer);
  }

  public YamlWriter(BufferedWriter writer) {
    super(writer);
  }

  public void setSingleIndent(String singleIndent) {
    this.singleIndent = singleIndent;
  }

  @Override
  public void writeCommentStart(Comment.At at) {
    synchronized (this) {
      if (at == Comment.At.SAME_LINE) {
        this.writeRaw(" #");
      } else {
        this.writeRaw(this.currentIndent);
        this.writeRaw('#');
      }
    }
  }

  @Override
  public void writeCommentEnd(Comment.At at) {
    synchronized (this) {
      if (at != Comment.At.SAME_LINE) {
        this.writeLine();
      }
    }
  }

  @Override
  public void writeNodeName(String value) {
    synchronized (this) {
      this.indentOrUnsetTempDisableWriteLine();
      this.writeString0(value, true);
    }
  }

  @Override
  public void writeEmptyMap() {
    synchronized (this) {
      this.writeRaw("{}");
    }
  }

  @Override
  public void writeBeginMap() {
    synchronized (this) {
      this.writeBeginCommon();
    }
  }

  @Override
  public void writeMapPreCommentEntryJoin() {

  }

  @Override
  public void writeMapPostCommentEntryJoin() {
    synchronized (this) {
      this.writeLine();
    }
  }

  @Override
  public void writeMapEntryEnd() {

  }

  @Override
  public void writeEndMap() {
    synchronized (this) {
      this.removeIndent();
    }
  }

  @Override
  public void writeEmptyList() {
    synchronized (this) {
      this.writeRaw("[]");
    }
  }

  @Override
  public void writeBeginList() {
    synchronized (this) {
      this.writeBeginCommon();
    }
  }

  @Override
  public void writeListEntry(Object entry) {
    synchronized (this) {
      this.indentOrUnsetTempDisableWriteLine();

      this.writeRaw("- ");
      this.setTempDisableNewLine();
      this.writeNode(entry, null);
      this.unsetTempDisableNewLine();
    }
  }

  @Override
  public void writeListEntryJoin() {
    synchronized (this) {
      this.writeLine();
    }
  }

  @Override
  public void writeListEntryEnd() {

  }

  @Override
  public void writeEndList() {
    synchronized (this) {
      this.removeIndent();
    }
  }

  @Override
  public void writeLine() {
    synchronized (this) {
      super.writeRaw(this.config.getLineSeparator());
    }
  }

  @Override
  public void writeString(String value) {
    synchronized (this) {
      this.writeString0(value, false);
    }
  }

  @Override
  public void writeCharacter(char value) {
    synchronized (this) {
      boolean shouldUseQuotes = this.shouldUseQuotes(value);
      if (shouldUseQuotes) {
        this.writeRaw('"');
      }
      this.writeCharacter0(value);
      if (shouldUseQuotes) {
        this.writeRaw('"');
      }
    }
  }

  private boolean shouldUseQuotes(char value) {
    return value == '\0' || value == '\u0007' || value == '\b' || value == '\t' || value == '\n' || value == '\u000B'
        || value == '\f' || value == '\r' || value == '\u001B' || (value == ' ' || value == '#'/*Not escaping, but avoiding usage without quotes.*/)
        || value == '"' || value == '\'' || value == '\\' || value == '\u0085' || value == '\u00A0' || value == '\u2028' || value == '\u2029';
  }

  protected void writeString0(String value, boolean nodeName) {
    if (value.isEmpty()) {
      this.writeRaw("\"\"");
    } else {
      char[] characters = value.toCharArray();
      boolean shouldUseQuotes = false;
      if (nodeName) {
        for (char character : characters) {
          if (this.shouldUseQuotes(character)) {
            shouldUseQuotes = true;
            break;
          }
        }
      } else {
        shouldUseQuotes = true;
      }
      if (shouldUseQuotes) {
        this.writeRaw('"');
      }

      char highSurrogate = 0;
      for (char character : characters) {
        if (highSurrogate != 0) {
          highSurrogate = 0;
          int codePoint = Character.toCodePoint(highSurrogate, character);
        } else if (Character.isHighSurrogate(character)) {
          highSurrogate = character;
        } else {
          this.writeCharacter0(character);
        }
      }

      if (highSurrogate != 0) {
        this.writeCharacter0(highSurrogate);
      }

      if (shouldUseQuotes) {
        this.writeRaw('"');
      }
      if (nodeName) {
        this.writeRaw(':');
        this.waitingForEntryValue = true;
      }
    }
  }

  private void writeCharacter0(int value) {
    switch (value) {
      case '\0' -> this.writeRaw("\\0");
      case '\u0007' -> this.writeRaw("\\a");
      case '\b' -> this.writeRaw("\\b");
      case '\t' -> this.writeRaw("\\t");
      case '\n' -> this.writeRaw("\\n");
      case '\u000B' -> this.writeRaw("\\v");
      case '\f' -> this.writeRaw("\\f");
      case '\r' -> this.writeRaw("\\r");
      case '\u001B' -> this.writeRaw("\\e");
      case '\"' -> this.writeRaw("\\\"");
      case '\\' -> this.writeRaw("\\\\");
      case '\u0085' -> this.writeRaw("\\N");
      case '\u00A0' -> this.writeRaw("\\_");
      case '\u2028' -> this.writeRaw("\\L");
      case '\u2029' -> this.writeRaw("\\P");
      default -> {
        if (Character.isIdentifierIgnorable(value) && !this.config.isAllowUnicode()) {
          //noinspection UnnecessaryUnicodeEscape
          if (value <= 0xFF) {
            String s = "0" + Integer.toString(value, 16);
            this.writeRaw("\\x");
            this.writeRaw(s.substring(s.length() - 2));
            break;
          } else if (Character.charCount(value) == 2) {
            String s = "000" + Long.toHexString(value);
            this.writeRaw("\\U");
            this.writeRaw(s.substring(s.length() - 8));
            break;
          } else {
            String s = "000" + Integer.toString(value, 16);
            this.writeRaw("\\u");
            this.writeRaw(s.substring(s.length() - 4));
            break;
          }
        }

        for (char c : Character.toChars(value)) {
          this.writeRaw(c);
        }
      }
    }
  }

  private void writeBeginCommon() {
    if (this.first) {
      this.first = false;
    } else {
      this.addIndent();
      if (!this.tempDisableNewLine) {
        this.writeLine();
      }
    }

    this.waitingForEntryValue = false;
  }

  private void addIndent() {
    this.currentIndent += this.singleIndent;
  }

  private void removeIndent() {
    if (this.currentIndent.length() >= this.singleIndent.length()) {
      this.currentIndent = this.currentIndent.substring(this.singleIndent.length());
    }
  }

  private void indentOrUnsetTempDisableWriteLine() {
    if (this.tempDisableNewLine) {
      this.unsetTempDisableNewLine();
    } else {
      this.writeRaw(this.currentIndent);
    }
  }

  private void setTempDisableNewLine() {
    this.tempDisableNewLine = true;
  }

  private void unsetTempDisableNewLine() {
    this.tempDisableNewLine = false;
  }

  @Override
  public void writeRaw(String value) {
    if (this.waitingForEntryValue) {
      this.waitingForEntryValue = false;
      super.writeRaw(' ');
    }

    super.writeRaw(value);
  }

  @Override
  public void writeRaw(char value) {
    if (this.waitingForEntryValue) {
      this.waitingForEntryValue = false;
      super.writeRaw(' ');
    }

    super.writeRaw(value);
  }
}
