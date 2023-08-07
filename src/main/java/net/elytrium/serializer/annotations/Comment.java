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

package net.elytrium.serializer.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Creates a comment.
 */
@Documented
@Repeatable(CommentsHolder.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Comment {

  CommentValue[] value();

  /**
   * Comment position.
   */
  At at() default At.PREPEND;

  int commentValueIndent() default -1;

  enum At {

    /**
     * The comment will be placed before the field.
     *
     * <pre> {@code
     *   # Line1
     *   # Line2
     *   regular-field: "regular value"
     * } </pre>
     */
    PREPEND,
    /**
     * The comment will be placed on the same line with the field.
     *
     * <p>The comment text shouldn't have more than one line.
     *
     * <pre> {@code
     *   regular-field: "regular value" # Line1 # Line2
     * } </pre>
     */
    SAME_LINE,
    /**
     * The comment will be placed after the field.
     *
     * <pre> {@code
     *   regular-field: "regular value"
     *   # Line1
     *   # Line2
     * } </pre>
     */
    APPEND
  }
}
