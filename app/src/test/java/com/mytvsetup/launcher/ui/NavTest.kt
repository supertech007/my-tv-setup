package com.mytvsetup.launcher.ui
import org.junit.Assert.*
import org.junit.Test
class NavTest {
 @Test fun swipeThenBack() {
  val nav = Nav(); nav.push(Screen.Swipe)
  assertEquals(listOf(Screen.Home, Screen.Swipe), nav.stack.value)
  assertTrue(nav.back()); assertEquals(listOf(Screen.Home), nav.stack.value)
 }
 @Test fun backAtHomeIsIgnored() {
  val nav = Nav(); assertFalse(nav.back()); assertEquals(listOf(Screen.Home), nav.stack.value)
 }
 @Test fun homeClearsHistory() {
  val nav = Nav(); nav.push(Screen.Settings); nav.push(Screen.AllApps); nav.home()
  assertEquals(listOf(Screen.Home), nav.stack.value)
 }
}
