
# PluginLib
A simple utility to allow downloading libraries and relocating them at runtime without having to shade them

# Usage
Simply extend **pluginlib.DependentJavaPlugin** instead of Bukkit's *JavaPlugin*.
```java
package org.example;  
  
import pluginlib.DependentJavaPlugin;  
  
public class ExamplePlugin extends DependentJavaPlugin {  
  
}
```

To add libraries, you have 2 ways:

## Through YML (Preferable)
Define them in your **plugin.yml** through the `runtime-libraries` section:
```yml
name: ExamplePlugin  
main: org.example.ExamplePlugin  
version: 1.0-SNAPSHOT  
authors: [...]  
runtime-libraries:  
  relocation-prefix: 'org.example.libs' # Required if we want to relocate libraries  
  libraries-folder: 'libraries' # If not defined, it will be 'libs'    
  libraries:  
    kotlin:
      groupId: org.jetbrains.kotlin  
      artifactId: kotlin-stdlib  
      version: 1.4.20
```
Here we want to get **Kotlin**'s standard library. 
Unless explicitly specified, **dependencies will be downloaded from Maven Central**. We can specify the repository we want to get from, using the `repository` property:
```yml
   jda:  
      groupId: net.dv8tion  
      artifactId: JDA  
      version: 4.2.0_222
      repository: https://jcenter.bintray.com/
```
This way, JDA will be downloaded from JCenter Bintray's repository.

There are multiple ways to define a library, aside from defining the library properties (group id, artifact id, etc) 

### Through Maven-like XML
For the sake of productivity, and because we love being lazy, you can **straight define the library's XML, much similar to Maven's format**:
```yml
    kotlin:  
      xml: '  
        <dependency>
          <groupId>org.jetbrains.kotlin</groupId> 
          <artifactId>kotlin-stdlib</artifactId> 
          <version>1.4.20</version> 
        </dependency> 
        '
```
This can be especially helpful if you don't want to bother converting the XML to YML, and if you just copied it straight from a website. **Warning: By time, your plugin.yml may get a little unclean if you keep doing this!**

### Defining a URL directly
Some libraries (although rare), do not follow the standard `repository:/group/artifact/version/artifact-version.jar` format, and may require us to explicitly define the URL we want to download the library from.

Let's imagine we want to use **[Aikar's Minecraft Timings](https://github.com/aikar/minecraft-timings)**:
```yml
    timings:  
      url: 'http://repo.aikar.co/nexus/content/groups/aikar/co/aikar/minecraft-timings/1.0.4/minecraft-timings-1.0.4.jar'
      artifactId: 'mc-timings'
      version: 1.0.4
```
**Important note**: When we follow this format, we **must** define the artifact ID and the version.

This can also be useful if what we're looking for is to download a JAR, rather than an actual library.

**Defining in your plugin.yml has a major advantage**: When your plugin loads, libraries will already have been loaded beforehand. 
**This means you can use your library components anywhere in your code, such as static blocks and initializers.**

## Load libraries using code
You can also load libraries programmatically, instead of defining them in your plugin.yml. 

This has a few advantages, such as being able to load your library conditionally (for example, we might need to load MySQL drivers only when the chosen database type is MySQL in our config), however it also has the disadvantage of being unable to use your library until it is explicitly loaded.

For example, if we want to use **[Caffeine](https://github.com/ben-manes/caffeine)**:
```java
package org.example;  
  
import pluginlib.DependentJavaPlugin;  
import pluginlib.PluginLib;  
  
public class ExamplePlugin extends DependentJavaPlugin {  
  
  private static final PluginLib CAFFEINE = PluginLib.builder()  
      .groupId("com.github.ben-manes.caffeine")  
      .artifactId("caffeine")  
      .version("2.8.6")  
      .build();  
  
  static {  
  CAFFEINE.load(ExamplePlugin.class);  
  }  
  
}
``` 

Just like the plugin.yml, we can also parse from XML:
```java
private static final PluginLib CAFFEINE = PluginLib.parseXML(  
  "<dependency>" +  
  "  <groupId>com.github.ben-manes.caffeine</groupId>" +  
  "  <artifactId>caffeine</artifactId>" +  
  "  <version>2.8.6</version>" +  
  "</dependency>"  
).build();
```

And, we can also parse the library from a URL:
```java
private static final PluginLib TIMINGS = PluginLib  
  .fromURL("https://repo.aikar.co/nexus/content/groups/aikar/co/aikar/minecraft-timings/1.0.4/minecraft-timings-1.0.4.jar")  
  .artifactId("timings")  
  .version("1.0.4")  
  .build();  
  
static {  
  TIMINGS.load(ExamplePlugin.class);  
}
```

# Relocating
Relocation is some re-mapping your code and dependencies to make them unique and not conflict with other plugins or dependencies. For example, if we want to relocate `okhttp3`'s library, its paths would change:
`okhttp3.OkHttpClient` -> `org.example.okhttp3.OkHttpClient`.
And the same with all other classes that fall in `okhttp3`'s package. If any plugin also includes okhttp3, we will be sure that there will not be any conflicts between our plugin and the others.

## Through YML
In our library's definition, we simply add the `relocation` section
```yml
  jda:  
    groupId: net.dv8tion  
    artifactId: JDA  
    version: 4.2.0_222  
    repository: https://jcenter.bintray.com/  
    relocation:  
      jda: 'net.dv8tion.jda'
```
Relocation follows the following template:
`<library name>: 'path to replace'`
library name would be `jda`, and in relocation it would appear as **\<relocation prefix>.\<library name>**, as in, **org.example.libs.jda**.

## Through code
We can specify relocation rules through our code in the builder:
```java
package org.example;  
  
import pluginlib.DependentJavaPlugin;  
import pluginlib.PluginLib;  
import pluginlib.Relocation;  
  
public class ExamplePlugin extends DependentJavaPlugin {  
  
  private static final String RELOCATION_PREFIX = "org.example.libs";  
  
  private static final PluginLib CAFFEINE = PluginLib.builder()  
  .groupId("com.github.ben-manes.caffeine")  
  .artifactId("caffeine")  
  .version("2.8.6")  
  .relocate(Relocation.of(RELOCATION_PREFIX, "caffeine", "com#github#benmanes#caffeine"))  
  .build();  
  
  static {  
  CAFFEINE.load(ExamplePlugin.class);  
  }  
}
```


**Very important note**: Relocating using plugin.yml or code **is not enough!** You must tell your build system (Maven or Gradle) to relocate paths in your code as well.

* Relocating in **[Maven](https://maven.apache.org/plugins/maven-shade-plugin/examples/class-relocation.html)**
* Relocating in **[Gradle](https://imperceptiblethoughts.com/shadow/configuration/relocation/)**

Notice that we did not directly put `com.github.benmanes`, and instead `com#github#benmanes`. This way, we will be able to outsmart build system relocation, and will not have this very string literal relocated.

# Full examples
You can view full examples in **maven** and **gradle** branches.
