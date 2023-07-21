<img src="https://elytrium.net/src/img/elytrium.webp" alt="Elytrium" align="right">

# Elytrium Java Serializer

[![Join our Discord](https://img.shields.io/discord/775778822334709780.svg?logo=discord&label=Discord)](https://ely.su/discord)

Java Library to (de-)serialize data in various formats (e.g. JSON, YAML)

## Features of Elytrium Java Serializer

- Zero dependencies
- Serialize any class without any modifications
- Auto replacer of placeholders
- Ability to add comments and new lines to the fields

## Basic usage

### Setup

Elytrium Java Serializer is uploaded to the Maven Central repository, so you can just add it as a dependency to your maven or gradle project.

- Maven (pom.xml):
   ```xml
       <dependencies>
           <dependency>
               <groupId>net.elytrium</groupId>
               <artifactId>serializer</artifactId>
               <version>1.0.0</version>
           </dependency>
       </dependencies>
   ```
- Gradle (build.gradle):
   ```groovy
       dependencies {
           implementation("net.elytrium:serializer:1.0.0")
       }
   ```

### Without any modifications

1) Create the class
    ```java
    public static class Settings {
        public String regularField = "regular value";
    }
    ```
2) Create YamlWriter
    ```java
    Settings settings = new Settings();
    YamlWriter writer = new YamlWriter(Files.newBufferedWriter(Path.of("/config.yml")));
    writer.writeNode(settings, null);
    ```
3) Create YamlReader
    ```java
    Settings settings = new Settings();
    YamlReader reader = new YamlReader(Files.newBufferedReader(Path.of("./config.yml")));
    reader.readSerializableObject(settings, Settings.class);
   ```

### With some modifications

1) Create the class that extends YamlSerializable. You can optionally modify the config and call ``YamlSerializable#setConfig(SerializerConfig)`` method. You can safely remove ``this.setConfig(CONFIG)``.
    ```java
    public static class Settings extends YamlSerializable {
        private static final SerializerConfig CONFIG = new SerializerConfig.Builder().build();
    
        Settings() {
          this.setConfig(CONFIG);
        }
    
        public String regularField = "regular value";
    }
    ```
2) Instantiate it.
    ```java
    Settings settings = new Settings();
    settings.reload(Path.of("./config.yml"));
    ```
3) Done!

## Pro usage

### Comments and New lines

```java
   @NewLine(amount = 3)
   @Comment(
           value = {
                   @CommentValue(" SAME_LINE comment Line 1")
           },
           at = Comment.At.SAME_LINE
   )
   @Comment(
           value = {
                   @CommentValue(" SAME_LINE APPEND second comment Line 1"),
                   @CommentValue(type = CommentValue.Type.NEW_LINE),
                   @CommentValue(" SAME_LINE APPEND second comment Line 2")
           },
           at = Comment.At.APPEND
   )
   public String regularField = "regular value";
```

### Final and Transient fields

Final fields - unmodifiable fields that will be saved in the config. \
Transient fields - unmodifiable fields that won't be saved in the config.

```java
  @Final
  public String finalField = "final";
  
  public final String finalFieldToo = "final";
  
  
  @Transient
  public String transientField = "transient";
  
  public transient String transientFieldToo = "transient";
```

### Placeholders

```java
   @RegisterPlaceholders({"PLACEHOLDER", "another-placeholder"})
   public String anotherStringWithPlaceholders = "{PLACEHOLDER} {ANOTHER_PLACEHOLDER}";
```

```java
  Assertions.assertEquals("value 1 value 2", Placeholders.replace(settings.anotherStringWithPlaceholders, "value 1", "value 2"));
```

### Custom Placeholders

Custom placeholder will be instantiated once for one SerializableConfig. \
Placeholders.replace will work even with custom placeholder.

```java
  @RegisterPlaceholders(value = {"PLACEHOLDER", "another-placeholder"}, replacer = StringPlaceholderReplacer.class)
  public String anotherStringWithPlaceholders = "{PLACEHOLDER} {ANOTHER_PLACEHOLDER}";
```


```java
  public class StringPlaceholderReplacer implements PlaceholderReplacer<String> {
    @Override
    public String replace(String value, String[] placeholders, Object... values) {
      for (int i = Math.min(values.length, placeholders.length) - 1; i >= 0; --i) {
        value = value.replace(placeholders[i], String.valueOf(values[i]));
      }
   
      return value;
    }
  }
```

### Custom Serializers

Custom serializers will be instantiated once for one SerializableConfig.

```java
  @Serializer(ExternalClassSerializer.class)
  public ExternalDeserializedClass testClass = new ExternalDeserializedClass();
```

```java
  public static class ExternalDeserializedClass {

    public long x;
    public long y;
    public long z;
    public ExternalNestedClass nestedClass;

    // Public constructor with no args should be created for deserializer to work
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

      // Public constructor with no args should be created for deserializer to work
      public ExternalNestedClass() {

      }

      public ExternalNestedClass(String name) {
        this.name = name;
      }
    }
  }
```

```java
  public static class ExternalClassSerializer extends ClassSerializer<ExternalDeserializedClass, Map<String, Object>> {

    @SuppressWarnings("unchecked")
    protected ExternalClassSerializer() {
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
```

### Custom serializers without @Serializer annotation

In case if you can't add @Serializer annotation or if you have multiple entries with similar classes, you can register the serializer in the config.

```java
  private static final SerializerConfig CONFIG = new SerializerConfig.Builder()
    .registerSerializer(new PathSerializer()).registerSerializer(new ClassSerializer<>(String.class, String.class) {
      @Override
      public String serialize(String from) {
        return from == null ? "" : from;
      }
 
      @Override
      public String deserialize(String from) {
        return from.trim().isEmpty() ? null : from;
      }
    }).build();
```

## Support

If you want to get help or donate to us, you can join our Discord server and talk to us here. \
Invite link: https://elytrium.net/discord