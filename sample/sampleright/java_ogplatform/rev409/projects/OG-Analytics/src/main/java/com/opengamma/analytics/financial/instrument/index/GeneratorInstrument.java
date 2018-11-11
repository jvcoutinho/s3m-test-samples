/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.instrument.index;

import javax.time.calendar.ZonedDateTime;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.Validate;

import com.opengamma.analytics.financial.instrument.InstrumentDefinition;

/**
 * Generic class for instrument generators (deposit, ois, irs, ...).
 * @param <ATTRIBUTE_TYPE> The type of attribute.
 */
public abstract class GeneratorInstrument<ATTRIBUTE_TYPE extends GeneratorAttribute> {

  /**
   * Name of the generator.
   */
  private final String _name;

  /**
   * Constructor.
   * @param name The generator name.
   */
  public GeneratorInstrument(String name) {
    Validate.notNull(name, "Name");
    _name = name;
  }

  /**
   * Gets the generator name.
   * @return The name.
   */
  public String getName() {
    return _name;
  }

  /**
   * Generate an instrument compatible with the generator from a reference date, one period and a market quote.
   * @param date The reference date. In general it is "today" or the trade date.
   * @param marketQuote The instrument market quote.
   * @param notional The instrument notional.
   * @param attribute The instrument attributes, as given by a GenratorAttribute.
   * @return The instrument.
   */
  public abstract InstrumentDefinition<?> generateInstrument(final ZonedDateTime date, final double marketQuote, final double notional, final ATTRIBUTE_TYPE attribute);

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + _name.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    GeneratorInstrument<?> other = (GeneratorInstrument<?>) obj;
    if (!ObjectUtils.equals(_name, other._name)) {
      return false;
    }
    return true;
  }

}
