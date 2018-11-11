/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.bbg;

import static com.opengamma.bbg.BloombergConstants.BBG_COMMON_STOCK_TYPE;
import static com.opengamma.bbg.BloombergConstants.BLOOMBERG_EQUITY_OPTION_SECURITY_TYPE;
import static com.opengamma.bbg.util.BloombergSecurityUtils.AAPL_EQUITY_TICKER;
import static com.opengamma.bbg.util.BloombergSecurityUtils.APV_EQUITY_OPTION_TICKER;
import static com.opengamma.bbg.util.BloombergSecurityUtils.ATT_EQUITY_TICKER;
import static com.opengamma.bbg.util.BloombergSecurityUtils.EUR;
import static com.opengamma.bbg.util.BloombergSecurityUtils.SPX_INDEX_OPTION_TICKER;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeAPVLEquityOptionSecurity;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeAUDUSDCurrencyFuture;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeAgricultureFuture;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeEquityFuture;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeEthanolFuture;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeExchangeTradedFund;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeExpectedAAPLEquitySecurity;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeExpectedATTEquitySecurity;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeInterestRateFuture;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeSPXIndexOptionSecurity;
import static com.opengamma.bbg.util.BloombergSecurityUtils.makeSilverFuture;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.time.Instant;
import javax.time.calendar.Clock;
import javax.time.calendar.TimeZone;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.Lists;
import com.opengamma.bbg.util.BloombergSecurityUtils;
import com.opengamma.core.id.ExternalSchemes;
import com.opengamma.core.security.Security;
import com.opengamma.financial.security.FinancialSecurity;
import com.opengamma.financial.security.equity.EquitySecurity;
import com.opengamma.financial.security.future.AgricultureFutureSecurity;
import com.opengamma.financial.security.future.BondFutureDeliverable;
import com.opengamma.financial.security.future.BondFutureSecurity;
import com.opengamma.financial.security.future.EnergyFutureSecurity;
import com.opengamma.financial.security.future.EquityFutureSecurity;
import com.opengamma.financial.security.future.FXFutureSecurity;
import com.opengamma.financial.security.future.InterestRateFutureSecurity;
import com.opengamma.financial.security.future.MetalFutureSecurity;
import com.opengamma.financial.security.option.AmericanExerciseType;
import com.opengamma.financial.security.option.EquityIndexOptionSecurity;
import com.opengamma.financial.security.option.EquityOptionSecurity;
import com.opengamma.financial.security.option.EuropeanExerciseType;
import com.opengamma.id.ExternalId;
import com.opengamma.id.ExternalIdBundle;
import com.opengamma.id.ExternalScheme;
import com.opengamma.id.UniqueId;
import com.opengamma.util.time.Expiry;

/**
 * Base case for testing BloombergSecuritySource.
 */
public abstract class BloombergSecuritySourceTestCase {

  private static final EquitySecurity EXPECTED_AAPL_EQUITY_SEC = makeExpectedAAPLEquitySecurity();
  private static final EquitySecurity EXPECTED_ATT_EQUITY_SEC = makeExpectedATTEquitySecurity();
  private static final EquityOptionSecurity EXPECTED_APVL_EQUITYOPTION_SEC = makeAPVLEquityOptionSecurity();
  private static final EquityIndexOptionSecurity EXPECTED_SPX_INDEXOPTION_SEC = makeSPXIndexOptionSecurity();
  private static final FXFutureSecurity EXPECTED_AUDUSD_FUTURE_SEC = makeAUDUSDCurrencyFuture();
  private static final MetalFutureSecurity EXPECTED_SILVER_FUTURE = makeSilverFuture();
  private static final EnergyFutureSecurity EXPECTED_ETHANOL_FUTURE = makeEthanolFuture();
  private static final InterestRateFutureSecurity EXPECTED_EURODOLLAR_FUTURE = makeInterestRateFuture();
  private static final AgricultureFutureSecurity EXPECTED_WHEAT_FUTURE_SEC = makeAgricultureFuture();
  private static final EquityFutureSecurity EXPECTED_EQUITY_FUTURE_SEC = makeEquityFuture();
  private static final EquitySecurity US_NATURAL_GAS_FUND = makeExchangeTradedFund();

