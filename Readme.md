## Test program to demonstrate Kotlin Reflection issue in CDP 7

This program demonstrates how a spark-submit invocation fails due to the presence of 
`hadoop-ozone-filesystem-hadoop3-0.5.0.7.1.4.2-1.jar` in the Hadoop classpath.


### Quick Test

Clone this project and then:

```shell
./gradlew clean build
```

This will build `build/libs/kotlin-reflect-test-1.0-all.jar`.

Move this jar to a Spark environment and run this:

```shell
spark-submit --class com.hitachivantara.reflect.App \
  --master local build/libs/kotlin-reflect-test-1.0-all.jar
```

If you do not have the `hadoop-ozone-filesystem-hadoop3-0.5.0.7.1.4.2-1.jar`
in your `$(hadoop classpath)`, this will work normally without exceptions. 

To simulate the presence of this jar, you can temporarily put it in your `$(hadoop classpath)`

```shell
wget -P $HADOOP_HOME/share/hadoop/common/ \
 "https://repository.cloudera.com/artifactory/libs-release-local/org/apache/hadoop/hadoop-ozone-filesystem-hadoop3/0.5.0.7.1.4.2-1/hadoop-ozone-filesystem-hadoop3-0.5.0.7.1.4.2-1.jar"
```
Note: This is an example location, you can put it anywhere as long as it appears on `$(hadoop classpath)`.

Now run the above `spark-submit` again and you should get the following error:
```log
...
Program arguments: Stranger Things
Exception in thread "main" kotlin.jvm.KotlinReflectionNotSupportedError: Kotlin reflection implementation is not found at runtime. Make sure you have kotlin-reflect.jar in the classpath
	at kotlin.jvm.internal.ClassReference.error(ClassReference.kt:79)
	at kotlin.jvm.internal.ClassReference.isCompanion(ClassReference.kt:77)
	at com.hitachivantara.reflect.SampleFunctionKt.printIsCompanionField(SampleFunction.kt:11)
	at com.hitachivantara.reflect.App.main(App.java:38)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:498)
	at org.apache.spark.deploy.JavaMainApplication.start(SparkApplication.scala:52)
	at org.apache.spark.deploy.SparkSubmit.org$apache$spark$deploy$SparkSubmit$$runMain(SparkSubmit.scala:855)
	at org.apache.spark.deploy.SparkSubmit.doRunMain$1(SparkSubmit.scala:161)
	at org.apache.spark.deploy.SparkSubmit.submit(SparkSubmit.scala:184)
	at org.apache.spark.deploy.SparkSubmit.doSubmit(SparkSubmit.scala:86)
	at org.apache.spark.deploy.SparkSubmit$$anon$2.doSubmit(SparkSubmit.scala:930)
	at org.apache.spark.deploy.SparkSubmit$.main(SparkSubmit.scala:939)
	at org.apache.spark.deploy.SparkSubmit.main(SparkSubmit.scala)
```

### Explanation

* The Ozone fat jar contains the `kotlin-stdlib.jar` but not the `kotlin-reflect.jar`.
* The program prints out the jars the two kotlin classes - `kotlin.jvm.internal.Reflection` and `kotlin.reflect.jvm.internal.KTypeImpl`
  are loaded from and their class loaders. These specific classes were chosen because one (`Reflection`) comes from the
  `kotlin-stdlib.jar` vs the other (`KTypeImpl`) comes from `kotlin-reflect.jar`.
* `spark-submit` main (not our App main class) is launched with the (JVM) `sun.misc.Launcher$AppClassLoader`. 
Thereafter it calls our application class `com.hitachivantara.reflect.App`, but its class loader is a 
Spark provided custom class loader `org.apache.spark.util.MutableURLClassLoader`.
* The hierarchy of these class loaders is `sun.misc.Launcher$AppClassLoader` is the parent of
`org.apache.spark.util.MutableURLClassLoader`. Therefore any class loaded by the child cannot be seen by the parent,
unless it is also present on the parent classloader's classpath which is the Java application classpath -
 `System.getProperty("java.class.path")`.
* It should be noted that our application fat jar (`kotlin-reflect-test-1.0-all.jar`) is not present on the Java class path
as printed by the program. But the Ozone jar (when placed on `$(hadoop classpath)`) is.
* In the clean run case - when the Ozone jar is not present on `$(hadoop classpath)` both the classes are loaded from our application fat jar `kotlin-reflect-test-1.0-all.jar` 
by the `org.apache.spark.util.MutableURLClassLoader`.
* In the case that the Ozone jar is present on the `$(hadoop classpath)`, the `Reflection` class is loaded 
from the Ozone jar by the `AppClassLoader`, but the `KTypeImpl` class is loaded from our application jar
by the `MutableURLClassLoader`.
* When our program invokes the code `.isCompanion`, in the `kotlin.jvm.internal.Reflection` class, there's a line of code:
    ```java
    Class<?> implClass = Class.forName("kotlin.reflect.jvm.internal.ReflectionFactoryImpl");
    ```
    which attempts to load a class from `kotlin-reflect.jar` which is not present on the `AppClassLoader` classpath
    and essentially leads to the exception we see.
* Any variations of the stack trace boils down to the same issue.

### Possible Mitigation
* In QA or Customer environments, put `kotlin-reflect.jar` and `kotlin-stdlib.jar` somewhere on the hadoop or spark classpath. This may need to be done on all the nodes
of the cluster where a multi-node cluster is involved. The Customer will have to agree to this.
    ```shell
    wget -P $HADOOP_HOME/share/hadoop/common \
     "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-reflect/1.5.21/kotlin-reflect-1.5.21.jar"
    
    wget -P $HADOOP_HOME/share/hadoop/common/ \
     "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.5.21/kotlin-stdlib-1.5.21.jar"
    ```
* In Development, you may simply move out the Ozone jar out from its location into a location that's outside
the `$(hadoop classpath)`.