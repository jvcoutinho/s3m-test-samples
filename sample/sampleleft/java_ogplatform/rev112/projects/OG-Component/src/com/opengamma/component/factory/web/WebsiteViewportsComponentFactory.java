/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.component.factory.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.fudgemsg.FudgeContext;
import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;
import org.springframework.web.context.ServletContextAware;

import com.opengamma.component.ComponentRepository;
import com.opengamma.component.factory.AbstractComponentFactory;
import com.opengamma.core.change.AggregatingChangeManager;
import com.opengamma.core.change.ChangeManager;
import com.opengamma.core.change.ChangeProvider;
import com.opengamma.core.position.PositionSource;
import com.opengamma.core.security.SecuritySource;
import com.opengamma.engine.ComputationTargetResolver;
import com.opengamma.engine.marketdata.NamedMarketDataSpecificationRepository;
import com.opengamma.engine.view.ViewDefinitionRepository;
import com.opengamma.engine.view.ViewProcessor;
import com.opengamma.financial.aggregation.PortfolioAggregationFunctions;
import com.opengamma.financial.view.ManageableViewDefinitionRepository;
import com.opengamma.livedata.UserPrincipal;
import com.opengamma.master.config.ConfigMaster;
import com.opengamma.master.historicaltimeseries.HistoricalTimeSeriesMaster;
import com.opengamma.master.marketdatasnapshot.MarketDataSnapshotMaster;
import com.opengamma.master.portfolio.PortfolioMaster;
import com.opengamma.master.position.PositionMaster;
import com.opengamma.master.security.SecurityMaster;
import com.opengamma.util.fudgemsg.OpenGammaFudgeContext;
import com.opengamma.web.server.AggregatedViewDefinitionManager;
import com.opengamma.web.server.push.ConnectionManagerImpl;
import com.opengamma.web.server.push.LongPollingConnectionManager;
import com.opengamma.web.server.push.MasterChangeManager;
import com.opengamma.web.server.push.WebPushServletContextUtils;
import com.opengamma.web.server.push.analytics.AnalyticsColumnsJsonWriter;
import com.opengamma.web.server.push.analytics.AnalyticsViewManager;
import com.opengamma.web.server.push.analytics.ViewportResultsJsonWriter;
import com.opengamma.web.server.push.analytics.formatting.ResultsFormatter;
import com.opengamma.web.server.push.rest.AggregatorNamesResource;
import com.opengamma.web.server.push.rest.LiveDataSourcesResource;
import com.opengamma.web.server.push.rest.MarketDataSnapshotListResource;
import com.opengamma.web.server.push.rest.MasterType;
import com.opengamma.web.server.push.rest.TimeSeriesResolverKeysResource;
import com.opengamma.web.server.push.rest.ViewDefinitionEntriesResource;
import com.opengamma.web.server.push.rest.ViewsResource;
import com.opengamma.web.server.push.rest.json.AnalyticsColumnGroupsMessageBodyWriter;
import com.opengamma.web.server.push.rest.json.Compressor;
import com.opengamma.web.server.push.rest.json.DependencyGraphGridStructureMessageBodyWriter;
import com.opengamma.web.server.push.rest.json.PortfolioGridStructureMessageBodyWriter;
import com.opengamma.web.server.push.rest.json.PrimitivesGridStructureMessageBodyWriter;
import com.opengamma.web.server.push.rest.json.ViewportResultsMessageBodyWriter;
import com.opengamma.web.server.push.rest.json.ViewportVersionMessageBodyWriter;

/**
 * Component factory for the main website viewports (for analytics).
 */
@BeanDefinition
public class WebsiteViewportsComponentFactory extends AbstractComponentFactory {

  /**
   * The configuration master.
   */
  @PropertyDefinition(validate = "notNull")
  private ConfigMaster _configMaster;
  /**
   * The security master.
   */
  @PropertyDefinition(validate = "notNull")
  private SecurityMaster _securityMaster;
  /**
   * The security source.
   */
  @PropertyDefinition(validate = "notNull")
  private SecuritySource _securitySource;
  /**
   * The position master.
   */
  @PropertyDefinition(validate = "notNull")
  private PositionMaster _positionMaster;
  /**
   * The portfolio master.
   */
  @PropertyDefinition(validate = "notNull")
  private PortfolioMaster _portfolioMaster;
  /**
   * The position source.
   */
  @PropertyDefinition(validate = "notNull")
  private PositionSource _positionSource;
  /**
   * The computation target resolver.
   */
  @PropertyDefinition(validate = "notNull")
  private ComputationTargetResolver _computationTargetResolver;
  /**
   * The time-series master.
   */
  @PropertyDefinition(validate = "notNull")
  private HistoricalTimeSeriesMaster _historicalTimeSeriesMaster;
  /**
   * The user master.
   */
  @PropertyDefinition(validate = "notNull")
  private PositionMaster _userPositionMaster;
  /**
   * The user master.
   */
  @PropertyDefinition(validate = "notNull")
  private PortfolioMaster _userPortfolioMaster;
  /**
   * The combined view definition repository.
   */
  @PropertyDefinition(validate = "notNull")
  private ViewDefinitionRepository _viewDefinitionRepository;
  /**
   * The user view definition repository.
   */
  @PropertyDefinition(validate = "notNull")
  private ManageableViewDefinitionRepository _userViewDefinitionRepository;
  /**
   * The view processor.
   */
  @PropertyDefinition(validate = "notNull")
  private ViewProcessor _viewProcessor;
  /**
   * The portfolio aggregation functions.
   */
  @PropertyDefinition(validate = "notNull")
  private PortfolioAggregationFunctions _portfolioAggregationFunctions;
  /**
   * The market data snapshot master.
   */
  @PropertyDefinition(validate = "notNull")
  private MarketDataSnapshotMaster _marketDataSnapshotMaster;
  /**
   * The user.
   */
  @PropertyDefinition(validate = "notNull")
  private UserPrincipal _user;
  /**
   * The fudge context.
   */
  @PropertyDefinition(validate = "notNull")
  private FudgeContext _fudgeContext = OpenGammaFudgeContext.getInstance();
  /**
   * For looking up market data provider specifications by name
   */
  @PropertyDefinition(validate = "notNull")
  private NamedMarketDataSpecificationRepository _marketDataSpecificationRepository;

