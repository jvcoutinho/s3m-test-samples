/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.util.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.ZipUtils;
import com.opengamma.util.db.DbConnector;
import com.opengamma.util.db.DbConnectorFactoryBean;
import com.opengamma.util.db.DbDialect;
import com.opengamma.util.db.HSQLDbDialect;
import com.opengamma.util.db.PostgresDbDialect;
import com.opengamma.util.db.SqlServer2008DbDialect;
import com.opengamma.util.test.DbTool.TableCreationCallback;
import com.opengamma.util.time.DateUtils;

/**
 * Base DB test.
 */
public abstract class DbTest implements TableCreationCallback {

  /** Logger. */
  private static final Logger s_logger = LoggerFactory.getLogger(DbTest.class);

  protected static Map<String, String> s_databaseTypeVersion = new HashMap<>();
  private static final Map<String, DbDialect> s_dbDialects = new HashMap<>();
  private static final File SCRIPT_INSTALL_DIR = new File(DbTool.getWorkingDirectory(), "temp/" + DbTest.class.getSimpleName());

  static {
    DateUtils.initTimeZone();
    addDbDialect("hsqldb", new HSQLDbDialect());
    addDbDialect("postgres", new PostgresDbDialect());
    addDbDialect("sqlserver2008", new SqlServer2008DbDialect());
  }

  static {
    try {
      extractSQLScript();
    } catch (IOException ex) {
      s_logger.warn("problem with unzip publish sql script to {} rethrowing", SCRIPT_INSTALL_DIR, ex);
      throw new OpenGammaRuntimeException("problem with unzip publish sql script to " + SCRIPT_INSTALL_DIR, ex);
    }
  }

  private final String _databaseType;
  private final String _targetVersion;
  private final String _createVersion;
  private final DbTool _dbtool;

  protected DbTest(String databaseType, String targetVersion, String createVersion) {
    ArgumentChecker.notNull(databaseType, "databaseType");
    _databaseType = databaseType;
    _dbtool = TestProperties.getDbTool(databaseType);
    _dbtool.setJdbcUrl(getDbTool().getTestDatabaseUrl());
    _targetVersion = targetVersion;
    _createVersion = createVersion;
    if (isScriptPublished()) {
      _dbtool.addDbScriptDirectory(SCRIPT_INSTALL_DIR.getAbsolutePath());
    } else {
      _dbtool.addDbScriptDirectory(DbTool.getWorkingDirectory());
    }
  }

  /**
   * Initialise the database to the required version. This tracks the last initialised version
   * in a static map to avoid duplicate DB operations on bigger test classes. This might not be
   * such a good idea.
   */
  @BeforeMethod
  public void setUp() throws Exception {
    String prevVersion = s_databaseTypeVersion.get(getDatabaseType());
    if ((prevVersion == null) || !prevVersion.equals(getTargetVersion())) {
      s_databaseTypeVersion.put(getDatabaseType(), getTargetVersion());
      _dbtool.setTargetVersion(getTargetVersion());
      _dbtool.setCreateVersion(getCreateVersion());
      _dbtool.dropTestSchema();
      _dbtool.createTestSchema();
      _dbtool.createTestTables(this);
    }
    _dbtool.clearTestTables();
  }

  private static void extractSQLScript() throws IOException {
    if (isScriptPublished()) {
      cleanUp();
      unzipSQLScripts();
    }
  }

  private static String getZipPath() {
    return TestProperties.getTestProperties().getProperty("test.sqlzip.path");
  }

  private static void unzipSQLScripts() throws IOException {
    File zipScriptPath = new File(DbTool.getWorkingDirectory(), getZipPath());
    for (File file : (Collection<File>) FileUtils.listFiles(zipScriptPath, new String[]{"zip"}, false)) {
      ZipUtils.unzipArchive(file, SCRIPT_INSTALL_DIR);
    }
  }

  @AfterMethod
  public void tearDown() throws Exception {
    _dbtool.resetTestCatalog(); // avoids locking issues with Derby
  }

  @AfterClass
  public void tearDownClass() throws Exception {
    _dbtool.close();
  }

  @AfterSuite
  public static void cleanUp() {
    FileUtils.deleteQuietly(SCRIPT_INSTALL_DIR);
  }

