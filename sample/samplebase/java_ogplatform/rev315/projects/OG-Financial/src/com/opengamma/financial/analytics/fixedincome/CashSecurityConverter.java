/**
 * Copyright (C) 2011 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.financial.analytics.fixedincome;

import javax.time.calendar.ZonedDateTime;

import org.apache.commons.lang.Validate;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.core.holiday.HolidaySource;
import com.opengamma.financial.convention.ConventionBundle;
import com.opengamma.financial.convention.ConventionBundleSource;
import com.opengamma.financial.convention.InMemoryConventionBundleMaster;
import com.opengamma.financial.convention.calendar.Calendar;
import com.opengamma.financial.instrument.Convention;
import com.opengamma.financial.instrument.FixedIncomeInstrumentConverter;
import com.opengamma.financial.instrument.cash.CashDefinition;
import com.opengamma.financial.security.cash.CashSecurity;
import com.opengamma.financial.security.cash.CashSecurityVisitor;
import com.opengamma.id.Identifier;
import com.opengamma.util.money.Currency;

/**
 * 
 */
public class CashSecurityConverter implements CashSecurityVisitor<FixedIncomeInstrumentConverter<?>> {
  private final HolidaySource _holidaySource;
  private final ConventionBundleSource _conventionSource;

  public CashSecurityConverter(final HolidaySource holidaySource,
      final ConventionBundleSource conventionSource) {
    Validate.notNull(holidaySource, "holiday source");
    Validate.notNull(conventionSource, "convention source");
    _holidaySource = holidaySource;
    _conventionSource = conventionSource;
  }

  @Override
  public CashDefinition visitCashSecurity(final CashSecurity security) {
    Validate.notNull(security, "security");
    ConventionBundle conventions = _conventionSource.getConventionBundle(security.getIdentifiers());
    final Currency currency = security.getCurrency();
    if (conventions == null) {
      // remove this
      conventions = _conventionSource.getConventionBundle(Identifier.of(InMemoryConventionBundleMaster.SIMPLE_NAME_SCHEME, currency.getCode() + "_GENERIC_CASH"));
      if (conventions == null) {
        throw new OpenGammaRuntimeException("Could not get convention for " + security);
      }
    }
    final Calendar calendar = CalendarUtil.getCalendar(_holidaySource, currency);
    final ZonedDateTime maturityDate = security.getMaturity();
    final Convention convention = new Convention(conventions.getSettlementDays(), conventions.getDayCount(),
        conventions.getBusinessDayConvention(), calendar, currency.getCode() + "_CASH_CONVENTION");
    return new CashDefinition(currency, maturityDate, security.getAmount(), security.getRate() / 100, convention);
  }

}
