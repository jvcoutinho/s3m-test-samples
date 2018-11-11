/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.util.map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import com.opengamma.util.tuple.Pair;

/**
 * Tests the {@link SoftValueHashMap2} implementation.
 */
@Test
public class SoftValueHashMap2Test {
  
  public void testBasicOperations() {
    final Map2<String, String, String> map = new SoftValueHashMap2<String, String, String> ();
    assertTrue(map.isEmpty());
    assertEquals(map.size(), 0);
    assertEquals(map.put(Pair.of("A", "B"), "Foo".intern()), null);
    assertEquals(map.put("B", "A", "Bar".intern()), null);
    assertFalse(map.isEmpty());
    assertEquals(map.size(), 2);
    assertEquals(map.get(Pair.of("A", "B")), "Foo");
    assertEquals(map.get(Pair.of("B", "A")), "Bar");
    assertEquals(map.get("A", "B"), "Foo");
    assertEquals(map.get("B", "A"), "Bar");
    assertEquals(map.get("X", "Y"), null);
    assertEquals(map.get(Pair.of("X", "Y")), null);
    assertTrue(map.containsKey(Pair.of("B", "A")));
    assertTrue(map.containsKey("A", "B"));
    assertFalse(map.containsKey("X", "Y"));
    assertFalse(map.containsKey(Pair.of("X", "Y")));
    assertTrue(map.containsValue("Bar".intern()));
    assertTrue(map.containsValue("Foo".intern()));
    assertFalse(map.containsValue(new String("Foo")));
    assertFalse(map.containsValue("Cow".intern()));
    map.clear();
    assertTrue(map.isEmpty());
    assertEquals(map.size(), 0);
    assertFalse(map.containsKey(Pair.of("B", "A")));
    assertFalse(map.containsKey("A", "B"));
    assertFalse(map.containsValue("Bar"));
    assertFalse(map.containsValue("Foo"));
  }
  
  public void testRemove() {
    final Map2<String, String, String> map = new SoftValueHashMap2<String, String, String>();
    map.put(Pair.of("A", "B"), "Foo");
    map.put("B", "A", "Bar");
    assertEquals(map.remove(Pair.of("B", "A")), "Bar");
    assertEquals(map.size(), 1);
    assertEquals(map.remove(Pair.of("B", "A")), null);
    assertEquals(map.remove(Pair.of("X", "Y")), null);
    assertEquals(map.size(), 1);
    assertEquals(map.remove("A", "B"), "Foo");
    assertEquals(map.size(), 0);
    assertEquals(map.remove("A", "B"), null);
  }
  
  public void testPutAll() {
    final Map2<String, String, String> map1 = new SoftValueHashMap2<String, String, String>();
    map1.put(Pair.of("A", "B"), "Foo");
    map1.put("B", "A", "Bar");
    final Map2<String, String, String> map2 = new SoftValueHashMap2<String, String, String>();
    map2.put(Pair.of("X", "Y"), "Cow");
    map2.put("Y", "X", "Dog");
    map1.putAll(map2);
    assertTrue(map1.containsKey("X", "Y"));
    assertTrue(map1.containsKey(Pair.of("X", "Y")));
  }

  public void testPutIfAbsent() {
    final Map2<String, String, String> map = new SoftValueHashMap2<String, String, String>();
    assertEquals(map.put("A", "B", "Foo"), null);
    assertEquals(map.put(Pair.of("B", "A"), "Bar"), null);
    assertEquals(map.put(Pair.of("A", "B"), "Cow"), "Foo");
    assertEquals(map.put("B", "A", "Dog"), "Bar");
    assertEquals(map.putIfAbsent("A", "B", "Foo"), "Cow");
    assertEquals(map.putIfAbsent("B", "A", "Bar"), "Dog");
    assertEquals(map.get("A", "B"), "Cow");
    assertEquals(map.get("B", "A"), "Dog");
  }
  
}
