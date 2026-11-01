package com.mytvsetup.launcher.ui
import org.junit.Assert.assertEquals
import org.junit.Test
class GreetingTest {
 @Test fun morning() { assertEquals("Good morning, Sam", greeting(9, "Sam")) }
 @Test fun afternoon() { assertEquals("Good afternoon, Sam", greeting(13, "Sam")) }
 @Test fun evening() { assertEquals("Good evening, Sam", greeting(20, "Sam")) }
 @Test fun customName() { assertEquals("Good morning, Anu", greeting(9, "Anu")) }
 @Test fun boundaries() {
  assertEquals("Good afternoon, Sam", greeting(12, "Sam"))
  assertEquals("Good evening, Sam", greeting(17, "Sam"))
 }
 @Test fun noNameJustGreets() { assertEquals("Good morning", greeting(9, " ")) }
}