  private static final ExternalScheme[] EXPECTED_IDENTIFICATION_SCHEME = new ExternalScheme[] {
      ExternalSchemes.BLOOMBERG_BUID, ExternalSchemes.BLOOMBERG_TICKER, ExternalSchemes.CUSIP};

  private BloombergSecuritySource _securitySource = null;

  @BeforeMethod
  public void setupBloombergSecuritySource() throws Exception {
    _securitySource = createSecuritySource();
  }

  protected abstract BloombergSecuritySource createSecuritySource() throws Exception;

  @AfterMethod
  public void terminateSecuritySource() throws Exception {
    stopSecuritySource();

    if (_securitySource != null) {
      _securitySource = null;
    }
  }

  protected void stopSecuritySource() throws Exception {
  }

  //-------------------------------------------------------------------------
  @Test
  public void getSecurityType() throws Exception {
    String bbgEquitySecType = BBG_COMMON_STOCK_TYPE;
    String bbgEquityOptionSecType = BLOOMBERG_EQUITY_OPTION_SECURITY_TYPE;
    assertEquals(bbgEquitySecType, _securitySource.getSecurityType(AAPL_EQUITY_TICKER));
    assertEquals(bbgEquityOptionSecType, _securitySource.getSecurityType(APV_EQUITY_OPTION_TICKER));
    assertEquals(bbgEquitySecType, _securitySource.getSecurityType("T US Equity"));
    assertNull(_securitySource.getSecurityType("INVALID"));
  }

