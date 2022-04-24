package com.hitachivantara.reflect

import java.lang.reflect.Field

fun main(args: Array<String>) {
    val name = " ${args.joinToString()}"
    println("Now on to $name ...")
}

fun printIsCompanionField(field: Field) {
    if (field.type::class.isCompanion) {
        println("field.type::class.isCompanion = true")
    } else {
        println("field.type::class.isCompanion = false")
    }
}