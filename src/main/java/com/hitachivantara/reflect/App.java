package com.hitachivantara.reflect;


import org.apache.spark.sql.SparkSession;

import java.lang.reflect.Field;
import java.text.MessageFormat;

public class App {

    public String sampleField = "sampleField";

    public static void main(String[] args) throws Exception {
        System.out.println("-------------------------------------");
        System.out.println("Java Class Path");
        System.out.println(System.getProperty("java.class.path"));
        System.out.println("-------------------------------------");

        String reflectionClassName = "kotlin.jvm.internal.Reflection";
        Class<?> reflectionClass = Class.forName(reflectionClassName);
        String pattern = "Loaded class {0} \n from jar file {1} \n by Class Loader {2}";
        System.out.println(MessageFormat.format(pattern,
                reflectionClassName,
                reflectionClass.getProtectionDomain().getCodeSource().getLocation(),
                reflectionClass.getClassLoader()));
        System.out.println("-------------------------------------");

        String kTypeImplClassName = "kotlin.reflect.jvm.internal.KTypeImpl";
        Class<?> kTypeClass = Class.forName(kTypeImplClassName);
        System.out.println(MessageFormat.format(pattern,
                kTypeImplClassName,
                kTypeClass.getProtectionDomain().getCodeSource().getLocation(),
                kTypeClass.getClassLoader()));
        System.out.println("-------------------------------------");

        Field sampleField = App.class.getField("sampleField");
        System.out.println("sampleField = " + sampleField);
        System.out.println("Invoking Kotlin reflection ...");
        SampleFunctionKt.main(new String[]{"Stranger Things"});
        SampleFunctionKt.printIsCompanionField(sampleField);
        System.out.println("-------------------------------------");
        System.out.println("              THE END");
        System.out.println("-------------------------------------");

        // The following two lines are needed only to make Spark happy when running with deploy-mode cluster with Yarn.

        // SparkSession sparkSession = SparkSession.builder().appName("kotlin-reflect-test").getOrCreate();
        // sparkSession.stop();
    }
}
