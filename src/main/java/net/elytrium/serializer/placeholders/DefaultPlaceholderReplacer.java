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

package net.elytrium.serializer.placeholders;

public class DefaultPlaceholderReplacer implements PlaceholderReplacer<String, String> {

  @Override
  public String replace(String value, String[] placeholders, Object... values) {
    return switch (placeholders.length) {
      case 0 -> value;
      case 1 -> values.length == 0 || value.isEmpty() ? value : value.replace(placeholders[0], String.valueOf(values[0]));
      default -> DefaultPlaceholderReplacer.replaceEach(value, placeholders, values);
    };
  }

  // https://github.com/apache/commons-lang/blob/fbfcda9b62654e5f03b404ba9b525f22a458e417/src/main/java/org/apache/commons/lang3/StringUtils.java#L6624
  private static String replaceEach(final String text, final String[] searchList, final Object[] replacementList) {
    final int searchLength = searchList.length;
    final int replacementLength = replacementList.length;

    // make sure lengths are ok, these need to be equal
    if (searchLength != replacementLength) {
      throw new IllegalArgumentException("Search and Replace array lengths don't match: " + searchLength + " vs " + replacementLength);
    }

    // keep track of which still have matches
    final boolean[] noMoreMatchesForReplIndex = new boolean[searchLength];

    // index on index that the match was found
    int textIndex = -1;
    int replaceIndex = -1;
    int tempIndex;

    // index of replace array that will replace the search string found
    // NOTE: logic duplicated below START
    for (int i = 0; i < searchLength; ++i) {
      if (noMoreMatchesForReplIndex[i]) {
        continue;
      }

      tempIndex = text.indexOf(searchList[i]);

      // see if we need to keep searching for this
      if (tempIndex == -1) {
        noMoreMatchesForReplIndex[i] = true;
      } else if (textIndex == -1 || tempIndex < textIndex) {
        textIndex = tempIndex;
        replaceIndex = i;
      }
    }
    // NOTE: logic mostly below END

    // no search strings found, we are done
    if (textIndex == -1) {
      return text;
    }

    int start = 0;

    // get a good guess on the size of the result buffer so it doesn't have to double if it goes over a bit
    int increase = 0;

    // count the replacement text elements that are larger than their corresponding text being replaced
    for (int i = 0; i < searchList.length; ++i) {
      // Make sure all arguments are strings.
      if (!(replacementList[i] instanceof String)) {
        replacementList[i] = String.valueOf(replacementList[i]); // Using String.valueOf() instead of toString() allows the array to contain null.
      }

      final int greater = ((String) replacementList[i]).length() - searchList[i].length();
      if (greater > 0) {
        increase += 3 * greater; // assume 3 matches
      }
    }

    // have upper-bound at 20% increase, then let Java take over
    increase = Math.min(increase, text.length() / 5);

    final StringBuilder buf = new StringBuilder(text.length() + increase);
    while (textIndex != -1) {
      for (int i = start; i < textIndex; ++i) {
        buf.append(text.charAt(i));
      }

      buf.append((String) replacementList[replaceIndex]);

      start = textIndex + searchList[replaceIndex].length();

      textIndex = -1;
      replaceIndex = -1;
      // find the next earliest match
      // NOTE: logic mostly duplicated above START
      for (int i = 0; i < searchLength; i++) {
        if (noMoreMatchesForReplIndex[i]) {
          continue;
        }

        tempIndex = text.indexOf(searchList[i], start);
        // see if we need to keep searching for this
        if (tempIndex == -1) {
          noMoreMatchesForReplIndex[i] = true;
        } else if (textIndex == -1 || tempIndex < textIndex) {
          textIndex = tempIndex;
          replaceIndex = i;
        }
      }
      // NOTE: logic duplicated above END
    }

    final int textLength = text.length();
    for (int i = start; i < textLength; ++i) {
      buf.append(text.charAt(i));
    }

    return buf.toString();
  }
}