  //-------------------------------------------------------------------------
  @Override
  public void init(ComponentRepository repo, LinkedHashMap<String, String> configuration) {
    final LongPollingConnectionManager longPolling = buildLongPolling();
    ChangeManager changeMgr = buildChangeManager();
    MasterChangeManager masterChangeMgr = buildMasterChangeManager();
    final ConnectionManagerImpl connectionMgr = new ConnectionManagerImpl(changeMgr, masterChangeMgr, longPolling);
    AggregatorNamesResource aggregatorsResource = new AggregatorNamesResource(getPortfolioAggregationFunctions().getMappedFunctions().keySet());
    MarketDataSnapshotListResource snapshotResource = new MarketDataSnapshotListResource(getMarketDataSnapshotMaster());

    AggregatedViewDefinitionManager aggregatedViewDefManager = new AggregatedViewDefinitionManager(
        getPositionSource(),
        getSecuritySource(),
        getViewDefinitionRepository(),
        getUserViewDefinitionRepository(),
        getUserPortfolioMaster(),
        getUserPositionMaster(),
        getPortfolioAggregationFunctions().getMappedFunctions());
    AnalyticsViewManager analyticsViewManager = new AnalyticsViewManager(getViewProcessor(),
                                                                         aggregatedViewDefManager,
                                                                         getMarketDataSnapshotMaster(),
                                                                         getComputationTargetResolver(),
                                                                         getMarketDataSpecificationRepository());
    ResultsFormatter resultsFormatter = new ResultsFormatter();
    AnalyticsColumnsJsonWriter columnWriter = new AnalyticsColumnsJsonWriter(resultsFormatter);
    ViewportResultsJsonWriter viewportResultsWriter = new ViewportResultsJsonWriter(resultsFormatter);

    repo.getRestComponents().publishResource(aggregatorsResource);
    repo.getRestComponents().publishResource(snapshotResource);
    repo.getRestComponents().publishResource(new LiveDataSourcesResource(getMarketDataSpecificationRepository()));
    repo.getRestComponents().publishResource(new ViewsResource(analyticsViewManager, connectionMgr));
    repo.getRestComponents().publishResource(new Compressor());
    repo.getRestComponents().publishResource(new TimeSeriesResolverKeysResource(getConfigMaster()));
    repo.getRestComponents().publishHelper(new ViewportVersionMessageBodyWriter());
    repo.getRestComponents().publishHelper(new PrimitivesGridStructureMessageBodyWriter(columnWriter));
    repo.getRestComponents().publishHelper(new PortfolioGridStructureMessageBodyWriter(columnWriter));
    repo.getRestComponents().publishHelper(new DependencyGraphGridStructureMessageBodyWriter(columnWriter));
    repo.getRestComponents().publishHelper(new AnalyticsColumnGroupsMessageBodyWriter(columnWriter));
    repo.getRestComponents().publishHelper(new ViewportResultsMessageBodyWriter(viewportResultsWriter));
    repo.getRestComponents().publishHelper(new ViewDefinitionEntriesResource(getViewDefinitionRepository()));

    // these items need to be available to the servlet, but aren't important enough to be published components
    repo.registerServletContextAware(new ServletContextAware() {
      @Override
      public void setServletContext(ServletContext servletContext) {
        WebPushServletContextUtils.setConnectionManager(servletContext, connectionMgr);
        WebPushServletContextUtils.setLongPollingConnectionManager(servletContext, longPolling);
      }
    });
  }

  protected LongPollingConnectionManager buildLongPolling() {
    return new LongPollingConnectionManager();
  }

  protected ChangeManager buildChangeManager() {
    List<ChangeProvider> providers = new ArrayList<ChangeProvider>();
    providers.add(getPositionMaster());
    providers.add(getPortfolioMaster());
    providers.add(getSecurityMaster());
    providers.add(getHistoricalTimeSeriesMaster());
    providers.add(getConfigMaster());
    return new AggregatingChangeManager(providers);
  }

  protected MasterChangeManager buildMasterChangeManager() {
    Map<MasterType, ChangeProvider> providers = new HashMap<MasterType, ChangeProvider>();
    providers.put(MasterType.POSITION, getPositionMaster());
    providers.put(MasterType.PORTFOLIO, getPortfolioMaster());
    providers.put(MasterType.SECURITY, getSecurityMaster());
    providers.put(MasterType.TIME_SERIES, getHistoricalTimeSeriesMaster());
    providers.put(MasterType.CONFIG, getConfigMaster());
    return new MasterChangeManager(providers);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code WebsiteViewportsComponentFactory}.
   * @return the meta-bean, not null
   */
  public static WebsiteViewportsComponentFactory.Meta meta() {
    return WebsiteViewportsComponentFactory.Meta.INSTANCE;
  }
  static {
    JodaBeanUtils.registerMetaBean(WebsiteViewportsComponentFactory.Meta.INSTANCE);
  }

