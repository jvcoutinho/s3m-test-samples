/**
 * Copyright (C) 2009 - 2010 by OpenGamma Inc.
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.timeseries;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.time.calendar.LocalDate;

import org.joda.beans.BeanDefinition;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.BasicMetaBean;
import org.joda.beans.impl.direct.DirectBean;
import org.joda.beans.impl.direct.DirectMetaProperty;

import com.opengamma.id.Identifier;
import com.opengamma.id.UniqueIdentifier;
import com.opengamma.util.db.PagingRequest;

/**
 * Request for searching for TimeSeries.
 * 
 * @param <T> LocalDate/java.sql.Date
 */
@BeanDefinition
public class TimeSeriesSearchRequest<T> extends DirectBean {
  /**
   * The request for paging.
   * By default all matching items will be returned.
   */
  @PropertyDefinition
  private PagingRequest _pagingRequest = PagingRequest.ALL;
  /**
   * The timeseries identifier for loading specific data points range
   */
  @PropertyDefinition
  private UniqueIdentifier _timeSeriesId;
  /**
   * Identifier value, will match against the <b>value</b> of the identifiers
   * (see Identifier.getValue());
   * wildcards allowed; 
   * will not match on the <b>key</b> of any of the identifiers;
   * null to search all identifiers
   */
  @PropertyDefinition
  private String _identifierValue;
  /**
   * List of Identifiers to search. Unlike _identifierValue, requires exact match
   * - no wildcards are allowed
   */
  @PropertyDefinition
  private final Set<Identifier> _identifiers = new HashSet<Identifier>();
  /**
   * Current date (if appicalable for identifiers)
   */
  @PropertyDefinition
  private LocalDate _currentDate;
  /**
   * The dataSource, null to search all dataSource.
   */
  @PropertyDefinition
  private String _dataSource;
  /**
   * The dataProvider, null to search all dataProvider.
   */
  @PropertyDefinition
  private String _dataProvider; 
  /**
   * The dataField, null to search all dataField.
   */
  @PropertyDefinition
  private String _dataField;
  /**
   * The observationTime, null to search all observationTime
   */
  @PropertyDefinition
  private String _observationTime;
  /**
   * The start date, null to search from start date in datastore.
   */
  @PropertyDefinition
  private T _start; 
  /**
   * The end date, null to search till end date in datastore.
   */
  @PropertyDefinition
  private T _end;
  /**
   * Set to true if to load datapoints, otherwise return just meta data
   */
  @PropertyDefinition
  private boolean _loadTimeSeries;
  /**
   * Set to true if to load the start and end date for timeseries
   */
  @PropertyDefinition
  private boolean _loadDates;
  
