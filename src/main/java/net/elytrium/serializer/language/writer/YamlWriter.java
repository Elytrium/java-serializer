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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedWriter;
import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.elytrium.serializer.SerializerConfig;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.YamlStringStyle;
import net.elytrium.serializer.language.object.YamlSerializable;

public class YamlWriter extends AbstractWriter {

  private final YamlSerializable yamlSerializable;

  private boolean first = true;
  private boolean tempDisableNewLine;

  private String singleIndent = "  ";
  private String currentIndent = "";
  private boolean waitingForEntryValue;

  public YamlWriter(SerializerConfig config, BufferedWriter writer) {
    super(config, writer);
    this.yamlSerializable = null;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public YamlWriter(SerializerConfig config, BufferedWriter writer, YamlSerializable serializable) {
    super(config, writer);
    this.yamlSerializable = serializable;
  }

  public YamlWriter(BufferedWriter writer) {
    super(writer);
    this.yamlSerializable = null;
  }

  public void setSingleIndent(String singleIndent) {
    this.singleIndent = singleIndent;
  }

  @Override
  public void writeCommentStart(@Nullable Field owner, Comment.At at) {
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
  public void writeCommentValueIndent(@Nullable Field owner, Comment.At at, int indent) {
    synchronized (this) {
      for (int i = 0; i < indent; ++i) {
        this.writeRaw(' ');
      }
    }
  }

  @Override
  public void writeCommentEnd(@Nullable Field owner, Comment.At at) {
    synchronized (this) {
      if (at != Comment.At.SAME_LINE) {
        this.writeLine();
      }
    }
  }

  @Override
  public void writeNodeName(@Nullable Field owner, String value) {
    synchronized (this) {
      this.writeIndent();
      this.writeString0(owner, value, true);
    }
  }

  @Override
  public void writeEmptyMap(@Nullable Field owner) {
    synchronized (this) {
      this.writeRaw("{}");
    }
  }

  @Override
  public void writeBeginMap(@Nullable Field owner) {
    synchronized (this) {
      this.writeBeginCommon();
    }
  }

  @Override
  public void writeMapPreCommentEntryJoin(@Nullable Field owner) {

  }

  @Override
  public void writeMapPostCommentEntryJoin(@Nullable Field owner) {
    synchronized (this) {
      this.writeLine();
    }
  }

  @Override
  public void writeMapEntryEnd(@Nullable Field owner) {

  }

  @Override
  public void writeEndMap(@Nullable Field owner) {
    synchronized (this) {
      this.removeIndent();
    }
  }

  @Override
  public void writeEmptyList(@Nullable Field owner) {
    synchronized (this) {
      this.writeRaw("[]");
    }
  }

  @Override
  public void writeBeginList(@Nullable Field owner) {
    synchronized (this) {
      this.writeBeginCommon();
    }
  }

  @Override
  public void writeListEntry(@Nullable Field owner, Object entry) {
    synchronized (this) {
      this.writeIndent();

      this.writeRaw("- ");
      this.setTempDisableNewLine();
      this.writeNode(entry, null);
      this.unsetTempDisableNewLine();
    }
  }

  @Override
  public void writeListEntryJoin(@Nullable Field owner) {
    synchronized (this) {
      this.writeLine();
    }
  }

  @Override
  public void writeListEntryEnd(@Nullable Field owner) {

  }

  @Override
  public void writeEndList(@Nullable Field owner) {
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
  public void writeString(@Nullable Field owner, String value) {
    synchronized (this) {
      this.writeString0(owner, value, false);
    }
  }

  @Override
  public void writeCharacter(@Nullable Field owner, char value) {
    synchronized (this) {
      boolean shouldUseQuotes = this.shouldUseQuotes(value, true);
      if (shouldUseQuotes) {
        this.writeRaw('"');
      }
      this.writeCharacter0(value, true);
      if (shouldUseQuotes) {
        this.writeRaw('"');
      }
    }
  }

  private boolean shouldUseQuotes(char[] characters, boolean avoidSpecial) {
    for (char character : characters) {
      if (this.shouldUseQuotes(character, avoidSpecial)) {
        return true;
      }
    }

    return false;
  }

  private boolean shouldUseQuotes(char value, boolean avoidSpecial) {
    return (avoidSpecial && (value == ' ' || value == '#' || value == '"'))
           || value == '\0' || value == '\u0007' || value == '\b' || value == '\t' || value == '\n' || value == '\u000B' || value == '\f' || value == '\r'
           || value == '\u001B' || value == '\'' || value == '\\' || value == '\u0085' || value == '\u00A0' || value == '\u2028' || value == '\u2029';
  }

  private void writeString0(@Nullable Field owner, String value, boolean nodeName) {
    if (value.isEmpty()) {
      if (nodeName) {
        this.writeRaw("\"\":");
        this.waitingForEntryValue = true;
      } else {
        this.writeRaw("\"\"");
      }
    } else {
      StringStyle style = null;
      if (nodeName) {
        style = StringStyle.NOT_QUOTED;
      } else {
        YamlStringStyle styleAnnotation = null;
        if (owner != null) {
          styleAnnotation = owner.getAnnotation(YamlStringStyle.class);
        }

        if (this.yamlSerializable != null) {
          StringStyle savedStyle = this.yamlSerializable.getStringStyle(owner);
          if (savedStyle != null) {
            style = savedStyle;
          }
        }

        if (styleAnnotation != null && (style == null || styleAnnotation.override())) {
          style = styleAnnotation.value();
        }

        if (style == null) {
          style = StringStyle.DOUBLE_QUOTED;
        }
      }

      style.write(this, value);

      if (nodeName) {
        this.writeRaw(':');
        this.waitingForEntryValue = true;
      }
    }
  }

  private void writeCharacters(char[] characters) {
    this.writeCharacters(characters, true, true, false, true);
  }

  private void writeCharacters(char[] characters, boolean escapeNewLine, boolean doubleNewLine, boolean firstNewLine, boolean escapeSpecial) {
    this.addIndent();

    if (firstNewLine) {
      this.writeLineAndIndent();
    }

    char[] lineSeparatorChars = this.config.getLineSeparatorChars();
    int lineSeparatorCharsCaught = 0;

    char highSurrogate = 0;
    for (char character : characters) {
      if (!escapeNewLine) {
        if (character == AbstractWriter.NEW_LINE || character == lineSeparatorChars[lineSeparatorCharsCaught]) {
          if (character == AbstractWriter.NEW_LINE || ++lineSeparatorCharsCaught == lineSeparatorChars.length) {
            lineSeparatorCharsCaught = 0;
            this.writeLineAndIndent();

            if (doubleNewLine) {
              this.writeLineAndIndent();
            }
          }

          continue;
        } else {
          for (int i = 0; i < lineSeparatorCharsCaught; i++) {
            this.writeCharacter0(lineSeparatorChars[i], escapeSpecial);
          }

          lineSeparatorCharsCaught = 0;
        }
      }

      if (highSurrogate != 0) {
        this.writeCharacter0(Character.toCodePoint(highSurrogate, character), escapeSpecial);
        highSurrogate = 0;
      } else if (Character.isHighSurrogate(character)) {
        highSurrogate = character;
      } else {
        this.writeCharacter0(character, escapeSpecial);
      }
    }

    if (highSurrogate != 0) {
      this.writeCharacter0(highSurrogate, escapeSpecial);
    }
    this.removeIndent();
  }

  private void writeCharacter0(int value, boolean escapeSpecial) {
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
      case '\\' -> this.writeRaw("\\\\");
      case '\u0085' -> this.writeRaw("\\N");
      case '\u00A0' -> this.writeRaw("\\_");
      case '\u2028' -> this.writeRaw("\\L");
      case '\u2029' -> this.writeRaw("\\P");
      case '"' -> {
        if (escapeSpecial) {
          this.writeRaw("\\\"");
        } else {
          this.writeRaw('"');
        }
      }
      default -> {
        if (Character.isIdentifierIgnorable(value) && !this.config.isAllowUnicode()) {
          if (value <= 0xFF) {
            this.writeRaw("\\x");
            String result = "0" + Integer.toString(value, 16);
            this.writeRaw(result.substring(result.length() - 2));
            break;
          } else if (Character.charCount(value) == 2) {
            this.writeRaw("\\U");
            String result = "000" + Long.toHexString(value);
            this.writeRaw(result.substring(result.length() - 8));
            break;
          } else {
            this.writeRaw("\\u");
            String result = "000" + Integer.toString(value, 16);
            this.writeRaw(result.substring(result.length() - 4));
            break;
          }
        }

        for (char character : Character.toChars(value)) {
          this.writeRaw(character);
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

  private void writeIndent() {
    if (this.tempDisableNewLine) {
      this.unsetTempDisableNewLine();
    } else {
      this.writeRaw(this.currentIndent);
    }
  }

  private void writeLineAndIndent() {
    this.writeLine();
    this.writeIndent();
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

  public enum StringStyle {

    /**
     * Only single line, not quoted,
     * but {@code "} will be used if special character was found
     */
    NOT_QUOTED((writer, string) -> {
      char[] characters = string.toCharArray();
      boolean shouldUseQuotes = writer.shouldUseQuotes(characters, true);

      if (shouldUseQuotes) {
        writer.writeRaw('"');
      }

      writer.writeCharacters(characters);

      if (shouldUseQuotes) {
        writer.writeRaw('"');
      }
    }),
    /**
     * Preferably single line, quoted with single quote {@code '},
     * but {@code "} will be used if special character was found
     */
    SINGLE_QUOTED((writer, string) -> {
      char[] characters = string.toCharArray();
      boolean shouldUseQuotes = writer.shouldUseQuotes(characters, false);

      if (shouldUseQuotes) {
        writer.writeRaw('"');
      } else {
        writer.writeRaw('\'');
      }

      writer.writeCharacters(string.replace("'", "''").toCharArray(), false, true, false, shouldUseQuotes);

      if (shouldUseQuotes) {
        writer.writeRaw('"');
      } else {
        writer.writeRaw('\'');
      }
    }),
    /**
     * Preferably single line, line separator will be escaped, quoted with double quote {@code "}.
     */
    DOUBLE_QUOTED((writer, string) -> {
      writer.writeRaw('"');
      writer.writeCharacters(string.toCharArray(), true, true, false, true);
      writer.writeRaw('"');
    }),
    /**
     * Preferably single line, line separators will make new lines, quoted with double quote {@code "}.
     */
    DOUBLE_QUOTED_MULTILINE((writer, string) -> {
      writer.writeRaw('"');
      writer.writeCharacters(string.toCharArray(), false, true, false, true);
      writer.writeRaw('"');
    }),
    /**
     * Preferably multi line, quoted with {@code >}, new lines will be replaced with spaces,
     * single new line at the end will be kept,
     * but {@code >-} will be used if new lines were found at the end of the string,
     * and {@code >+} will be used if multiple new lines were found at the end of the string.
     */
    MULTILINE_FOLDED_AUTO_CLIPPED((writer, string) -> {
      if (!string.endsWith(writer.config.getLineSeparator())) {
        writer.writeRaw(">-");
      } else if (string.endsWith(writer.config.getDoubledLineSeparator())) {
        writer.writeRaw(">+");
      } else {
        writer.writeRaw(">");
      }

      writer.writeCharacters(string.toCharArray(), false, true, true, false);
    }),
    /**
     * Preferably multi line, quoted with {@code >-}, new lines will be replaced with spaces,
     * can't contain new lines at the end. Throws an exception if new line caught at the end.
     */
    MULTILINE_FOLDED_STRIPPED((writer, string) -> {
      if (string.endsWith(writer.config.getLineSeparator())) {
        throw new IllegalStateException("This string type can't contain new lines at the end");
      }

      writer.writeRaw(">-");
      writer.writeCharacters(string.toCharArray(), false, true, true, false);
    }),
    /**
     * Preferably multi line, quoted with {@code >+}, new lines will be replaced with spaces,
     * all new lines from end will be kept,
     * but {@code >-} will be used if new lines were found at the end of the string.
     */
    MULTILINE_FOLDED_AUTO_KEPT((writer, string) -> {
      if (!string.endsWith(writer.config.getLineSeparator())) {
        writer.writeRaw(">-");
      } else {
        writer.writeRaw(">+");
      }

      writer.writeCharacters(string.toCharArray(), false, true, true, false);
    }),
    /**
     * Preferably multi line, quoted with {@code |}, single new line at the end will be kept,
     * but {@code |-} will be used if no new lines were found at the end of the string.
     */
    MULTILINE_LITERAL_AUTO_CLIPPED((writer, string) -> {
      if (!string.endsWith(writer.config.getLineSeparator())) {
        writer.writeRaw("|-");
      } else if (string.endsWith(writer.config.getDoubledLineSeparator())) {
        writer.writeRaw("|+");
      } else {
        writer.writeRaw("|");
      }

      writer.writeCharacters(string.toCharArray(), false, false, true, false);
    }),
    /**
     * Preferably multi line, quoted with {@code |},
     * can't contain new lines at the end. Throws an exception if new line caught at the end.
     */
    MULTILINE_LITERAL_STRIPPED((writer, string) -> {
      if (string.endsWith(writer.config.getLineSeparator())) {
        throw new IllegalStateException("This string type can't contain new lines at the end");
      }

      writer.writeRaw("|-");
      writer.writeCharacters(string.toCharArray(), false, false, true, false);
    }),
    /**
     * Preferably multi line, quoted with {@code |+}, all new lines from end will be kept,
     * but {@code |-} will be used if no new lines were found at the end of the string.
     */
    MULTILINE_LITERAL_AUTO_KEPT((writer, string) -> {
      if (!string.endsWith(writer.config.getLineSeparator())) {
        writer.writeRaw("|- ");
      } else {
        writer.writeRaw("|+ ");
      }

      writer.writeCharacters(string.toCharArray(), false, false, true, false);
    });

    private final BiConsumer<YamlWriter, String> writeFunction;

    StringStyle(BiConsumer<YamlWriter, String> writeFunction) {
      this.writeFunction = writeFunction;
    }

    public void write(YamlWriter writer, String string) {
      this.writeFunction.accept(writer, string);
    }
  }
}
