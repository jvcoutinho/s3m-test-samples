/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.masterdb.user;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import javax.time.Instant;
import javax.time.calendar.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import com.google.common.collect.Sets;
import com.opengamma.id.ExternalIdBundle;
import com.opengamma.id.UniqueId;
import com.opengamma.master.user.ManageableOGUser;
import com.opengamma.master.user.UserDocument;
import com.opengamma.util.test.DbTest;

/**
 * Tests ModifyUserDbUserMasterWorker.
 */
public class ModifyUserDbUserMasterWorkerAddTest extends AbstractDbUserMasterWorkerTest {
  // superclass sets up dummy database

  private static final Logger s_logger = LoggerFactory.getLogger(ModifyUserDbUserMasterWorkerAddTest.class);
  private static final ExternalIdBundle BUNDLE = ExternalIdBundle.of("A", "B");

  @Factory(dataProvider = "databases", dataProviderClass = DbTest.class)
  public ModifyUserDbUserMasterWorkerAddTest(String databaseType, String databaseVersion) {
    super(databaseType, databaseVersion, false);
    s_logger.info("running testcases for {}", databaseType);
  }

  //-------------------------------------------------------------------------
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void test_addUser_nullDocument() {
    _usrMaster.add(null);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void test_add_noUser() {
    UserDocument doc = new UserDocument();
    _usrMaster.add(doc);
  }

  @Test
  public void test_add() {
    Instant now = Instant.now(_usrMaster.getTimeSource());
    
    ManageableOGUser user = new ManageableOGUser("AddedUser");
    user.setPasswordHash("TESTHASH");
    user.setName("Test Name");
    user.setEmailAddress("test@test.com");
    user.setExternalIdBundle(BUNDLE);
    user.setEntitlements(Sets.newHashSet("A", "B"));
    TimeZone zone = user.getTimeZone();
    UserDocument doc = new UserDocument(user);
    UserDocument test = _usrMaster.add(doc);
    
    UniqueId uniqueId = test.getUniqueId();
    assertNotNull(uniqueId);
    assertEquals("DbUsr", uniqueId.getScheme());
    assertTrue(uniqueId.isVersioned());
    assertTrue(Long.parseLong(uniqueId.getValue()) >= 1000);
    assertEquals("0", uniqueId.getVersion());
    assertEquals(now, test.getVersionFromInstant());
    assertEquals(null, test.getVersionToInstant());
    assertEquals(now, test.getCorrectionFromInstant());
    assertEquals(null, test.getCorrectionToInstant());
    ManageableOGUser testUser = test.getUser();
    assertNotNull(testUser);
    assertEquals(uniqueId, testUser.getUniqueId());
    assertEquals("TESTHASH", testUser.getPasswordHash());
    assertEquals("Test Name", testUser.getName());
    assertEquals(zone, testUser.getTimeZone());
    assertEquals("test@test.com", testUser.getEmailAddress());
    assertEquals(BUNDLE, testUser.getExternalIdBundle());
    assertEquals(Sets.newHashSet("A", "B"), testUser.getEntitlements());
  }

  @Test
  public void test_add_addThenGet() {
    ManageableOGUser user = new ManageableOGUser("Test");
    user.setName("Test Name");
    user.setEmailAddress("test@test.com");
    user.setEntitlements(Sets.newHashSet("A", "B"));
    UserDocument doc = new UserDocument(user);
    UserDocument added = _usrMaster.add(doc);
    
    UserDocument test = _usrMaster.get(added.getUniqueId());
    assertEquals(added, test);
  }

}
