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

package net.elytrium.serializer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.Final;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.annotations.OverrideNameStyle;
import net.elytrium.serializer.annotations.RegisterPlaceholders;
import net.elytrium.serializer.annotations.Serializer;
import net.elytrium.serializer.custom.ClassSerializer;
import net.elytrium.serializer.language.object.YamlSerializable;
import net.elytrium.serializer.language.writer.YamlWriter;
import net.elytrium.serializer.placeholders.Placeholders;
import net.elytrium.serializer.placeholders.DefaultPlaceholderReplacer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SerializerTest {

  @Test
  void placeholdersTest() {
    String stringWithPlaceholders = "{PLACEHOLDER1} {PLACEHOLDER2} {PLACEHOLDER3}";
    Placeholders.addPlaceholders(stringWithPlaceholders, new DefaultPlaceholderReplacer(), "placeholder3", "PLACEHOLDER1", "{PLACEHOLDER2}");
    Assertions.assertEquals("2 3 1", Placeholders.replace(stringWithPlaceholders, "1", "2", "3"));
    Placeholders.removePlaceholders(stringWithPlaceholders);
  }

  @Test
  public void testSerializeYaml() {
    StringWriter stringWriter = new StringWriter();
    BufferedWriter printWriter = new BufferedWriter(stringWriter);

    YamlWriter writer = new YamlWriter(printWriter);
    writer.writeNode(new Settings(), null);
    try {
      printWriter.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    System.out.println(stringWriter);
  }

  @Test
  void testConfig() throws IOException {
    Path configWithoutPrefixPath = Files.createTempFile("config", ".yml");
    this.processTempFile(configWithoutPrefixPath);
    /*
    try (InputStream inputStream = SerializerTest.class.getResourceAsStream("/config.yml")) {
      java.util.Objects.requireNonNull(inputStream);
      Files.copy(inputStream, configWithoutPrefixPath);
    }
    */
    Settings settings = new Settings();
    for (int i = 0; i < 4; ++i) {
      if (settings.reload(configWithoutPrefixPath) == LoadResult.CONFIG_NOT_EXISTS) {
        Assertions.assertEquals(0, i);
      }
    }

    Assertions.assertEquals("final value", settings.finalField);
    Assertions.assertEquals("regular \"value\"", settings.regularField);
    Assertions.assertEquals((float) Math.PI, settings.regularFloatField);
    Assertions.assertEquals(Math.E, settings.regularDoubleField);
    Assertions.assertEquals(RegularEnum.TRUE, settings.enumField);
    Assertions.assertEquals("string value", settings.prepend.stringField);
    Assertions.assertEquals("string value", settings.prepend.fieldWithCommentAtSameLine);
    Assertions.assertEquals("string value", settings.prepend.stringField);
    Assertions.assertEquals("string value", settings.prepend.fieldWithCommentAtSameLine);
    Assertions.assertEquals("string value", settings.prepend.sameLine.append.field1);
    Assertions.assertEquals("string value", settings.prepend.sameLine.append.field2);
    Assertions.assertEquals(
        "This is string with placeholders",
        Placeholders.replace(settings.stringWithPlaceholders, "string", "placeholders")
    );
    Assertions.assertEquals(
        "This is string with placeholders",
        Placeholders.replace(settings.stringWithPlaceholders2, "placeholders", "string")
    );
    Assertions.assertEquals("value 1 value 2", Placeholders.replace(settings.anotherStringWithPlaceholders, "value 1", "value 2"));
    Assertions.assertEquals("{PLACEHOLDER} {ANOTHER_PLACEHOLDER}", settings.anotherStringWithPlaceholders);
    Assertions.assertEquals(2, settings.prepend.sameLine.append.nestedLists.size());
    Assertions.assertEquals(2, settings.objectListWithMaps.size());
    Assertions.assertEquals(3, settings.listOfString2ObjectMap.size());
    Assertions.assertEquals(3, settings.chaosMapList.size());
    Assertions.assertEquals(2, settings.chaosMap.size());

    settings.int2StringMap.forEach((key, value) -> {
      Assertions.assertEquals(Integer.class, key.getClass());
      Assertions.assertEquals(String.class, value.getClass());
    });

    this.assertNodeSequence(settings.nodeTest.nodeSeqMap.get("1"), "some value", 1234, "value", 10);
    this.assertNodeSequence(settings.nodeTest.nodeSeqMap.get("b"), "2nd string", 1234, "value", 10);
    this.assertNodeSequence(settings.nodeTest.nodeSeqMap.get("c::c"), "3rd string", 4321, "value", 10);
    this.assertNodeSequence(settings.nodeTest.nodeSeqList.get(0), "first", 100, "value", 10);
    this.assertNodeSequence(settings.nodeTest.nodeSeqList.get(1), "second", 200, "value", 10);

    Assertions.assertEquals(new Date(123456789L), settings.dateField);
    Assertions.assertEquals(Paths.get("test.3gp"), settings.pathField);
    Assertions.assertEquals(2, settings.listField.size());
    Assertions.assertEquals("test", settings.listField.get(0));
    Assertions.assertEquals(123, (Long) settings.listField.get(1));
    Assertions.assertEquals("test", settings.numeric1234Field);
    Assertions.assertEquals(0, settings.changedNameField.test);
    Assertions.assertEquals(2, settings.createdTestClass.stringsList.size());
    Assertions.assertEquals(0, settings.testClass.x);
    Assertions.assertEquals(0, settings.testClass.y);
    Assertions.assertEquals(0, settings.testClass.z);
    Assertions.assertEquals("sample-name", settings.testClass.nestedClass.name);

    RegularEnum testEnumField = RegularEnum.ENUM_VALUE_1;
    Date testDateField = new Date(223456789L);
    Path testPathField = Paths.get("test2.3gp");

    settings.enumField = testEnumField;
    settings.dateField = testDateField;
    settings.pathField = testPathField;

    settings.save(configWithoutPrefixPath);

    Settings newSettings = new Settings();

    LoadResult result = newSettings.load(configWithoutPrefixPath);
    Assertions.assertEquals(LoadResult.SUCCESS, result);

    this.compareFiles("config.yml", configWithoutPrefixPath);

    Assertions.assertEquals(testEnumField, newSettings.enumField);
    Assertions.assertEquals(testDateField, newSettings.dateField);
    Assertions.assertEquals(testPathField, newSettings.pathField);
  }

  @SuppressWarnings("SameParameterValue")
  private void assertNodeSequence(Settings.NodeTest.TestNodeSequence node, String expectedString, int expectedInteger, String a, int b) {
    Assertions.assertEquals(44, node.ignored);
    Assertions.assertEquals("final", node.finalField);
    Assertions.assertEquals(expectedString, node.someString);
    Assertions.assertEquals(expectedInteger, node.someInteger);
    Assertions.assertEquals(a, node.otherNodeSeq.a);
    Assertions.assertEquals(b, node.otherNodeSeq.b);
  }

  private void processTempFile(Path path) {
    File file = path.toFile();
    if (!file.delete()) { // We don't need an empty temp file, we need only path.
      throw new IllegalStateException("File must be deleted.");
    }
    file.deleteOnExit();
  }

  @SuppressWarnings("SameParameterValue")
  private void compareFiles(String finalFileName, Path currentFilePath) throws IOException {
    try (InputStream finalConfig = SerializerTest.class.getResourceAsStream("/" + finalFileName)) {
      if (finalConfig == null) {
        throw new IllegalStateException("Stream cannot be null.");
      } else {
        Assertions.assertEquals(
            new String(finalConfig.readAllBytes(), StandardCharsets.UTF_8),
            Files.readString(currentFilePath)
        );
      }
    }
  }

  private static <K, V> Map<K, V> map(K k1, V v1) {
    Map<K, V> map = new LinkedHashMap<>(1);
    map.put(k1, v1);
    return map;
  }

  private static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2) {
    Map<K, V> map = new LinkedHashMap<>(2);
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  @SuppressWarnings("SameParameterValue")
  private static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3) {
    Map<K, V> map = new LinkedHashMap<>(3);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    return map;
  }

  @SuppressWarnings("SameParameterValue")
  private static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    LinkedHashMap<K, V> map = new LinkedHashMap<>(4);
    map.put(k1, v1);
    map.put(k2, v2);
    map.put(k3, v3);
    map.put(k4, v4);
    return map;
  }

  /*
  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, V> map(Object... values) {
    int capacity = values.length / 2;
    if (capacity != values.length / 2.0) {
      throw new IllegalArgumentException("Invalid arguments amount!");
    }

    Map<K, V> result = new LinkedHashMap<>(capacity);
    for (int i = 0; i < values.length; ++i) {
      result.put((K) values[i], (V) values[++i]);
    }

    return result;
  }
  */

  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
  public static class Settings extends YamlSerializable {

    private static final SerializerConfig CONFIG = new SerializerConfig.Builder().registerSerializer(new ClassSerializer<>(String.class, String.class) {

      @Override
      public String serialize(String from) {
        return from == null ? "" : from;
      }

      @Override
      public String deserialize(String from) {
        return from.trim().isEmpty() ? null : from;
      }
    }).registerSerializer(new PathSerializer()).build();

    Settings() {
      super(Settings.CONFIG);
    }

    @Final
    public String finalField = "final value";

    public String regularField = "regular \"value\"";

    public float regularFloatField = (float) Math.PI;
    public double regularDoubleField = Math.E;

    public RegularEnum enumField = RegularEnum.TRUE;

    @RegisterPlaceholders({"{TEST}", "test2"})
    public String stringWithPlaceholders = "This is {TEST} with {TEST2}";

    @RegisterPlaceholders({"test2", "test"})
    public String stringWithPlaceholders2 = "This is {TEST} with {TEST2}";

    @RegisterPlaceholders({"PLACEHOLDER", "another-placeholder"})
    public String anotherStringWithPlaceholders = "{PLACEHOLDER} {ANOTHER_PLACEHOLDER}";

    public Map<Integer, String> int2StringMap = SerializerTest.map(1, "v1", 15555, "v2", 44, "v3");

    public final List<Object> objectListWithMaps = Arrays.asList(
        SerializerTest.map("test", SerializerTest.map("test2", "test")),
        SerializerTest.map("test", SerializerTest.map("test2", "test"))
    );
    public final List<Map<String, Object>> listOfString2ObjectMap = Arrays.asList(
        SerializerTest.map("test", SerializerTest.map("test2", "test")),
        SerializerTest.map("test", SerializerTest.map("test2", "test")),
        SerializerTest.map("the", SerializerTest.map("slow", "rush"))
    );
    public List<Map<String, Map<String, List<NodeTest.TestNodeSequence>>>> chaosMapList = Arrays.asList(
        SerializerTest.map(
            "test-1", SerializerTest.map("test-1-1",
                Arrays.asList(new NodeTest.TestNodeSequence()),
                "test-1-2", Arrays.asList(new NodeTest.TestNodeSequence())
            ),
            "test-2", SerializerTest.map("test-2-1", Arrays.asList(new NodeTest.TestNodeSequence()))
        ),
        SerializerTest.map("porsh", SerializerTest.map("okean", Arrays.asList(new NodeTest.TestNodeSequence()))),
        SerializerTest.map("4e", SerializerTest.map("naJlulll", Arrays.asList(new NodeTest.TestNodeSequence())))
    );
    public Map<String, Map<String, List<String>>> chaosMap = SerializerTest.map(
        "test-1", SerializerTest.map("test-1-1", Arrays.asList("element"), "test-1-2", Arrays.asList("element")),
        "test-2", SerializerTest.map("test-2-1", Arrays.asList("element"))
    );

    @Comment({
        @CommentValue(" PREPEND comment Line 1"),
        @CommentValue(" PREPEND comment Line 2")
    })
    @OverrideNameStyle(field = NameStyle.CAMEL_CASE, node = NameStyle.COBOL_CASE)
    public Prepend prepend = new Prepend();

    @Comment({
        @CommentValue(" PREPEND class comment")
    })
    public static class Prepend {

      @OverrideNameStyle(field = NameStyle.CAMEL_CASE, node = NameStyle.COBOL_CASE)
      public String stringField = "string value";

      @NewLine
      @Comment(
          value = {
              @CommentValue(" fieldWithCommentAtSameLine comment"),
              @CommentValue(" You can still see this comment, but why do you need this?")
          },
          at = Comment.At.SAME_LINE
      )
      public String fieldWithCommentAtSameLine = "string value";

      @NewLine(amount = 3)
      @Comment(
          value = {
              @CommentValue(" SAME_LINE comment Line 1")
          },
          at = Comment.At.SAME_LINE
      )
      @Comment(
          value = {
              @CommentValue(" sameLine APPEND second comment Line 1"),
              @CommentValue(type = CommentValue.Type.NEW_LINE),
              @CommentValue(" sameLine APPEND second comment Line 2")
          },
          at = Comment.At.APPEND
      )
      public SameLine sameLine = new SameLine();

      public static class SameLine {

        public Append append = new Append();

        public static class Append {

          @Comment(
              value = {
                  @CommentValue(" field1 APPEND comment"),
                  @CommentValue(" Second line")
              },
              at = Comment.At.APPEND
          )
          public String field1 = "string value";

          @Comment(
              value = {
                  @CommentValue(" field2 PREPEND comment"),
                  @CommentValue(" Line 2")
              },
              at = Comment.At.PREPEND
          )
          public String field2 = "string value";
          public List<List<List<String>>> nestedLists = Arrays.asList(
              Arrays.asList(
                  Arrays.asList("0", "1", "2"),
                  Arrays.asList("a", "b", "c")
              ),
              Arrays.asList(
                  Arrays.asList("3", "4", "5"),
                  Arrays.asList("d", "e", "f")
              )
          );
        }
      }
    }

    public NodeTest nodeTest = new NodeTest();

    public static class NodeTest {

      public Map<String, TestNodeSequence> nodeSeqMap = SerializerTest.map(
          "1", new TestNodeSequence(),
          "b", new TestNodeSequence("2nd string"),
          "c::c", new TestNodeSequence("3rd string", 4321)
      );

      public List<TestNodeSequence> nodeSeqList = Arrays.asList(new TestNodeSequence("first", 100), new TestNodeSequence("second", 200));

      public static class TestNodeSequence {

        @Final
        public String finalField = "final";

        public transient int ignored = 44;

        public String someString = "some value";

        public int someInteger = 1234;

        public TestNodeSequence.OtherNodeSeq otherNodeSeq = new OtherNodeSeq();

        public TestNodeSequence() {

        }

        public TestNodeSequence(String s) {
          this.someString = s;
        }

        public TestNodeSequence(String s, int i) {
          this.someString = s;
          this.someInteger = i;
        }

        public static class OtherNodeSeq {

          public String a = "value";
          public int b = 10;
        }
      }
    }

    @NewLine(amount = 2)
    @Serializer(DateSerializer.class)
    public Date dateField = new Date(123456789L);

    public Path pathField = Paths.get("test.3gp");

    public List<Object> listField = Arrays.asList("test", 123);

    public String numeric1234Field = "test";

    public ChangedNameClass changedNameField = new ChangedNameClass();

    public static class ChangedNameClass {

      public int test;
    }

    @Serializer(ExternalClassSerializer.class)
    public ExternalDeserializedClass testClass = new ExternalDeserializedClass();

    public CreatedTestClass createdTestClass = new CreatedTestClass();
  }

  public static class CreatedTestClass {

    public List<String> stringsList = Arrays.asList("test-1", "test-2");
  }

  public static class ExternalDeserializedClass {

    public long x;
    public long y;
    public long z;
    public ExternalNestedClass nestedClass;

    public ExternalDeserializedClass() {
      this.x = 0;
      this.y = 0;
      this.z = 0;
      this.nestedClass = new ExternalNestedClass("sample-name");
    }

    public ExternalDeserializedClass(long x, long y, long z, String name) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.nestedClass = new ExternalNestedClass(name);
    }

    public static class ExternalNestedClass {

      private String name;

      public ExternalNestedClass() {

      }

      public ExternalNestedClass(String name) {
        this.name = name;
      }
    }
  }

  public static class ExternalClassSerializer extends ClassSerializer<ExternalDeserializedClass, Map<String, Object>> {

    @SuppressWarnings("unchecked")
    public ExternalClassSerializer() {
      super(ExternalDeserializedClass.class, (Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    @Override
    public Map<String, Object> serialize(ExternalDeserializedClass from) {
      return SerializerTest.map("field-x", from.x, "field-y", from.y, "field-z", from.z, "nested-class-name", from.nestedClass.name);
    }

    @Override
    public ExternalDeserializedClass deserialize(Map<String, Object> from) {
      return new ExternalDeserializedClass(
          (long) from.get("field-x"),
          (long) from.get("field-y"),
          (long) from.get("field-z"),
          (String) from.get("nested-class-name")
      );
    }
  }

  public static class DateSerializer extends ClassSerializer<Date, Long> {

    public DateSerializer() {
      super(Date.class, Long.class);
    }

    @Override
    public Long serialize(Date from) {
      return from.getTime() * 10L;
    }

    @Override
    public Date deserialize(Long from) {
      return new Date(from / 10L);
    }
  }

  public static class PathSerializer extends ClassSerializer<Path, String> {

    public PathSerializer() {
      super(Path.class, String.class);
    }

    @Override
    public String serialize(Path from) {
      return from.toString();
    }

    @Override
    public Path deserialize(String from) {
      return Paths.get(from);
    }
  }

  private enum RegularEnum {

    ENUM_VALUE_1,
    TRUE,
    FALSE,
  }
}