  protected static Object[][] getParametersForSeparateMasters(int prevVersionCount) {
    String databaseType = System.getProperty("test.database.type");
    if (databaseType == null) {
      databaseType = "all";
    }
    Collection<String> databaseTypes = TestProperties.getDatabaseTypes(databaseType);
    ArrayList<Object[]> parameters = new ArrayList<Object[]>();
    for (String dbType : databaseTypes) {
      DbTool dbtool = TestProperties.getDbTool(dbType);
      dbtool.setJdbcUrl(dbtool.getTestDatabaseUrl());
      if (isScriptPublished()) {
        dbtool.addDbScriptDirectory(SCRIPT_INSTALL_DIR.getAbsolutePath());
      } else {
        dbtool.addDbScriptDirectory(DbTool.getWorkingDirectory());
      }
      for (String masterDB : dbtool.getScriptDirs().keySet()) {
        Set<Integer> versions = dbtool.getScriptDirs().get(masterDB).keySet();
        int max = Collections.max(versions);
        int min = Collections.min(versions);
        for (int v = max; v >= Math.max(max - prevVersionCount, min); v--) {
          parameters.add(new Object[]{dbType, masterDB, "" + max /*target_version*/, "" + v /*migrate_from_version*/});
        }
      }
    }
    Object[][] array = new Object[parameters.size()][];
    parameters.toArray(array);
    return array;
  }

  protected static Object[][] getParameters() {
    String databaseType = System.getProperty("test.database.type");
    if (databaseType == null) {
      databaseType = "hsqldb";
    }
    Collection<String> databaseTypes = TestProperties.getDatabaseTypes(databaseType);
    ArrayList<Object[]> parameters = new ArrayList<Object[]>();
    for (String dbType : databaseTypes) {
      parameters.add(new Object[]{dbType, "latest"});
    }
    Object[][] array = new Object[parameters.size()][];
    parameters.toArray(array);
    return array;
  }

  public static Object[][] getParametersForDatabase(final String databaseType) {
    ArrayList<Object[]> parameters = new ArrayList<Object[]>();
    for (String db : TestProperties.getDatabaseTypes(databaseType)) {
      parameters.add(new Object[]{db, "latest"});
    }
    Object[][] array = new Object[parameters.size()][];
    parameters.toArray(array);
    return array;
  }

  private static boolean isScriptPublished() {
    String zipPath = getZipPath();
    if (zipPath == null) {
      throw new OpenGammaRuntimeException("missing test.sqlZip.path property in test properties file");
    }
    File zipScriptPath = new File(DbTool.getWorkingDirectory(), zipPath);
    boolean result = false;
    if (zipScriptPath.exists()) {
      @SuppressWarnings("rawtypes")
      Collection zipfiles = FileUtils.listFiles(zipScriptPath, new String[]{"zip"}, false);
      result = !zipfiles.isEmpty();
    }
    return result;
  }

  @DataProvider(name = "localDatabase")
  public static Object[][] data_localDatabase() {
    return getParametersForDatabase("hsqldb");
  }

  @DataProvider(name = "databases")
  public static Object[][] data_databases() {
    try{
    return getParameters();
    }catch(Exception ex){
      System.out.println(ex.getMessage());
      return null;
    }
  }

  @DataProvider(name = "databasesVersionsForSeparateMasters")
  public static Object[][] data_databasesVersionsForSeparateMasters() {
    return getParametersForSeparateMasters(3);
  }

  protected static int getPreviousVersionCount() {
    String previousVersionCountString = System.getProperty("test.database.previousVersions");
    int previousVersionCount;
    if (previousVersionCountString == null) {
      previousVersionCount = 0; // If you run from Eclipse, use current version only
    } else {
      previousVersionCount = Integer.parseInt(previousVersionCountString);
    }
    return previousVersionCount;
  }

  public DbTool getDbTool() {
    return _dbtool;
  }

  public String getDatabaseType() {
    return _databaseType;
  }

  public String getCreateVersion() {
    return _createVersion;
  }

  public String getTargetVersion() {
    return _targetVersion;
  }

  public DataSourceTransactionManager getTransactionManager() {
    return new DataSourceTransactionManager(getDbTool().getDataSource());
  }

  public DbConnector getDbConnector() {
    DbDialect dbDialect = s_dbDialects.get(getDatabaseType());
    if (dbDialect == null) {
      throw new OpenGammaRuntimeException("config error - no DBHelper setup for " + getDatabaseType());
    }
    DbConnectorFactoryBean factory = new DbConnectorFactoryBean();
    factory.setName("DbTest");
    factory.setDialect(dbDialect);
    factory.setDataSource(getDbTool().getDataSource());
    factory.setTransactionIsolationLevelName("ISOLATION_READ_COMMITTED");
    factory.setTransactionPropagationBehaviorName("PROPAGATION_REQUIRED");
    return factory.createObject();
  }

  /**
   * Adds a dialect to the map of known.
   *
   * @param dbType  the database type, not null
   * @param dialect  the dialect, not null
   */
  public static void addDbDialect(String dbType, DbDialect dialect) {
    s_dbDialects.put(dbType, dialect);
  }

  /**
   * Override this if you wish to do something with the database while it is in its "upgrading" state - e.g. populate with test data
   * at a particular version to test the data transformations on the next version upgrades.
   */
  public void tablesCreatedOrUpgraded(final String version, final String prefix) {
    // No action 
  }

  //-------------------------------------------------------------------------
  @Override
  public String toString() {
    return getDatabaseType() + ":" + getTargetVersion();
  }

}