  @Test
  public void aaplEquityByBbgTicker() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_AAPL_EQUITY_SEC,
        ExternalSchemes.BLOOMBERG_TICKER);
    Security sec = _securitySource.getSingle(ExternalIdBundle.of(bloombergIdentifier));
    assertEquitySecurity(EXPECTED_AAPL_EQUITY_SEC, sec);
  }

  private ExternalId getBloombergIdentifier(FinancialSecurity finSec, ExternalScheme scheme) {
    ExternalIdBundle identifierBundle = finSec.getExternalIdBundle();
    return identifierBundle.getExternalId(scheme);
  }

  @Test
  public void aaplEquityByBbgUnique() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_AAPL_EQUITY_SEC,
        ExternalSchemes.BLOOMBERG_BUID);
    Security sec = _securitySource.getSingle(ExternalIdBundle.of(bloombergIdentifier));
    assertEquitySecurity(EXPECTED_AAPL_EQUITY_SEC, sec);
  }

  @Test
  public void aaplEquitiesByBbgTicker() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_AAPL_EQUITY_SEC,
        ExternalSchemes.BLOOMBERG_TICKER);
    Collection<Security> securities = _securitySource.get(ExternalIdBundle.of(bloombergIdentifier));
    assertNotNull(securities);
    assertEquals(1, securities.size());
    Security sec = securities.iterator().next();
    assertEquitySecurity(EXPECTED_AAPL_EQUITY_SEC, sec);
  }

  @Test
  public void aaplEquitiesByBbgUnique() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_AAPL_EQUITY_SEC,
        ExternalSchemes.BLOOMBERG_BUID);
    Collection<Security> securities = _securitySource.get(ExternalIdBundle.of(bloombergIdentifier));
    assertNotNull(securities);
    assertEquals(1, securities.size());
    Security sec = securities.iterator().next();
    assertEquitySecurity(EXPECTED_AAPL_EQUITY_SEC, sec);
  }

  @Test
  public void attEquityByBbgTicker() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_ATT_EQUITY_SEC,
        ExternalSchemes.BLOOMBERG_TICKER);
    Security sec = _securitySource.getSingle(ExternalIdBundle.of(bloombergIdentifier));
    assertEquitySecurity(EXPECTED_ATT_EQUITY_SEC, sec);
  }

  @Test
  public void exchangeTradedFund() throws Exception {
    Security security = _securitySource.getSingle(US_NATURAL_GAS_FUND.getExternalIdBundle());
    assertEquitySecurity(US_NATURAL_GAS_FUND, security);
  }

  @Test
  public void attEquitiesByBbgTicker() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_ATT_EQUITY_SEC,
        ExternalSchemes.BLOOMBERG_TICKER);
    Collection<Security> securities = _securitySource.get(ExternalIdBundle.of(bloombergIdentifier));
    assertNotNull(securities);
    assertEquals(1, securities.size());
    Security sec = securities.iterator().next();
    assertEquitySecurity(EXPECTED_ATT_EQUITY_SEC, sec);
  }

  @Test
  public void attEquityByBbgUnique() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_ATT_EQUITY_SEC,
        ExternalSchemes.BLOOMBERG_BUID);
    Security sec = _securitySource.getSingle(ExternalIdBundle.of(bloombergIdentifier));
    assertEquitySecurity(EXPECTED_ATT_EQUITY_SEC, sec);
  }

  @Test
  public void attEquitiesByBbgUnique() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_ATT_EQUITY_SEC,
        ExternalSchemes.BLOOMBERG_BUID);
    Collection<Security> securities = _securitySource.get(ExternalIdBundle.of(bloombergIdentifier));
    assertNotNull(securities);
    assertEquals(1, securities.size());
    Security sec = securities.iterator().next();
    assertEquitySecurity(EXPECTED_ATT_EQUITY_SEC, sec);
  }

  @Test
  public void apvEquityOptionByBbgTicker() throws Exception {
    Security sec = _securitySource.getSingle(EXPECTED_APVL_EQUITYOPTION_SEC.getExternalIdBundle());
    assertAmericanVanillaEquityOptionSecurity(EXPECTED_APVL_EQUITYOPTION_SEC, sec);
  }

  @Test
  public void apvEquityOptionsByBbgTicker() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_APVL_EQUITYOPTION_SEC,
        ExternalSchemes.BLOOMBERG_TICKER);
    Collection<Security> securities = _securitySource.get(ExternalIdBundle.of(bloombergIdentifier));
    assertNotNull(securities);
    assertEquals(1, securities.size());
    Security sec = securities.iterator().next();
    assertAmericanVanillaEquityOptionSecurity(EXPECTED_APVL_EQUITYOPTION_SEC, sec);
  }

  @Test
  public void apvEquityOptionsByBbgUnique() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_APVL_EQUITYOPTION_SEC,
        ExternalSchemes.BLOOMBERG_BUID);
    Collection<Security> securities = _securitySource.get(ExternalIdBundle.of(bloombergIdentifier));
    assertNotNull(securities);
    assertEquals(1, securities.size());
    Security sec = securities.iterator().next();
    assertAmericanVanillaEquityOptionSecurity(EXPECTED_APVL_EQUITYOPTION_SEC, sec);
  }

  @Test
  public void apvEquityOptionByBbgUnique() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_APVL_EQUITYOPTION_SEC,
        ExternalSchemes.BLOOMBERG_BUID);
    Security sec = _securitySource.getSingle(ExternalIdBundle.of(bloombergIdentifier));
    assertAmericanVanillaEquityOptionSecurity(EXPECTED_APVL_EQUITYOPTION_SEC, sec);
  }

  @Test
  public void spxIndexOptionByBbgTicker() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_SPX_INDEXOPTION_SEC,
        ExternalSchemes.BLOOMBERG_TICKER);
    Security sec = _securitySource.getSingle(ExternalIdBundle.of(bloombergIdentifier));
    assertEuropeanVanillaEquityIndexOptionSecurity(EXPECTED_SPX_INDEXOPTION_SEC, sec);
  }

  @Test
  public void spxIndexOptionsByBbgTicker() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_SPX_INDEXOPTION_SEC,
        ExternalSchemes.BLOOMBERG_TICKER);
    Collection<Security> securities = _securitySource.get(ExternalIdBundle.of(bloombergIdentifier));
    assertNotNull(securities);
    assertEquals(1, securities.size());
    Security sec = securities.iterator().next();
    assertEuropeanVanillaEquityIndexOptionSecurity(EXPECTED_SPX_INDEXOPTION_SEC, sec);
  }

  @Test
  public void spxIndexOptionByBbgUnique() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_SPX_INDEXOPTION_SEC,
        ExternalSchemes.BLOOMBERG_BUID);
    Security sec = _securitySource.getSingle(ExternalIdBundle.of(bloombergIdentifier));
    assertEuropeanVanillaEquityIndexOptionSecurity(EXPECTED_SPX_INDEXOPTION_SEC, sec);
  }

  @Test
  public void spxIndexOptionsByBbgUnique() throws Exception {
    ExternalId bloombergIdentifier = getBloombergIdentifier(EXPECTED_SPX_INDEXOPTION_SEC,
        ExternalSchemes.BLOOMBERG_BUID);
    Collection<Security> securities = _securitySource.get(ExternalIdBundle.of(bloombergIdentifier));
    assertNotNull(securities);
    assertEquals(1, securities.size());
    Security sec = securities.iterator().next();
    assertEuropeanVanillaEquityIndexOptionSecurity(EXPECTED_SPX_INDEXOPTION_SEC, sec);
  }

  @Test(groups={"bbgSecurityFutureTests"})
  public void agricultureFuture() throws Exception {
    Security wheat = _securitySource.getSingle(EXPECTED_WHEAT_FUTURE_SEC.getExternalIdBundle());
    assertNotNull(wheat);
    assertTrue(wheat instanceof AgricultureFutureSecurity);
    assertEquals(EXPECTED_WHEAT_FUTURE_SEC, wheat);
  }

  @Test(groups={"bbgSecurityFutureTests"})
  public void equityFuture() throws Exception {
    Security spIndex = _securitySource.getSingle(EXPECTED_EQUITY_FUTURE_SEC.getExternalIdBundle());
    assertNotNull(spIndex);
    assertTrue(spIndex instanceof EquityFutureSecurity);
    assertEquals(EXPECTED_EQUITY_FUTURE_SEC, spIndex);
  }

  @Test(enabled = false)
  public void currencyFuture() throws Exception {
    ExternalIdBundle id = ExternalIdBundle.of(ExternalSchemes.bloombergTickerSecurityId("LNM0 Curncy"));
    Security audUsd = _securitySource.getSingle(id);
    assertNotNull(audUsd);
    assertTrue(audUsd instanceof FXFutureSecurity);
    assertEquals(EXPECTED_AUDUSD_FUTURE_SEC, audUsd);
  }

  @Test(groups={"bbgSecurityFutureTests"})
  public void euroBondFuture() throws Exception {
    ExternalIdBundle euroBund = ExternalIdBundle.of(ExternalSchemes.bloombergTickerSecurityId("RXA Comdty"));
    Security bond = _securitySource.getSingle(euroBund);
    assertNotNull(bond);
    assertTrue(bond instanceof BondFutureSecurity);
    BondFutureSecurity euroBondFuture = (BondFutureSecurity) bond;
    assertEquals("FUTURE", euroBondFuture.getSecurityType());
    assertEquals("Bond", euroBondFuture.getContractCategory());
    assertEquals(EUR, euroBondFuture.getCurrency());
    String displayName = euroBondFuture.getName();
    assertNotNull(displayName);
    assertTrue(displayName.contains("EURO-BUND FUTURE"));
    Expiry expiry = euroBondFuture.getExpiry();
    assertNotNull(expiry);
    assertTrue(expiry.toInstant().isAfter(getTodayInstant()));
    assertEquals("XEUR", euroBondFuture.getTradingExchange());
    assertEquals("XEUR", euroBondFuture.getSettlementExchange());
    //assert identifiers are set
    Collection<ExternalId> identifiers = euroBondFuture.getExternalIdBundle().getExternalIds();
    assertNotNull(identifiers);
    assertTrue(identifiers.size() >= EXPECTED_IDENTIFICATION_SCHEME.length);
    ExternalIdBundle identifierBundle = ExternalIdBundle.of(identifiers);
    for (ExternalScheme expectedIDScheme : EXPECTED_IDENTIFICATION_SCHEME) {
      assertNotNull(identifierBundle.getExternalId(expectedIDScheme));
    }
    //assert deliverables are not empty
    Collection<BondFutureDeliverable> basket = euroBondFuture.getBasket();
    assertNotNull(basket);
    for (BondFutureDeliverable bondFutureDeliverable : basket) {
      ExternalIdBundle bundle = bondFutureDeliverable.getIdentifiers();
      assertNotNull(bundle);
      assertNotNull(bundle.getExternalId(ExternalSchemes.BLOOMBERG_BUID));
      assertTrue(bondFutureDeliverable.getConversionFactor() > 0);
    }
  }

  private Instant getTodayInstant() {
    Instant toDay = Clock.system(TimeZone.UTC).instant();
    return toDay;
  }

  @Test(groups={"bbgSecurityFutureTests"})
  public void metalFuture() throws Exception {
    Security silverFuture = _securitySource.getSingle(EXPECTED_SILVER_FUTURE.getExternalIdBundle());
    assertNotNull(silverFuture);
    assertTrue(silverFuture instanceof MetalFutureSecurity);
    assertEquals(EXPECTED_SILVER_FUTURE, silverFuture);
  }

  @Test(groups={"bbgSecurityFutureTests"})
  public void energyFuture() throws Exception {
    Security ethanolFuture = _securitySource.getSingle(EXPECTED_ETHANOL_FUTURE.getExternalIdBundle());
    assertNotNull(ethanolFuture);
    assertTrue(ethanolFuture instanceof EnergyFutureSecurity);
    assertEquals(EXPECTED_ETHANOL_FUTURE, ethanolFuture);
  }

  @Test(groups={"bbgSecurityFutureTests"})
  public void interestRateFuture() throws Exception {
    Security euroDollar = _securitySource.getSingle(EXPECTED_EURODOLLAR_FUTURE.getExternalIdBundle());
    assertNotNull(euroDollar);
    assertTrue(euroDollar instanceof InterestRateFutureSecurity);
    assertEquals(EXPECTED_EURODOLLAR_FUTURE, euroDollar);
  }

  @Test
  public void invalidSecurity() throws Exception {
    ExternalIdBundle invalidKey = ExternalIdBundle.of(ExternalSchemes.bloombergTickerSecurityId("INVALID"));
    Security sec = _securitySource.getSingle(invalidKey);
    assertNull(sec);
  }

  @Test
  public void invalidSecurities() throws Exception {
    ExternalIdBundle invalidKey = ExternalIdBundle.of(ExternalSchemes.bloombergTickerSecurityId("INVALID"));
    Collection<Security> securities = _securitySource.get(invalidKey);
    assertNotNull(securities);
    assertTrue(securities.isEmpty());
  }

  @Test
  public void multiThreadedSecurityRequest() throws Exception {

    ExternalIdBundle apvKey = ExternalIdBundle.of(ExternalSchemes.bloombergTickerSecurityId(APV_EQUITY_OPTION_TICKER));
    ExternalIdBundle spxKey = ExternalIdBundle.of(ExternalSchemes.bloombergTickerSecurityId(SPX_INDEX_OPTION_TICKER));
    ExternalIdBundle aaplKey = ExternalIdBundle.of(ExternalSchemes.bloombergTickerSecurityId(AAPL_EQUITY_TICKER));
    ExternalIdBundle attKey = ExternalIdBundle.of(ExternalSchemes.bloombergTickerSecurityId(ATT_EQUITY_TICKER));

    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Future<Security>> apvresults = new ArrayList<Future<Security>>();
    List<Future<Security>> spxresults = new ArrayList<Future<Security>>();
    List<Future<Security>> aaplresults = new ArrayList<Future<Security>>();
    List<Future<Security>> attresults = new ArrayList<Future<Security>>();

    for (int i = 0; i < 10; i++) {
      apvresults.add(pool.submit(new BSMGetSecurityCallable(apvKey)));
      spxresults.add(pool.submit(new BSMGetSecurityCallable(spxKey)));
      aaplresults.add(pool.submit(new BSMGetSecurityCallable(aaplKey)));
      attresults.add(pool.submit(new BSMGetSecurityCallable(attKey)));
    }

    for (Future<Security> future : apvresults) {
      // Check that each one didn't throw an exception and returns the expected
      // APV security
      Security sec = future.get();
      assertAmericanVanillaEquityOptionSecurity(EXPECTED_APVL_EQUITYOPTION_SEC, sec);
    }

    for (Future<Security> future : spxresults) {
      // Check that each one didn't throw an exception and returns the expected
      // SPX security
      Security sec = future.get();
      assertEuropeanVanillaEquityIndexOptionSecurity(EXPECTED_SPX_INDEXOPTION_SEC, sec);
    }

    for (Future<Security> future : aaplresults) {
      // Check that each one didn't throw an exception and returns the expected
      // AAPL security
      Security sec = future.get();
      assertEquitySecurity(EXPECTED_AAPL_EQUITY_SEC, sec);
    }

    for (Future<Security> future : attresults) {
      // Check that each one didn't throw an exception and returns the expected
      // AT&T security
      Security sec = future.get();
      assertEquitySecurity(EXPECTED_ATT_EQUITY_SEC, sec);
    }
  }

  public static void assertEquitySecurity(EquitySecurity expectedEquity, Security sec) {
    // check specific bits we want to spot failures on quickly
    assertNotNull(sec);
    assertTrue(sec instanceof EquitySecurity);
    EquitySecurity actualEquity = (EquitySecurity) sec;
    assertEquals(expectedEquity.getSecurityType(), actualEquity.getSecurityType());
    
    ExternalId expectedBUID = expectedEquity.getExternalIdBundle().getExternalId(ExternalSchemes.BLOOMBERG_BUID);
    ExternalId actualBUID = actualEquity.getExternalIdBundle().getExternalId(ExternalSchemes.BLOOMBERG_BUID);
    assertEquals(expectedBUID, actualBUID);
    
    ExternalId expectedTicker = expectedEquity.getExternalIdBundle().getExternalId(ExternalSchemes.BLOOMBERG_TICKER);
    ExternalId actualTicker = actualEquity.getExternalIdBundle().getExternalId(ExternalSchemes.BLOOMBERG_TICKER);
    assertEquals(expectedTicker, actualTicker);
    
    assertEquals(expectedEquity.getUniqueId(), actualEquity.getUniqueId());
    assertEquals(expectedEquity.getShortName(), actualEquity.getShortName());
    assertEquals(expectedEquity.getExchange(), actualEquity.getExchange());
    assertEquals(expectedEquity.getCompanyName(), actualEquity.getCompanyName());
    assertEquals(expectedEquity.getCurrency(), actualEquity.getCurrency());
    
    // check the lot without Identifiers
    ExternalIdBundle expectedIdentifiers = expectedEquity.getExternalIdBundle();
    ExternalIdBundle actualIdentifiers = actualEquity.getExternalIdBundle();
    
    expectedEquity.setExternalIdBundle(ExternalIdBundle.EMPTY);
    actualEquity.setExternalIdBundle(ExternalIdBundle.EMPTY);
    assertEquals(expectedEquity, actualEquity);
    
    expectedEquity.setExternalIdBundle(expectedIdentifiers);
    actualEquity.setExternalIdBundle(actualIdentifiers);
  }

  public static void assertAmericanVanillaEquityOptionSecurity(EquityOptionSecurity expectedOption, Security sec) {
    // check specific bits we want to spot failures on quickly
    assertNotNull(sec);
    assertTrue(sec instanceof EquityOptionSecurity);
    final EquityOptionSecurity equitySec = (EquityOptionSecurity) sec;
    assertTrue(equitySec.getExerciseType() instanceof AmericanExerciseType);
    assertEquityOptionSecurity(expectedOption, sec);
  }

  public static void assertAmericanVanillaEquityIndexOptionSecurity(EquityIndexOptionSecurity expectedOption, Security sec) {
    // check specific bits we want to spot failures on quickly
    assertNotNull(sec);
    assertTrue(sec instanceof EquityIndexOptionSecurity);
    final EquityIndexOptionSecurity equityIndexOption = (EquityIndexOptionSecurity) sec;
    assertTrue(equityIndexOption.getExerciseType() instanceof AmericanExerciseType);
    assertEquityIndexOptionSecurity(expectedOption, sec);
  }

  public static void assertEquityOptionSecurity(EquityOptionSecurity expectedOption, Security sec) {
    assertNotNull(expectedOption);
    assertNotNull(sec);
    EquityOptionSecurity actualOption = (EquityOptionSecurity) sec;
    assertEquals(expectedOption.getExternalIdBundle(), actualOption.getExternalIdBundle());
    assertEquals(expectedOption.getUniqueId(), actualOption.getUniqueId());
    assertEquals(expectedOption.getSecurityType(), actualOption.getSecurityType());
    assertEquals(expectedOption.getCurrency(), actualOption.getCurrency());
    assertEquals(expectedOption.getOptionType(), actualOption.getOptionType());
    assertTrue(expectedOption.getStrike() == actualOption.getStrike());
    assertEquals(expectedOption.getExpiry(), actualOption.getExpiry());
    assertEquals(expectedOption.getUnderlyingId(), actualOption.getUnderlyingId());
    assertEquals(expectedOption.getName(), actualOption.getName());
    // check the lot
    assertEquals(expectedOption, sec);
  }

  public static void assertEquityIndexOptionSecurity(EquityIndexOptionSecurity expectedOption, Security sec) {
    assertNotNull(expectedOption);
    assertNotNull(sec);
    EquityIndexOptionSecurity actualOption = (EquityIndexOptionSecurity) sec;
    assertEquals(expectedOption.getExternalIdBundle(), actualOption.getExternalIdBundle());
    assertEquals(expectedOption.getUniqueId(), actualOption.getUniqueId());
    assertEquals(expectedOption.getSecurityType(), actualOption.getSecurityType());
    assertEquals(expectedOption.getCurrency(), actualOption.getCurrency());
    assertEquals(expectedOption.getOptionType(), actualOption.getOptionType());
    assertTrue(expectedOption.getStrike() == actualOption.getStrike());
    assertEquals(expectedOption.getExpiry(), actualOption.getExpiry());
    assertEquals(expectedOption.getUnderlyingId(), actualOption.getUnderlyingId());
    assertEquals(expectedOption.getName(), actualOption.getName());
    // check the lot
    assertEquals(expectedOption, sec);
  }

  public static void assertEuropeanVanillaEquityOptionSecurity(EquityOptionSecurity expectedOption, Security sec) {
    // check specific bits we want to spot failures on quickly
    assertNotNull(sec);
    assertTrue(sec instanceof EquityOptionSecurity);
    final EquityOptionSecurity equitySec = (EquityOptionSecurity) sec;
    assertTrue(equitySec.getExerciseType() instanceof EuropeanExerciseType);
    assertEquityOptionSecurity(expectedOption, sec);
  }

  public static void assertEuropeanVanillaEquityIndexOptionSecurity(EquityIndexOptionSecurity expectedOption, Security sec) {
    // check specific bits we want to spot failures on quickly
    assertNotNull(sec);
    assertTrue(sec instanceof EquityIndexOptionSecurity);
    final EquityIndexOptionSecurity equityIndexOption = (EquityIndexOptionSecurity) sec;
    assertTrue(equityIndexOption.getExerciseType() instanceof EuropeanExerciseType);
    assertEquityIndexOptionSecurity(expectedOption, sec);
  }

  private class BSMGetSecurityCallable implements Callable<Security> {
    ExternalIdBundle _secKey;

    public BSMGetSecurityCallable(ExternalIdBundle secKey) {
      _secKey = secKey;
    }

    public Security call() throws Exception {
      return _securitySource.getSingle(_secKey);
    }
  }

  @Test
  public void test_getBulkSecurity() throws Exception {
    UniqueId aaplId = UniqueId.of(BloombergSecuritySource.BLOOMBERG_SCHEME, BloombergSecurityUtils.AAPL_BUID);
    UniqueId attId = UniqueId.of(BloombergSecuritySource.BLOOMBERG_SCHEME, BloombergSecurityUtils.ATT_BUID);
    
    Map<UniqueId, Security> securities = _securitySource.get(Lists.newArrayList(aaplId, attId));
    assertNotNull(securities);
    assertEquals(2, securities.size());
    assertTrue(securities.keySet().contains(aaplId));
    assertTrue(securities.keySet().contains(attId));
    assertEquitySecurity(EXPECTED_AAPL_EQUITY_SEC, securities.get(aaplId));
    assertEquitySecurity(EXPECTED_ATT_EQUITY_SEC, securities.get(attId));
    
  }

}
