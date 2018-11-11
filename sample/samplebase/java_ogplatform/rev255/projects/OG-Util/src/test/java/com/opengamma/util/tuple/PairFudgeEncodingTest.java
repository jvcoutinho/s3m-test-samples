/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.util.tuple;

import static org.testng.Assert.assertEquals;

import javax.time.calendar.LocalDate;

import org.fudgemsg.FudgeMsg;
import org.testng.annotations.Test;

import com.opengamma.id.ExternalId;
import com.opengamma.id.ExternalIdBundle;
import com.opengamma.id.UniqueId;
import com.opengamma.util.test.AbstractFudgeBuilderTestCase;
import com.opengamma.util.time.Tenor;

/**
 * Test Fudge encoding.
 */
@Test
public class PairFudgeEncodingTest extends AbstractFudgeBuilderTestCase {

  public void test_OO_Bundle() {
    Pair<String, ExternalIdBundle> object = Pair.of("Hello", ExternalIdBundle.of(ExternalId.of("A", "B")));
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_OO_UniqueId() {
    Pair<String, UniqueId> object = Pair.of("Hello", UniqueId.of("A", "B"));
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_OO_null() {
    Pair<String, UniqueId> object = Pair.of("Hello", null);
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_LO() {
    Pair<Long, UniqueId> object = LongObjectPair.of(23L, UniqueId.of("A", "B"));
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_LD() {
    Pair<Long, Double> object = LongDoublePair.of(23L, 4.5d);
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_IO() {
    Pair<Integer, UniqueId> object = IntObjectPair.of(23, UniqueId.of("A", "B"));
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_ID() {
    Pair<Integer, Double> object = IntDoublePair.of(23, 4.5d);
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_DD() {
    Pair<Double, Double> object = DoublesPair.of(23.2, 4.5d);
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_TypeWithSecondaryTypeAndBuilderEncoding() {
    Pair<Tenor, Tenor> object = Pair.of(Tenor.DAY, Tenor.WORKING_DAYS_IN_MONTH);
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_staticTypedMethods() {
    ObjectsPair<Tenor, Tenor> in = Pair.of(Tenor.DAY, Tenor.WORKING_DAYS_IN_MONTH);
    FudgeMsg msg = ObjectsPairFudgeBuilder.buildMessage(getFudgeSerializer(), in, Tenor.class, Tenor.class);
    ObjectsPair<Tenor, Tenor> out = ObjectsPairFudgeBuilder.buildObject(getFudgeDeserializer(), msg, Tenor.class, Tenor.class);
    assertEquals(out, in);
    msg = cycleMessage(msg);
    out = ObjectsPairFudgeBuilder.buildObject(getFudgeDeserializer(), msg, Tenor.class, Tenor.class);
    assertEquals(out, in);
  }

  public void test_TypeWithSecondaryTypeAndReducedNumber() {
    Pair<LocalDate, Long> object = Pair.of(LocalDate.of(2011, 6, 30), 6L);
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_nullFirst() {
    Pair<String, String> object = Pair.of(null, "B");
    assertEncodeDecodeCycle(Pair.class, object);
  }

  public void test_nullSecond() {
    Pair<String, String> object = Pair.of("A", null);
    assertEncodeDecodeCycle(Pair.class, object);
  }

}