  @Override
  public WebsiteViewportsComponentFactory.Meta metaBean() {
    return WebsiteViewportsComponentFactory.Meta.INSTANCE;
  }

  @Override
  protected Object propertyGet(String propertyName, boolean quiet) {
    switch (propertyName.hashCode()) {
      case 10395716:  // configMaster
        return getConfigMaster();
      case -887218750:  // securityMaster
        return getSecurityMaster();
      case -702456965:  // securitySource
        return getSecuritySource();
      case -1840419605:  // positionMaster
        return getPositionMaster();
      case -772274742:  // portfolioMaster
        return getPortfolioMaster();
      case -1655657820:  // positionSource
        return getPositionSource();
      case 1562222174:  // computationTargetResolver
        return getComputationTargetResolver();
      case 173967376:  // historicalTimeSeriesMaster
        return getHistoricalTimeSeriesMaster();
      case 1808868758:  // userPositionMaster
        return getUserPositionMaster();
      case 686514815:  // userPortfolioMaster
        return getUserPortfolioMaster();
      case -952415422:  // viewDefinitionRepository
        return getViewDefinitionRepository();
      case -1371772371:  // userViewDefinitionRepository
        return getUserViewDefinitionRepository();
      case -1697555603:  // viewProcessor
        return getViewProcessor();
      case 940303425:  // portfolioAggregationFunctions
        return getPortfolioAggregationFunctions();
      case 2090650860:  // marketDataSnapshotMaster
        return getMarketDataSnapshotMaster();
      case 3599307:  // user
        return getUser();
      case -917704420:  // fudgeContext
        return getFudgeContext();
      case 1743800263:  // marketDataSpecificationRepository
        return getMarketDataSpecificationRepository();
    }
    return super.propertyGet(propertyName, quiet);
  }

  @Override
  protected void propertySet(String propertyName, Object newValue, boolean quiet) {
    switch (propertyName.hashCode()) {
      case 10395716:  // configMaster
        setConfigMaster((ConfigMaster) newValue);
        return;
      case -887218750:  // securityMaster
        setSecurityMaster((SecurityMaster) newValue);
        return;
      case -702456965:  // securitySource
        setSecuritySource((SecuritySource) newValue);
        return;
      case -1840419605:  // positionMaster
        setPositionMaster((PositionMaster) newValue);
        return;
      case -772274742:  // portfolioMaster
        setPortfolioMaster((PortfolioMaster) newValue);
        return;
      case -1655657820:  // positionSource
        setPositionSource((PositionSource) newValue);
        return;
      case 1562222174:  // computationTargetResolver
        setComputationTargetResolver((ComputationTargetResolver) newValue);
        return;
      case 173967376:  // historicalTimeSeriesMaster
        setHistoricalTimeSeriesMaster((HistoricalTimeSeriesMaster) newValue);
        return;
      case 1808868758:  // userPositionMaster
        setUserPositionMaster((PositionMaster) newValue);
        return;
      case 686514815:  // userPortfolioMaster
        setUserPortfolioMaster((PortfolioMaster) newValue);
        return;
      case -952415422:  // viewDefinitionRepository
        setViewDefinitionRepository((ViewDefinitionRepository) newValue);
        return;
      case -1371772371:  // userViewDefinitionRepository
        setUserViewDefinitionRepository((ManageableViewDefinitionRepository) newValue);
        return;
      case -1697555603:  // viewProcessor
        setViewProcessor((ViewProcessor) newValue);
        return;
      case 940303425:  // portfolioAggregationFunctions
        setPortfolioAggregationFunctions((PortfolioAggregationFunctions) newValue);
        return;
      case 2090650860:  // marketDataSnapshotMaster
        setMarketDataSnapshotMaster((MarketDataSnapshotMaster) newValue);
        return;
      case 3599307:  // user
        setUser((UserPrincipal) newValue);
        return;
      case -917704420:  // fudgeContext
        setFudgeContext((FudgeContext) newValue);
        return;
      case 1743800263:  // marketDataSpecificationRepository
        setMarketDataSpecificationRepository((NamedMarketDataSpecificationRepository) newValue);
        return;
    }
    super.propertySet(propertyName, newValue, quiet);
  }

