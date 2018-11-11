/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.util.test;

import static org.testng.AssertJUnit.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.opengamma.util.tuple.Triple;


/**
 * Tests the creation + upgrade sequence results in the same structure as a pure create.
 */
public abstract class AbstractDbUpgradeTest extends DbTest {

  private static final Map<String, Map<String, String>> s_targetSchema = Maps.newHashMap();

  private final List<Triple<String, String, String>> _comparisons = Lists.newLinkedList();

  protected Map<String, String> getVersionSchemas() {
    Map<String, String> versionSchema = s_targetSchema.get(getDatabaseType());
    if (versionSchema == null) {
      versionSchema = new HashMap<>();
      s_targetSchema.put(getDatabaseType(), versionSchema);
    }
    return versionSchema;
  }

  @BeforeMethod
  public void setUp() throws Exception {
    DbTool dbTool = getDbTool();
    dbTool.setTargetVersion(getTargetVersion());
    dbTool.setCreateVersion(getCreateVersion());
    dbTool.dropTestSchema();
    dbTool.createTestSchema();
    dbTool.createTables(_masterDB, dbTool.getTestCatalog(), dbTool.getTestSchema(), Integer.parseInt(getTargetVersion()), Integer.parseInt(getCreateVersion()), this);
    dbTool.clearTestTables();
  }
  
  private final String _masterDB;

  protected AbstractDbUpgradeTest(String databaseType, String masterDB, final String targetVersion, final String crateVersion) {
    super(databaseType, targetVersion, crateVersion);
    _masterDB = masterDB;
  }

  @Test
  public void testDatabaseUpgrade() {
    for (Triple<String, String, String> comparison : _comparisons) {
      /*
       * System.out.println(comparison.getFirst() + " expected:");
       * System.out.println(comparison.getSecond());
       * System.out.println(comparison.getFirst() + " found:");
       * System.out.println(comparison.getThird());
       */
      int diff = StringUtils.indexOfDifference(comparison.getSecond(), comparison.getThird());
      if (diff >= 0) {
        System.err.println("Difference at " + diff);
        System.err.println("Upgraded --->..." + StringUtils.substring(comparison.getSecond(), diff - 200, diff) +
          "<-!!!->" + StringUtils.substring(comparison.getSecond(), diff, diff + 200) + "...");
        System.err.println(" Created --->..." + StringUtils.substring(comparison.getThird(), diff - 200, diff) +
          "<-!!!->" + StringUtils.substring(comparison.getThird(), diff, diff + 200) + "...");
      }
      assertEquals(getDatabaseType() + ": " + comparison.getFirst(), comparison.getSecond(), comparison.getThird());
    }
  }

  @Override
  public void tablesCreatedOrUpgraded(final String version, final String prefix) {
    final Map<String, String> versionSchemas = getVersionSchemas();
    if (versionSchemas.containsKey(prefix + "_" + version)) {
      // if we've already done the full schema, then we want to test that this upgrade has given us the same (but defer the comparison)
      _comparisons.add(new Triple<String, String, String>(prefix + "_" + version, versionSchemas.get(prefix + "_" + version), getDbTool().describeDatabase(prefix)));
    } else {
      // tests are run with most recent full schema first, so we can store that as a reference
      versionSchemas.put(prefix + "_" + version, getDbTool().describeDatabase(prefix));
    }
  }

  @Override
  public String toString() {
    return getDatabaseType() + "/" + _masterDB + ":" + getCreateVersion() + " >>> " + getTargetVersion();
  }

}