  public TimeSeriesSearchRequest() {
  }
  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code TimeSeriesSearchRequest<T>}.
   * @param <R>  the bean's generic type
   * @return the meta-bean, not null
   */
  @SuppressWarnings("unchecked")
  public static <R> TimeSeriesSearchRequest.Meta<R> meta() {
    return TimeSeriesSearchRequest.Meta.INSTANCE;
  }

  @SuppressWarnings("unchecked")
  @Override
  public TimeSeriesSearchRequest.Meta<T> metaBean() {
    return TimeSeriesSearchRequest.Meta.INSTANCE;
  }

  @Override
  protected Object propertyGet(String propertyName) {
    switch (propertyName.hashCode()) {
      case -2092032669:  // pagingRequest
        return getPagingRequest();
      case 1709694943:  // timeSeriesId
        return getTimeSeriesId();
      case 2085582408:  // identifierValue
        return getIdentifierValue();
      case 1368189162:  // identifiers
        return getIdentifiers();
      case 600751303:  // currentDate
        return getCurrentDate();
      case 1272470629:  // dataSource
        return getDataSource();
      case 339742651:  // dataProvider
        return getDataProvider();
      case -386794640:  // dataField
        return getDataField();
      case 951232793:  // observationTime
        return getObservationTime();
      case 109757538:  // start
        return getStart();
      case 100571:  // end
        return getEnd();
      case 1833789738:  // loadTimeSeries
        return isLoadTimeSeries();
      case 1364095295:  // loadDates
        return isLoadDates();
    }
    return super.propertyGet(propertyName);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void propertySet(String propertyName, Object newValue) {
    switch (propertyName.hashCode()) {
      case -2092032669:  // pagingRequest
        setPagingRequest((PagingRequest) newValue);
        return;
      case 1709694943:  // timeSeriesId
        setTimeSeriesId((UniqueIdentifier) newValue);
        return;
      case 2085582408:  // identifierValue
        setIdentifierValue((String) newValue);
        return;
      case 1368189162:  // identifiers
        setIdentifiers((Set<Identifier>) newValue);
        return;
      case 600751303:  // currentDate
        setCurrentDate((LocalDate) newValue);
        return;
      case 1272470629:  // dataSource
        setDataSource((String) newValue);
        return;
      case 339742651:  // dataProvider
        setDataProvider((String) newValue);
        return;
      case -386794640:  // dataField
        setDataField((String) newValue);
        return;
      case 951232793:  // observationTime
        setObservationTime((String) newValue);
        return;
      case 109757538:  // start
        setStart((T) newValue);
        return;
      case 100571:  // end
        setEnd((T) newValue);
        return;
      case 1833789738:  // loadTimeSeries
        setLoadTimeSeries((boolean) (Boolean) newValue);
        return;
      case 1364095295:  // loadDates
        setLoadDates((boolean) (Boolean) newValue);
        return;
    }
    super.propertySet(propertyName, newValue);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the request for paging.
   * By default all matching items will be returned.
   * @return the value of the property
   */
  public PagingRequest getPagingRequest() {
    return _pagingRequest;
  }

  /**
   * Sets the request for paging.
   * By default all matching items will be returned.
   * @param pagingRequest  the new value of the property
   */
  public void setPagingRequest(PagingRequest pagingRequest) {
    this._pagingRequest = pagingRequest;
  }

  /**
   * Gets the the {@code pagingRequest} property.
   * By default all matching items will be returned.
   * @return the property, not null
   */
  public final Property<PagingRequest> pagingRequest() {
    return metaBean().pagingRequest().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the timeseries identifier for loading specific data points range
   * @return the value of the property
   */
  public UniqueIdentifier getTimeSeriesId() {
    return _timeSeriesId;
  }

  /**
   * Sets the timeseries identifier for loading specific data points range
   * @param timeSeriesId  the new value of the property
   */
  public void setTimeSeriesId(UniqueIdentifier timeSeriesId) {
    this._timeSeriesId = timeSeriesId;
  }

  /**
   * Gets the the {@code timeSeriesId} property.
   * @return the property, not null
   */
  public final Property<UniqueIdentifier> timeSeriesId() {
    return metaBean().timeSeriesId().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets identifier value, will match against the <b>value</b> of the identifiers
   * (see Identifier.getValue());
   * wildcards allowed;
   * will not match on the <b>key</b> of any of the identifiers;
   * null to search all identifiers
   * @return the value of the property
   */
  public String getIdentifierValue() {
    return _identifierValue;
  }

  /**
   * Sets identifier value, will match against the <b>value</b> of the identifiers
   * (see Identifier.getValue());
   * wildcards allowed;
   * will not match on the <b>key</b> of any of the identifiers;
   * null to search all identifiers
   * @param identifierValue  the new value of the property
   */
  public void setIdentifierValue(String identifierValue) {
    this._identifierValue = identifierValue;
  }

  /**
   * Gets the the {@code identifierValue} property.
   * (see Identifier.getValue());
   * wildcards allowed;
   * will not match on the <b>key</b> of any of the identifiers;
   * null to search all identifiers
   * @return the property, not null
   */
  public final Property<String> identifierValue() {
    return metaBean().identifierValue().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets list of Identifiers to search. Unlike _identifierValue, requires exact match
   * - no wildcards are allowed
   * @return the value of the property
   */
  public Set<Identifier> getIdentifiers() {
    return _identifiers;
  }

  /**
   * Sets list of Identifiers to search. Unlike _identifierValue, requires exact match
   * - no wildcards are allowed
   * @param identifiers  the new value of the property
   */
  public void setIdentifiers(Set<Identifier> identifiers) {
    this._identifiers.clear();
    this._identifiers.addAll(identifiers);
  }

  /**
   * Gets the the {@code identifiers} property.
   * - no wildcards are allowed
   * @return the property, not null
   */
  public final Property<Set<Identifier>> identifiers() {
    return metaBean().identifiers().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets current date (if appicalable for identifiers)
   * @return the value of the property
   */
  public LocalDate getCurrentDate() {
    return _currentDate;
  }

  /**
   * Sets current date (if appicalable for identifiers)
   * @param currentDate  the new value of the property
   */
  public void setCurrentDate(LocalDate currentDate) {
    this._currentDate = currentDate;
  }

  /**
   * Gets the the {@code currentDate} property.
   * @return the property, not null
   */
  public final Property<LocalDate> currentDate() {
    return metaBean().currentDate().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the dataSource, null to search all dataSource.
   * @return the value of the property
   */
  public String getDataSource() {
    return _dataSource;
  }

  /**
   * Sets the dataSource, null to search all dataSource.
   * @param dataSource  the new value of the property
   */
  public void setDataSource(String dataSource) {
    this._dataSource = dataSource;
  }

  /**
   * Gets the the {@code dataSource} property.
   * @return the property, not null
   */
  public final Property<String> dataSource() {
    return metaBean().dataSource().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the dataProvider, null to search all dataProvider.
   * @return the value of the property
   */
  public String getDataProvider() {
    return _dataProvider;
  }

  /**
   * Sets the dataProvider, null to search all dataProvider.
   * @param dataProvider  the new value of the property
   */
  public void setDataProvider(String dataProvider) {
    this._dataProvider = dataProvider;
  }

  /**
   * Gets the the {@code dataProvider} property.
   * @return the property, not null
   */
  public final Property<String> dataProvider() {
    return metaBean().dataProvider().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the dataField, null to search all dataField.
   * @return the value of the property
   */
  public String getDataField() {
    return _dataField;
  }

  /**
   * Sets the dataField, null to search all dataField.
   * @param dataField  the new value of the property
   */
  public void setDataField(String dataField) {
    this._dataField = dataField;
  }

  /**
   * Gets the the {@code dataField} property.
   * @return the property, not null
   */
  public final Property<String> dataField() {
    return metaBean().dataField().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the observationTime, null to search all observationTime
   * @return the value of the property
   */
  public String getObservationTime() {
    return _observationTime;
  }

  /**
   * Sets the observationTime, null to search all observationTime
   * @param observationTime  the new value of the property
   */
  public void setObservationTime(String observationTime) {
    this._observationTime = observationTime;
  }

  /**
   * Gets the the {@code observationTime} property.
   * @return the property, not null
   */
  public final Property<String> observationTime() {
    return metaBean().observationTime().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the start date, null to search from start date in datastore.
   * @return the value of the property
   */
  public T getStart() {
    return _start;
  }

  /**
   * Sets the start date, null to search from start date in datastore.
   * @param start  the new value of the property
   */
  public void setStart(T start) {
    this._start = start;
  }

  /**
   * Gets the the {@code start} property.
   * @return the property, not null
   */
  public final Property<T> start() {
    return metaBean().start().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the end date, null to search till end date in datastore.
   * @return the value of the property
   */
  public T getEnd() {
    return _end;
  }

  /**
   * Sets the end date, null to search till end date in datastore.
   * @param end  the new value of the property
   */
  public void setEnd(T end) {
    this._end = end;
  }

  /**
   * Gets the the {@code end} property.
   * @return the property, not null
   */
  public final Property<T> end() {
    return metaBean().end().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets set to true if to load datapoints, otherwise return just meta data
   * @return the value of the property
   */
  public boolean isLoadTimeSeries() {
    return _loadTimeSeries;
  }

  /**
   * Sets set to true if to load datapoints, otherwise return just meta data
   * @param loadTimeSeries  the new value of the property
   */
  public void setLoadTimeSeries(boolean loadTimeSeries) {
    this._loadTimeSeries = loadTimeSeries;
  }

  /**
   * Gets the the {@code loadTimeSeries} property.
   * @return the property, not null
   */
  public final Property<Boolean> loadTimeSeries() {
    return metaBean().loadTimeSeries().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets set to true if to load the start and end date for timeseries
   * @return the value of the property
   */
  public boolean isLoadDates() {
    return _loadDates;
  }

  /**
   * Sets set to true if to load the start and end date for timeseries
   * @param loadDates  the new value of the property
   */
  public void setLoadDates(boolean loadDates) {
    this._loadDates = loadDates;
  }

  /**
   * Gets the the {@code loadDates} property.
   * @return the property, not null
   */
  public final Property<Boolean> loadDates() {
    return metaBean().loadDates().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code TimeSeriesSearchRequest}.
   */
  public static class Meta<T> extends BasicMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    @SuppressWarnings("unchecked")
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code pagingRequest} property.
     */
    private final MetaProperty<PagingRequest> _pagingRequest = DirectMetaProperty.ofReadWrite(this, "pagingRequest", PagingRequest.class);
    /**
     * The meta-property for the {@code timeSeriesId} property.
     */
    private final MetaProperty<UniqueIdentifier> _timeSeriesId = DirectMetaProperty.ofReadWrite(this, "timeSeriesId", UniqueIdentifier.class);
    /**
     * The meta-property for the {@code identifierValue} property.
     */
    private final MetaProperty<String> _identifierValue = DirectMetaProperty.ofReadWrite(this, "identifierValue", String.class);
    /**
     * The meta-property for the {@code identifiers} property.
     */
    @SuppressWarnings("unchecked")
    private final MetaProperty<Set<Identifier>> _identifiers = DirectMetaProperty.ofReadWrite(this, "identifiers", (Class) Set.class);
    /**
     * The meta-property for the {@code currentDate} property.
     */
    private final MetaProperty<LocalDate> _currentDate = DirectMetaProperty.ofReadWrite(this, "currentDate", LocalDate.class);
    /**
     * The meta-property for the {@code dataSource} property.
     */
    private final MetaProperty<String> _dataSource = DirectMetaProperty.ofReadWrite(this, "dataSource", String.class);
    /**
     * The meta-property for the {@code dataProvider} property.
     */
    private final MetaProperty<String> _dataProvider = DirectMetaProperty.ofReadWrite(this, "dataProvider", String.class);
    /**
     * The meta-property for the {@code dataField} property.
     */
    private final MetaProperty<String> _dataField = DirectMetaProperty.ofReadWrite(this, "dataField", String.class);
    /**
     * The meta-property for the {@code observationTime} property.
     */
    private final MetaProperty<String> _observationTime = DirectMetaProperty.ofReadWrite(this, "observationTime", String.class);
    /**
     * The meta-property for the {@code start} property.
     */
    @SuppressWarnings("unchecked")
    private final MetaProperty<T> _start = (DirectMetaProperty) DirectMetaProperty.ofReadWrite(this, "start", Object.class);
    /**
     * The meta-property for the {@code end} property.
     */
    @SuppressWarnings("unchecked")
    private final MetaProperty<T> _end = (DirectMetaProperty) DirectMetaProperty.ofReadWrite(this, "end", Object.class);
    /**
     * The meta-property for the {@code loadTimeSeries} property.
     */
    private final MetaProperty<Boolean> _loadTimeSeries = DirectMetaProperty.ofReadWrite(this, "loadTimeSeries", Boolean.TYPE);
    /**
     * The meta-property for the {@code loadDates} property.
     */
    private final MetaProperty<Boolean> _loadDates = DirectMetaProperty.ofReadWrite(this, "loadDates", Boolean.TYPE);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<Object>> _map;

    @SuppressWarnings("unchecked")
    protected Meta() {
      LinkedHashMap temp = new LinkedHashMap();
      temp.put("pagingRequest", _pagingRequest);
      temp.put("timeSeriesId", _timeSeriesId);
      temp.put("identifierValue", _identifierValue);
      temp.put("identifiers", _identifiers);
      temp.put("currentDate", _currentDate);
      temp.put("dataSource", _dataSource);
      temp.put("dataProvider", _dataProvider);
      temp.put("dataField", _dataField);
      temp.put("observationTime", _observationTime);
      temp.put("start", _start);
      temp.put("end", _end);
      temp.put("loadTimeSeries", _loadTimeSeries);
      temp.put("loadDates", _loadDates);
      _map = Collections.unmodifiableMap(temp);
    }

    @Override
    public TimeSeriesSearchRequest<T> createBean() {
      return new TimeSeriesSearchRequest<T>();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends TimeSeriesSearchRequest<T>> beanType() {
      return (Class) TimeSeriesSearchRequest.class;
    }

    @Override
    public Map<String, MetaProperty<Object>> metaPropertyMap() {
      return _map;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code pagingRequest} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<PagingRequest> pagingRequest() {
      return _pagingRequest;
    }

    /**
     * The meta-property for the {@code timeSeriesId} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<UniqueIdentifier> timeSeriesId() {
      return _timeSeriesId;
    }

    /**
     * The meta-property for the {@code identifierValue} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> identifierValue() {
      return _identifierValue;
    }

    /**
     * The meta-property for the {@code identifiers} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Set<Identifier>> identifiers() {
      return _identifiers;
    }

    /**
     * The meta-property for the {@code currentDate} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<LocalDate> currentDate() {
      return _currentDate;
    }

    /**
     * The meta-property for the {@code dataSource} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> dataSource() {
      return _dataSource;
    }

    /**
     * The meta-property for the {@code dataProvider} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> dataProvider() {
      return _dataProvider;
    }

    /**
     * The meta-property for the {@code dataField} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> dataField() {
      return _dataField;
    }

    /**
     * The meta-property for the {@code observationTime} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> observationTime() {
      return _observationTime;
    }

    /**
     * The meta-property for the {@code start} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<T> start() {
      return _start;
    }

    /**
     * The meta-property for the {@code end} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<T> end() {
      return _end;
    }

    /**
     * The meta-property for the {@code loadTimeSeries} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Boolean> loadTimeSeries() {
      return _loadTimeSeries;
    }

    /**
     * The meta-property for the {@code loadDates} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Boolean> loadDates() {
      return _loadDates;
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