  @Override
  protected void validate() {
    JodaBeanUtils.notNull(_configMaster, "configMaster");
    JodaBeanUtils.notNull(_securityMaster, "securityMaster");
    JodaBeanUtils.notNull(_securitySource, "securitySource");
    JodaBeanUtils.notNull(_positionMaster, "positionMaster");
    JodaBeanUtils.notNull(_portfolioMaster, "portfolioMaster");
    JodaBeanUtils.notNull(_positionSource, "positionSource");
    JodaBeanUtils.notNull(_computationTargetResolver, "computationTargetResolver");
    JodaBeanUtils.notNull(_historicalTimeSeriesMaster, "historicalTimeSeriesMaster");
    JodaBeanUtils.notNull(_userPositionMaster, "userPositionMaster");
    JodaBeanUtils.notNull(_userPortfolioMaster, "userPortfolioMaster");
    JodaBeanUtils.notNull(_viewDefinitionRepository, "viewDefinitionRepository");
    JodaBeanUtils.notNull(_userViewDefinitionRepository, "userViewDefinitionRepository");
    JodaBeanUtils.notNull(_viewProcessor, "viewProcessor");
    JodaBeanUtils.notNull(_portfolioAggregationFunctions, "portfolioAggregationFunctions");
    JodaBeanUtils.notNull(_marketDataSnapshotMaster, "marketDataSnapshotMaster");
    JodaBeanUtils.notNull(_user, "user");
    JodaBeanUtils.notNull(_fudgeContext, "fudgeContext");
    JodaBeanUtils.notNull(_marketDataSpecificationRepository, "marketDataSpecificationRepository");
    super.validate();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      WebsiteViewportsComponentFactory other = (WebsiteViewportsComponentFactory) obj;
      return JodaBeanUtils.equal(getConfigMaster(), other.getConfigMaster()) &&
          JodaBeanUtils.equal(getSecurityMaster(), other.getSecurityMaster()) &&
          JodaBeanUtils.equal(getSecuritySource(), other.getSecuritySource()) &&
          JodaBeanUtils.equal(getPositionMaster(), other.getPositionMaster()) &&
          JodaBeanUtils.equal(getPortfolioMaster(), other.getPortfolioMaster()) &&
          JodaBeanUtils.equal(getPositionSource(), other.getPositionSource()) &&
          JodaBeanUtils.equal(getComputationTargetResolver(), other.getComputationTargetResolver()) &&
          JodaBeanUtils.equal(getHistoricalTimeSeriesMaster(), other.getHistoricalTimeSeriesMaster()) &&
          JodaBeanUtils.equal(getUserPositionMaster(), other.getUserPositionMaster()) &&
          JodaBeanUtils.equal(getUserPortfolioMaster(), other.getUserPortfolioMaster()) &&
          JodaBeanUtils.equal(getViewDefinitionRepository(), other.getViewDefinitionRepository()) &&
          JodaBeanUtils.equal(getUserViewDefinitionRepository(), other.getUserViewDefinitionRepository()) &&
          JodaBeanUtils.equal(getViewProcessor(), other.getViewProcessor()) &&
          JodaBeanUtils.equal(getPortfolioAggregationFunctions(), other.getPortfolioAggregationFunctions()) &&
          JodaBeanUtils.equal(getMarketDataSnapshotMaster(), other.getMarketDataSnapshotMaster()) &&
          JodaBeanUtils.equal(getUser(), other.getUser()) &&
          JodaBeanUtils.equal(getFudgeContext(), other.getFudgeContext()) &&
          JodaBeanUtils.equal(getMarketDataSpecificationRepository(), other.getMarketDataSpecificationRepository()) &&
          super.equals(obj);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash += hash * 31 + JodaBeanUtils.hashCode(getConfigMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getSecurityMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getSecuritySource());
    hash += hash * 31 + JodaBeanUtils.hashCode(getPositionMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getPortfolioMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getPositionSource());
    hash += hash * 31 + JodaBeanUtils.hashCode(getComputationTargetResolver());
    hash += hash * 31 + JodaBeanUtils.hashCode(getHistoricalTimeSeriesMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getUserPositionMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getUserPortfolioMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getViewDefinitionRepository());
    hash += hash * 31 + JodaBeanUtils.hashCode(getUserViewDefinitionRepository());
    hash += hash * 31 + JodaBeanUtils.hashCode(getViewProcessor());
    hash += hash * 31 + JodaBeanUtils.hashCode(getPortfolioAggregationFunctions());
    hash += hash * 31 + JodaBeanUtils.hashCode(getMarketDataSnapshotMaster());
    hash += hash * 31 + JodaBeanUtils.hashCode(getUser());
    hash += hash * 31 + JodaBeanUtils.hashCode(getFudgeContext());
    hash += hash * 31 + JodaBeanUtils.hashCode(getMarketDataSpecificationRepository());
    return hash ^ super.hashCode();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the configuration master.
   * @return the value of the property, not null
   */
  public ConfigMaster getConfigMaster() {
    return _configMaster;
  }

  /**
   * Sets the configuration master.
   * @param configMaster  the new value of the property, not null
   */
  public void setConfigMaster(ConfigMaster configMaster) {
    JodaBeanUtils.notNull(configMaster, "configMaster");
    this._configMaster = configMaster;
  }

  /**
   * Gets the the {@code configMaster} property.
   * @return the property, not null
   */
  public final Property<ConfigMaster> configMaster() {
    return metaBean().configMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the security master.
   * @return the value of the property, not null
   */
  public SecurityMaster getSecurityMaster() {
    return _securityMaster;
  }

  /**
   * Sets the security master.
   * @param securityMaster  the new value of the property, not null
   */
  public void setSecurityMaster(SecurityMaster securityMaster) {
    JodaBeanUtils.notNull(securityMaster, "securityMaster");
    this._securityMaster = securityMaster;
  }

  /**
   * Gets the the {@code securityMaster} property.
   * @return the property, not null
   */
  public final Property<SecurityMaster> securityMaster() {
    return metaBean().securityMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the security source.
   * @return the value of the property, not null
   */
  public SecuritySource getSecuritySource() {
    return _securitySource;
  }

  /**
   * Sets the security source.
   * @param securitySource  the new value of the property, not null
   */
  public void setSecuritySource(SecuritySource securitySource) {
    JodaBeanUtils.notNull(securitySource, "securitySource");
    this._securitySource = securitySource;
  }

  /**
   * Gets the the {@code securitySource} property.
   * @return the property, not null
   */
  public final Property<SecuritySource> securitySource() {
    return metaBean().securitySource().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the position master.
   * @return the value of the property, not null
   */
  public PositionMaster getPositionMaster() {
    return _positionMaster;
  }

  /**
   * Sets the position master.
   * @param positionMaster  the new value of the property, not null
   */
  public void setPositionMaster(PositionMaster positionMaster) {
    JodaBeanUtils.notNull(positionMaster, "positionMaster");
    this._positionMaster = positionMaster;
  }

  /**
   * Gets the the {@code positionMaster} property.
   * @return the property, not null
   */
  public final Property<PositionMaster> positionMaster() {
    return metaBean().positionMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the portfolio master.
   * @return the value of the property, not null
   */
  public PortfolioMaster getPortfolioMaster() {
    return _portfolioMaster;
  }

  /**
   * Sets the portfolio master.
   * @param portfolioMaster  the new value of the property, not null
   */
  public void setPortfolioMaster(PortfolioMaster portfolioMaster) {
    JodaBeanUtils.notNull(portfolioMaster, "portfolioMaster");
    this._portfolioMaster = portfolioMaster;
  }

  /**
   * Gets the the {@code portfolioMaster} property.
   * @return the property, not null
   */
  public final Property<PortfolioMaster> portfolioMaster() {
    return metaBean().portfolioMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the position source.
   * @return the value of the property, not null
   */
  public PositionSource getPositionSource() {
    return _positionSource;
  }

  /**
   * Sets the position source.
   * @param positionSource  the new value of the property, not null
   */
  public void setPositionSource(PositionSource positionSource) {
    JodaBeanUtils.notNull(positionSource, "positionSource");
    this._positionSource = positionSource;
  }

  /**
   * Gets the the {@code positionSource} property.
   * @return the property, not null
   */
  public final Property<PositionSource> positionSource() {
    return metaBean().positionSource().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the computation target resolver.
   * @return the value of the property, not null
   */
  public ComputationTargetResolver getComputationTargetResolver() {
    return _computationTargetResolver;
  }

  /**
   * Sets the computation target resolver.
   * @param computationTargetResolver  the new value of the property, not null
   */
  public void setComputationTargetResolver(ComputationTargetResolver computationTargetResolver) {
    JodaBeanUtils.notNull(computationTargetResolver, "computationTargetResolver");
    this._computationTargetResolver = computationTargetResolver;
  }

  /**
   * Gets the the {@code computationTargetResolver} property.
   * @return the property, not null
   */
  public final Property<ComputationTargetResolver> computationTargetResolver() {
    return metaBean().computationTargetResolver().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time-series master.
   * @return the value of the property, not null
   */
  public HistoricalTimeSeriesMaster getHistoricalTimeSeriesMaster() {
    return _historicalTimeSeriesMaster;
  }

  /**
   * Sets the time-series master.
   * @param historicalTimeSeriesMaster  the new value of the property, not null
   */
  public void setHistoricalTimeSeriesMaster(HistoricalTimeSeriesMaster historicalTimeSeriesMaster) {
    JodaBeanUtils.notNull(historicalTimeSeriesMaster, "historicalTimeSeriesMaster");
    this._historicalTimeSeriesMaster = historicalTimeSeriesMaster;
  }

  /**
   * Gets the the {@code historicalTimeSeriesMaster} property.
   * @return the property, not null
   */
  public final Property<HistoricalTimeSeriesMaster> historicalTimeSeriesMaster() {
    return metaBean().historicalTimeSeriesMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the user master.
   * @return the value of the property, not null
   */
  public PositionMaster getUserPositionMaster() {
    return _userPositionMaster;
  }

  /**
   * Sets the user master.
   * @param userPositionMaster  the new value of the property, not null
   */
  public void setUserPositionMaster(PositionMaster userPositionMaster) {
    JodaBeanUtils.notNull(userPositionMaster, "userPositionMaster");
    this._userPositionMaster = userPositionMaster;
  }

  /**
   * Gets the the {@code userPositionMaster} property.
   * @return the property, not null
   */
  public final Property<PositionMaster> userPositionMaster() {
    return metaBean().userPositionMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the user master.
   * @return the value of the property, not null
   */
  public PortfolioMaster getUserPortfolioMaster() {
    return _userPortfolioMaster;
  }

  /**
   * Sets the user master.
   * @param userPortfolioMaster  the new value of the property, not null
   */
  public void setUserPortfolioMaster(PortfolioMaster userPortfolioMaster) {
    JodaBeanUtils.notNull(userPortfolioMaster, "userPortfolioMaster");
    this._userPortfolioMaster = userPortfolioMaster;
  }

  /**
   * Gets the the {@code userPortfolioMaster} property.
   * @return the property, not null
   */
  public final Property<PortfolioMaster> userPortfolioMaster() {
    return metaBean().userPortfolioMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the combined view definition repository.
   * @return the value of the property, not null
   */
  public ViewDefinitionRepository getViewDefinitionRepository() {
    return _viewDefinitionRepository;
  }

  /**
   * Sets the combined view definition repository.
   * @param viewDefinitionRepository  the new value of the property, not null
   */
  public void setViewDefinitionRepository(ViewDefinitionRepository viewDefinitionRepository) {
    JodaBeanUtils.notNull(viewDefinitionRepository, "viewDefinitionRepository");
    this._viewDefinitionRepository = viewDefinitionRepository;
  }

  /**
   * Gets the the {@code viewDefinitionRepository} property.
   * @return the property, not null
   */
  public final Property<ViewDefinitionRepository> viewDefinitionRepository() {
    return metaBean().viewDefinitionRepository().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the user view definition repository.
   * @return the value of the property, not null
   */
  public ManageableViewDefinitionRepository getUserViewDefinitionRepository() {
    return _userViewDefinitionRepository;
  }

  /**
   * Sets the user view definition repository.
   * @param userViewDefinitionRepository  the new value of the property, not null
   */
  public void setUserViewDefinitionRepository(ManageableViewDefinitionRepository userViewDefinitionRepository) {
    JodaBeanUtils.notNull(userViewDefinitionRepository, "userViewDefinitionRepository");
    this._userViewDefinitionRepository = userViewDefinitionRepository;
  }

  /**
   * Gets the the {@code userViewDefinitionRepository} property.
   * @return the property, not null
   */
  public final Property<ManageableViewDefinitionRepository> userViewDefinitionRepository() {
    return metaBean().userViewDefinitionRepository().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the view processor.
   * @return the value of the property, not null
   */
  public ViewProcessor getViewProcessor() {
    return _viewProcessor;
  }

  /**
   * Sets the view processor.
   * @param viewProcessor  the new value of the property, not null
   */
  public void setViewProcessor(ViewProcessor viewProcessor) {
    JodaBeanUtils.notNull(viewProcessor, "viewProcessor");
    this._viewProcessor = viewProcessor;
  }

  /**
   * Gets the the {@code viewProcessor} property.
   * @return the property, not null
   */
  public final Property<ViewProcessor> viewProcessor() {
    return metaBean().viewProcessor().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the portfolio aggregation functions.
   * @return the value of the property, not null
   */
  public PortfolioAggregationFunctions getPortfolioAggregationFunctions() {
    return _portfolioAggregationFunctions;
  }

  /**
   * Sets the portfolio aggregation functions.
   * @param portfolioAggregationFunctions  the new value of the property, not null
   */
  public void setPortfolioAggregationFunctions(PortfolioAggregationFunctions portfolioAggregationFunctions) {
    JodaBeanUtils.notNull(portfolioAggregationFunctions, "portfolioAggregationFunctions");
    this._portfolioAggregationFunctions = portfolioAggregationFunctions;
  }

  /**
   * Gets the the {@code portfolioAggregationFunctions} property.
   * @return the property, not null
   */
  public final Property<PortfolioAggregationFunctions> portfolioAggregationFunctions() {
    return metaBean().portfolioAggregationFunctions().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the market data snapshot master.
   * @return the value of the property, not null
   */
  public MarketDataSnapshotMaster getMarketDataSnapshotMaster() {
    return _marketDataSnapshotMaster;
  }

  /**
   * Sets the market data snapshot master.
   * @param marketDataSnapshotMaster  the new value of the property, not null
   */
  public void setMarketDataSnapshotMaster(MarketDataSnapshotMaster marketDataSnapshotMaster) {
    JodaBeanUtils.notNull(marketDataSnapshotMaster, "marketDataSnapshotMaster");
    this._marketDataSnapshotMaster = marketDataSnapshotMaster;
  }

  /**
   * Gets the the {@code marketDataSnapshotMaster} property.
   * @return the property, not null
   */
  public final Property<MarketDataSnapshotMaster> marketDataSnapshotMaster() {
    return metaBean().marketDataSnapshotMaster().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the user.
   * @return the value of the property, not null
   */
  public UserPrincipal getUser() {
    return _user;
  }

  /**
   * Sets the user.
   * @param user  the new value of the property, not null
   */
  public void setUser(UserPrincipal user) {
    JodaBeanUtils.notNull(user, "user");
    this._user = user;
  }

  /**
   * Gets the the {@code user} property.
   * @return the property, not null
   */
  public final Property<UserPrincipal> user() {
    return metaBean().user().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the fudge context.
   * @return the value of the property, not null
   */
  public FudgeContext getFudgeContext() {
    return _fudgeContext;
  }

  /**
   * Sets the fudge context.
   * @param fudgeContext  the new value of the property, not null
   */
  public void setFudgeContext(FudgeContext fudgeContext) {
    JodaBeanUtils.notNull(fudgeContext, "fudgeContext");
    this._fudgeContext = fudgeContext;
  }

  /**
   * Gets the the {@code fudgeContext} property.
   * @return the property, not null
   */
  public final Property<FudgeContext> fudgeContext() {
    return metaBean().fudgeContext().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets for looking up market data provider specifications by name
   * @return the value of the property, not null
   */
  public NamedMarketDataSpecificationRepository getMarketDataSpecificationRepository() {
    return _marketDataSpecificationRepository;
  }

  /**
   * Sets for looking up market data provider specifications by name
   * @param marketDataSpecificationRepository  the new value of the property, not null
   */
  public void setMarketDataSpecificationRepository(NamedMarketDataSpecificationRepository marketDataSpecificationRepository) {
    JodaBeanUtils.notNull(marketDataSpecificationRepository, "marketDataSpecificationRepository");
    this._marketDataSpecificationRepository = marketDataSpecificationRepository;
  }

  /**
   * Gets the the {@code marketDataSpecificationRepository} property.
   * @return the property, not null
   */
  public final Property<NamedMarketDataSpecificationRepository> marketDataSpecificationRepository() {
    return metaBean().marketDataSpecificationRepository().createProperty(this);
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code WebsiteViewportsComponentFactory}.
   */
  public static class Meta extends AbstractComponentFactory.Meta {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code configMaster} property.
     */
    private final MetaProperty<ConfigMaster> _configMaster = DirectMetaProperty.ofReadWrite(
        this, "configMaster", WebsiteViewportsComponentFactory.class, ConfigMaster.class);
    /**
     * The meta-property for the {@code securityMaster} property.
     */
    private final MetaProperty<SecurityMaster> _securityMaster = DirectMetaProperty.ofReadWrite(
        this, "securityMaster", WebsiteViewportsComponentFactory.class, SecurityMaster.class);
    /**
     * The meta-property for the {@code securitySource} property.
     */
    private final MetaProperty<SecuritySource> _securitySource = DirectMetaProperty.ofReadWrite(
        this, "securitySource", WebsiteViewportsComponentFactory.class, SecuritySource.class);
    /**
     * The meta-property for the {@code positionMaster} property.
     */
    private final MetaProperty<PositionMaster> _positionMaster = DirectMetaProperty.ofReadWrite(
        this, "positionMaster", WebsiteViewportsComponentFactory.class, PositionMaster.class);
    /**
     * The meta-property for the {@code portfolioMaster} property.
     */
    private final MetaProperty<PortfolioMaster> _portfolioMaster = DirectMetaProperty.ofReadWrite(
        this, "portfolioMaster", WebsiteViewportsComponentFactory.class, PortfolioMaster.class);
    /**
     * The meta-property for the {@code positionSource} property.
     */
    private final MetaProperty<PositionSource> _positionSource = DirectMetaProperty.ofReadWrite(
        this, "positionSource", WebsiteViewportsComponentFactory.class, PositionSource.class);
    /**
     * The meta-property for the {@code computationTargetResolver} property.
     */
    private final MetaProperty<ComputationTargetResolver> _computationTargetResolver = DirectMetaProperty.ofReadWrite(
        this, "computationTargetResolver", WebsiteViewportsComponentFactory.class, ComputationTargetResolver.class);
    /**
     * The meta-property for the {@code historicalTimeSeriesMaster} property.
     */
    private final MetaProperty<HistoricalTimeSeriesMaster> _historicalTimeSeriesMaster = DirectMetaProperty.ofReadWrite(
        this, "historicalTimeSeriesMaster", WebsiteViewportsComponentFactory.class, HistoricalTimeSeriesMaster.class);
    /**
     * The meta-property for the {@code userPositionMaster} property.
     */
    private final MetaProperty<PositionMaster> _userPositionMaster = DirectMetaProperty.ofReadWrite(
        this, "userPositionMaster", WebsiteViewportsComponentFactory.class, PositionMaster.class);
    /**
     * The meta-property for the {@code userPortfolioMaster} property.
     */
    private final MetaProperty<PortfolioMaster> _userPortfolioMaster = DirectMetaProperty.ofReadWrite(
        this, "userPortfolioMaster", WebsiteViewportsComponentFactory.class, PortfolioMaster.class);
    /**
     * The meta-property for the {@code viewDefinitionRepository} property.
     */
    private final MetaProperty<ViewDefinitionRepository> _viewDefinitionRepository = DirectMetaProperty.ofReadWrite(
        this, "viewDefinitionRepository", WebsiteViewportsComponentFactory.class, ViewDefinitionRepository.class);
    /**
     * The meta-property for the {@code userViewDefinitionRepository} property.
     */
    private final MetaProperty<ManageableViewDefinitionRepository> _userViewDefinitionRepository = DirectMetaProperty.ofReadWrite(
        this, "userViewDefinitionRepository", WebsiteViewportsComponentFactory.class, ManageableViewDefinitionRepository.class);
    /**
     * The meta-property for the {@code viewProcessor} property.
     */
    private final MetaProperty<ViewProcessor> _viewProcessor = DirectMetaProperty.ofReadWrite(
        this, "viewProcessor", WebsiteViewportsComponentFactory.class, ViewProcessor.class);
    /**
     * The meta-property for the {@code portfolioAggregationFunctions} property.
     */
    private final MetaProperty<PortfolioAggregationFunctions> _portfolioAggregationFunctions = DirectMetaProperty.ofReadWrite(
        this, "portfolioAggregationFunctions", WebsiteViewportsComponentFactory.class, PortfolioAggregationFunctions.class);
    /**
     * The meta-property for the {@code marketDataSnapshotMaster} property.
     */
    private final MetaProperty<MarketDataSnapshotMaster> _marketDataSnapshotMaster = DirectMetaProperty.ofReadWrite(
        this, "marketDataSnapshotMaster", WebsiteViewportsComponentFactory.class, MarketDataSnapshotMaster.class);
    /**
     * The meta-property for the {@code user} property.
     */
    private final MetaProperty<UserPrincipal> _user = DirectMetaProperty.ofReadWrite(
        this, "user", WebsiteViewportsComponentFactory.class, UserPrincipal.class);
    /**
     * The meta-property for the {@code fudgeContext} property.
     */
    private final MetaProperty<FudgeContext> _fudgeContext = DirectMetaProperty.ofReadWrite(
        this, "fudgeContext", WebsiteViewportsComponentFactory.class, FudgeContext.class);
    /**
     * The meta-property for the {@code marketDataSpecificationRepository} property.
     */
    private final MetaProperty<NamedMarketDataSpecificationRepository> _marketDataSpecificationRepository = DirectMetaProperty.ofReadWrite(
        this, "marketDataSpecificationRepository", WebsiteViewportsComponentFactory.class, NamedMarketDataSpecificationRepository.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> _metaPropertyMap$ = new DirectMetaPropertyMap(
      this, (DirectMetaPropertyMap) super.metaPropertyMap(),
        "configMaster",
        "securityMaster",
        "securitySource",
        "positionMaster",
        "portfolioMaster",
        "positionSource",
        "computationTargetResolver",
        "historicalTimeSeriesMaster",
        "userPositionMaster",
        "userPortfolioMaster",
        "viewDefinitionRepository",
        "userViewDefinitionRepository",
        "viewProcessor",
        "portfolioAggregationFunctions",
        "marketDataSnapshotMaster",
        "user",
        "fudgeContext",
        "marketDataSpecificationRepository");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 10395716:  // configMaster
          return _configMaster;
        case -887218750:  // securityMaster
          return _securityMaster;
        case -702456965:  // securitySource
          return _securitySource;
        case -1840419605:  // positionMaster
          return _positionMaster;
        case -772274742:  // portfolioMaster
          return _portfolioMaster;
        case -1655657820:  // positionSource
          return _positionSource;
        case 1562222174:  // computationTargetResolver
          return _computationTargetResolver;
        case 173967376:  // historicalTimeSeriesMaster
          return _historicalTimeSeriesMaster;
        case 1808868758:  // userPositionMaster
          return _userPositionMaster;
        case 686514815:  // userPortfolioMaster
          return _userPortfolioMaster;
        case -952415422:  // viewDefinitionRepository
          return _viewDefinitionRepository;
        case -1371772371:  // userViewDefinitionRepository
          return _userViewDefinitionRepository;
        case -1697555603:  // viewProcessor
          return _viewProcessor;
        case 940303425:  // portfolioAggregationFunctions
          return _portfolioAggregationFunctions;
        case 2090650860:  // marketDataSnapshotMaster
          return _marketDataSnapshotMaster;
        case 3599307:  // user
          return _user;
        case -917704420:  // fudgeContext
          return _fudgeContext;
        case 1743800263:  // marketDataSpecificationRepository
          return _marketDataSpecificationRepository;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends WebsiteViewportsComponentFactory> builder() {
      return new DirectBeanBuilder<WebsiteViewportsComponentFactory>(new WebsiteViewportsComponentFactory());
    }

    @Override
    public Class<? extends WebsiteViewportsComponentFactory> beanType() {
      return WebsiteViewportsComponentFactory.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return _metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code configMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ConfigMaster> configMaster() {
      return _configMaster;
    }

    /**
     * The meta-property for the {@code securityMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<SecurityMaster> securityMaster() {
      return _securityMaster;
    }

    /**
     * The meta-property for the {@code securitySource} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<SecuritySource> securitySource() {
      return _securitySource;
    }

    /**
     * The meta-property for the {@code positionMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<PositionMaster> positionMaster() {
      return _positionMaster;
    }

    /**
     * The meta-property for the {@code portfolioMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<PortfolioMaster> portfolioMaster() {
      return _portfolioMaster;
    }

    /**
     * The meta-property for the {@code positionSource} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<PositionSource> positionSource() {
      return _positionSource;
    }

    /**
     * The meta-property for the {@code computationTargetResolver} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ComputationTargetResolver> computationTargetResolver() {
      return _computationTargetResolver;
    }

    /**
     * The meta-property for the {@code historicalTimeSeriesMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<HistoricalTimeSeriesMaster> historicalTimeSeriesMaster() {
      return _historicalTimeSeriesMaster;
    }

    /**
     * The meta-property for the {@code userPositionMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<PositionMaster> userPositionMaster() {
      return _userPositionMaster;
    }

    /**
     * The meta-property for the {@code userPortfolioMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<PortfolioMaster> userPortfolioMaster() {
      return _userPortfolioMaster;
    }

    /**
     * The meta-property for the {@code viewDefinitionRepository} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ViewDefinitionRepository> viewDefinitionRepository() {
      return _viewDefinitionRepository;
    }

    /**
     * The meta-property for the {@code userViewDefinitionRepository} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ManageableViewDefinitionRepository> userViewDefinitionRepository() {
      return _userViewDefinitionRepository;
    }

    /**
     * The meta-property for the {@code viewProcessor} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ViewProcessor> viewProcessor() {
      return _viewProcessor;
    }

    /**
     * The meta-property for the {@code portfolioAggregationFunctions} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<PortfolioAggregationFunctions> portfolioAggregationFunctions() {
      return _portfolioAggregationFunctions;
    }

    /**
     * The meta-property for the {@code marketDataSnapshotMaster} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<MarketDataSnapshotMaster> marketDataSnapshotMaster() {
      return _marketDataSnapshotMaster;
    }

    /**
     * The meta-property for the {@code user} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<UserPrincipal> user() {
      return _user;
    }

    /**
     * The meta-property for the {@code fudgeContext} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<FudgeContext> fudgeContext() {
      return _fudgeContext;
    }

    /**
     * The meta-property for the {@code marketDataSpecificationRepository} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<NamedMarketDataSpecificationRepository> marketDataSpecificationRepository() {
      return _marketDataSpecificationRepository;
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
