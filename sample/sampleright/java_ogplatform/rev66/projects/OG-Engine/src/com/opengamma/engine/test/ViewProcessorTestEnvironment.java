/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.test;

import java.util.concurrent.Executors;

import javax.time.Instant;

import org.fudgemsg.FudgeContext;

import com.opengamma.core.config.ConfigSource;
import com.opengamma.core.position.PositionSource;
import com.opengamma.core.security.SecuritySource;
import com.opengamma.engine.ComputationTargetSpecification;
import com.opengamma.engine.DefaultComputationTargetResolver;
import com.opengamma.engine.InMemorySecuritySource;
import com.opengamma.engine.depgraph.DependencyGraphBuilderFactory;
import com.opengamma.engine.function.CachingFunctionRepositoryCompiler;
import com.opengamma.engine.function.CompiledFunctionService;
import com.opengamma.engine.function.FunctionCompilationContext;
import com.opengamma.engine.function.FunctionExecutionContext;
import com.opengamma.engine.function.FunctionRepository;
import com.opengamma.engine.function.InMemoryFunctionRepository;
import com.opengamma.engine.function.resolver.DefaultFunctionResolver;
import com.opengamma.engine.function.resolver.FunctionResolver;
import com.opengamma.engine.marketdata.InMemoryLKVMarketDataProvider;
import com.opengamma.engine.marketdata.InMemoryNamedMarketDataSpecificationRepository;
import com.opengamma.engine.marketdata.MarketDataProvider;
import com.opengamma.engine.marketdata.resolver.MarketDataProviderResolver;
import com.opengamma.engine.marketdata.resolver.SingleMarketDataProviderResolver;
import com.opengamma.engine.value.ValueRequirement;
import com.opengamma.engine.view.ViewCalculationConfiguration;
import com.opengamma.engine.view.ViewCalculationResultModel;
import com.opengamma.engine.view.ViewDefinition;
import com.opengamma.engine.view.ViewProcessImpl;
import com.opengamma.engine.view.ViewProcessorFactoryBean;
import com.opengamma.engine.view.ViewProcessorImpl;
import com.opengamma.engine.view.ViewResultModel;
import com.opengamma.engine.view.cache.InMemoryViewComputationCacheSource;
import com.opengamma.engine.view.calc.DependencyGraphExecutorFactory;
import com.opengamma.engine.view.calc.ExecutionResult;
import com.opengamma.engine.view.calc.SingleNodeExecutorFactory;
import com.opengamma.engine.view.calc.ViewComputationJob;
import com.opengamma.engine.view.calc.ViewResultListenerFactory;
import com.opengamma.engine.view.calcnode.JobDispatcher;
import com.opengamma.engine.view.calcnode.LocalNodeJobInvoker;
import com.opengamma.engine.view.calcnode.SimpleCalculationNode;
import com.opengamma.engine.view.calcnode.ViewProcessorQueryReceiver;
import com.opengamma.engine.view.calcnode.ViewProcessorQuerySender;
import com.opengamma.engine.view.calcnode.stats.DiscardingInvocationStatisticsGatherer;
import com.opengamma.engine.view.compilation.CompiledViewDefinitionWithGraphsImpl;
import com.opengamma.engine.view.compilation.ViewCompilationServices;
import com.opengamma.engine.view.compilation.ViewDefinitionCompiler;
import com.opengamma.engine.view.permission.DefaultViewPermissionProvider;
import com.opengamma.id.UniqueId;
import com.opengamma.id.VersionCorrection;
import com.opengamma.livedata.UserPrincipal;
import com.opengamma.transport.ByteArrayFudgeRequestSender;
import com.opengamma.transport.FudgeRequestDispatcher;
import com.opengamma.transport.InMemoryByteArrayRequestConduit;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.fudgemsg.OpenGammaFudgeContext;

/**
 * Provides access to a ready-made and customisable view processing environment for testing.
 */
public class ViewProcessorTestEnvironment {

  public static final UserPrincipal TEST_USER = UserPrincipal.getLocalUser();

  public static final String TEST_VIEW_DEFINITION_NAME = "Test View";
  public static final String TEST_CALC_CONFIG_NAME = "Test Calc Config";

  private static final ComputationTargetSpecification s_primitiveTarget = ComputationTargetSpecification.of(UniqueId.of("Scheme", "PrimitiveValue"));
  private static final ValueRequirement s_primitive1 = new ValueRequirement("Value1", s_primitiveTarget);
  private static final ValueRequirement s_primitive2 = new ValueRequirement("Value2", s_primitiveTarget);

  // Settings
  private MarketDataProvider _marketDataProvider;
  private MarketDataProviderResolver _marketDataProviderResolver;
  private SecuritySource _securitySource;
  private PositionSource _positionSource;
  private FunctionExecutionContext _functionExecutionContext;
  private FunctionCompilationContext _functionCompilationContext;
  private ViewDefinition _viewDefinition;
  private FunctionRepository _functionRepository;
  private DependencyGraphExecutorFactory<ExecutionResult> _dependencyGraphExecutorFactory;
  private DependencyGraphBuilderFactory _dependencyGraphBuilderFactory;

  // Environment
  private ViewProcessorImpl _viewProcessor;
  private FunctionResolver _functionResolver;
  private ConfigSource _configSource;
  private ViewResultListenerFactory _viewResultListenerFactory;

  public void init() {
    ViewProcessorFactoryBean vpFactBean = new ViewProcessorFactoryBean();
    vpFactBean.setName("test");

    FudgeContext fudgeContext = OpenGammaFudgeContext.getInstance();
    SecuritySource securitySource = getSecuritySource() != null ? getSecuritySource() : generateSecuritySource();
    FunctionCompilationContext functionCompilationContext = getFunctionCompilationContext() != null ? getFunctionCompilationContext() : generateFunctionCompilationContext();

    ConfigSource configSource = getConfigSource() != null ? getConfigSource() : generateConfigSource();

    InMemoryViewComputationCacheSource cacheSource = new InMemoryViewComputationCacheSource(fudgeContext);
    vpFactBean.setComputationCacheSource(cacheSource);

    DependencyGraphExecutorFactory<ExecutionResult> dependencyGraphExecutorFactory = getDependencyGraphExecutorFactory() != null ? getDependencyGraphExecutorFactory()
        : generateDependencyGraphExecutorFactory();
    vpFactBean.setDependencyGraphExecutorFactory(dependencyGraphExecutorFactory);

    FunctionRepository functionRepository = getFunctionRepository() != null ? getFunctionRepository() : generateFunctionRepository();
    final CompiledFunctionService compiledFunctions = new CompiledFunctionService(functionRepository, new CachingFunctionRepositoryCompiler(), functionCompilationContext);
    compiledFunctions.initialize();
    vpFactBean.setFunctionCompilationService(compiledFunctions);

    MarketDataProviderResolver marketDataProviderResolver = getMarketDataProviderResolver() != null ? getMarketDataProviderResolver() : generateMarketDataProviderResolver(securitySource);
    vpFactBean.setMarketDataProviderResolver(marketDataProviderResolver);

    vpFactBean.setConfigSource(configSource);
    vpFactBean.setViewPermissionProvider(new DefaultViewPermissionProvider());
    vpFactBean.setNamedMarketDataSpecificationRepository(new InMemoryNamedMarketDataSpecificationRepository());
    _configSource = configSource;

    ViewProcessorQueryReceiver calcNodeQueryReceiver = new ViewProcessorQueryReceiver();
    FudgeRequestDispatcher calcNodeQueryRequestDispatcher = new FudgeRequestDispatcher(calcNodeQueryReceiver);
    InMemoryByteArrayRequestConduit calcNodeQueryRequestConduit = new InMemoryByteArrayRequestConduit(calcNodeQueryRequestDispatcher);
    ByteArrayFudgeRequestSender calcNodeQueryRequestSender = new ByteArrayFudgeRequestSender(calcNodeQueryRequestConduit);
    ViewProcessorQuerySender calcNodeQuerySender = new ViewProcessorQuerySender(calcNodeQueryRequestSender);
    vpFactBean.setViewProcessorQueryReceiver(calcNodeQueryReceiver);

    FunctionExecutionContext functionExecutionContext = getFunctionExecutionContext() != null ? getFunctionExecutionContext() : generateFunctionExecutionContext();
    functionExecutionContext.setSecuritySource(securitySource);

    SimpleCalculationNode localCalcNode = new SimpleCalculationNode(cacheSource, compiledFunctions, functionExecutionContext, calcNodeQuerySender, "node", Executors.newCachedThreadPool(),
        new DiscardingInvocationStatisticsGatherer());
    LocalNodeJobInvoker jobInvoker = new LocalNodeJobInvoker(localCalcNode);
    vpFactBean.setComputationJobDispatcher(new JobDispatcher(jobInvoker));
    vpFactBean.setFunctionResolver(generateFunctionResolver(compiledFunctions));
    vpFactBean.setViewResultListenerFactory(_viewResultListenerFactory);
    _viewProcessor = (ViewProcessorImpl) vpFactBean.createObject();
  }
  
  public CompiledViewDefinitionWithGraphsImpl compileViewDefinition(Instant valuationTime, VersionCorrection versionCorrection) {
    if (getViewProcessor() == null) {
      throw new IllegalStateException(ViewProcessorTestEnvironment.class.getName() + " has not been initialised");
    }
    ViewCompilationServices compilationServices = new ViewCompilationServices(
        getMarketDataProvider().getAvailabilityProvider(),
        getFunctionResolver(),
        getFunctionCompilationContext(),
        getViewProcessor().getFunctionCompilationService().getExecutorService(),
        (getDependencyGraphBuilderFactory() != null) ? getDependencyGraphBuilderFactory() : generateDependencyGraphBuilderFactory());
    return ViewDefinitionCompiler.compile(getViewDefinition(), compilationServices, valuationTime, versionCorrection);
  }

  // Environment
  // -------------------------------------------------------------------------
  public ViewDefinition getViewDefinition() {
    return _viewDefinition;
  }
  
  public void setViewDefinition(ViewDefinition viewDefinition) {
    _viewDefinition = viewDefinition;
  }

  private ViewDefinition generateViewDefinition() {
    ViewDefinition testDefinition = new ViewDefinition(UniqueId.of("boo", "far"), TEST_VIEW_DEFINITION_NAME, TEST_USER);
    ViewCalculationConfiguration calcConfig = new ViewCalculationConfiguration(testDefinition, TEST_CALC_CONFIG_NAME);
    calcConfig.addSpecificRequirement(getPrimitive1());
    calcConfig.addSpecificRequirement(getPrimitive2());
    testDefinition.addViewCalculationConfiguration(calcConfig);
    
    setViewDefinition(testDefinition);
    return testDefinition;
  }
  
  public ConfigSource getConfigSource() {
    return _configSource;
  }
  
  public MockConfigSource getMockViewDefinitionRepository() {
    return (MockConfigSource) _configSource;
  }
  
  public void setConfigSource(ConfigSource configSource) {
    _configSource = configSource;
  }
  
  public void setViewResultListenerFactory(ViewResultListenerFactory viewResultListenerFactory) {
    _viewResultListenerFactory = viewResultListenerFactory;
  }

  private ConfigSource generateConfigSource() {
    MockConfigSource repository = new MockConfigSource();
    ViewDefinition defaultDefinition = getViewDefinition() != null ? getViewDefinition() : generateViewDefinition();
    repository.put(defaultDefinition);
    setConfigSource(repository);
    return repository;
  }
  
  public MarketDataProvider getMarketDataProvider() {
    return _marketDataProvider;
  }

  public void setMarketDataProvider(MarketDataProvider marketDataProvider) {
    ArgumentChecker.notNull(marketDataProvider, "marketDataProvider");
    _marketDataProvider = marketDataProvider;
  }
  
  private MarketDataProvider generateMarketDataProvider(SecuritySource securitySource) {
    InMemoryLKVMarketDataProvider provider = new InMemoryLKVMarketDataProvider(securitySource);
    provider.addValue(getPrimitive1(), 0);
    provider.addValue(getPrimitive2(), 0);
    setMarketDataProvider(provider);
    return provider;
  }
  
  public MarketDataProviderResolver getMarketDataProviderResolver() {
    return _marketDataProviderResolver;
  }
  
  public void setMarketDataProviderResolver(MarketDataProviderResolver marketDataProviderResolver) {
    ArgumentChecker.notNull(marketDataProviderResolver, "marketDataProviderResolver");
    _marketDataProviderResolver = marketDataProviderResolver;
  }
  
  private MarketDataProviderResolver generateMarketDataProviderResolver(SecuritySource securitySource) {
    MarketDataProvider marketDataProvider = getMarketDataProvider() != null ? getMarketDataProvider() : generateMarketDataProvider(securitySource);
    MarketDataProviderResolver resolver = new SingleMarketDataProviderResolver(marketDataProvider);
    setMarketDataProviderResolver(resolver);
    return resolver;
  }
  
  public FunctionRepository getFunctionRepository() {
    return _functionRepository;
  }
  
  public void setFunctionRepository(FunctionRepository functionRepository) {
    _functionRepository = functionRepository;
  }

  private FunctionRepository generateFunctionRepository() {
    FunctionRepository functionRepository = new InMemoryFunctionRepository();
    setFunctionRepository(functionRepository);
    return functionRepository;
  }
  
  public DependencyGraphBuilderFactory getDependencyGraphBuilderFactory() {
    return _dependencyGraphBuilderFactory;
  }

  public void setDependencyGraphBuilderFactory(final DependencyGraphBuilderFactory dependencyGraphBuilderFactory) {
    _dependencyGraphBuilderFactory = dependencyGraphBuilderFactory;
  }

  private DependencyGraphBuilderFactory generateDependencyGraphBuilderFactory() {
    final DependencyGraphBuilderFactory factory = new DependencyGraphBuilderFactory();
    setDependencyGraphBuilderFactory(factory);
    return factory;
  }

  public DependencyGraphExecutorFactory<ExecutionResult> getDependencyGraphExecutorFactory() {
    return _dependencyGraphExecutorFactory;
  }

  public void setDependencyGraphExecutorFactory(DependencyGraphExecutorFactory<ExecutionResult> dependencyGraphExecutorFactory) {
    _dependencyGraphExecutorFactory = dependencyGraphExecutorFactory;
  }
  
  private DependencyGraphExecutorFactory<ExecutionResult> generateDependencyGraphExecutorFactory() {
    DependencyGraphExecutorFactory<ExecutionResult> dgef = new SingleNodeExecutorFactory();
    setDependencyGraphExecutorFactory(dgef);
    return dgef;
  }
  
  public SecuritySource getSecuritySource() {
    return _securitySource;
  }
  
  public void setSecuritySource(SecuritySource securitySource) {
    _securitySource = securitySource;
  }
  
  private SecuritySource generateSecuritySource() {
    SecuritySource securitySource = new InMemorySecuritySource();
    setSecuritySource(securitySource);
    return securitySource;
  }
  
  public PositionSource getPositionSource() {
    return _positionSource;
  }
  
  public void setPositionSource(PositionSource positionSource) {
    _positionSource = positionSource;
  }
  
  public FunctionExecutionContext getFunctionExecutionContext() {
    return _functionExecutionContext;
  }
  
  public void setFunctionExecutionContext(FunctionExecutionContext functionExecutionContext) {
    _functionExecutionContext = functionExecutionContext;
  }
  
  private FunctionExecutionContext generateFunctionExecutionContext() {
    FunctionExecutionContext fec = new FunctionExecutionContext();
    setFunctionExecutionContext(fec);
    return fec;
  }
  
  public FunctionCompilationContext getFunctionCompilationContext() {
    return _functionCompilationContext;
  }
  
  public void setFunctionCompilationContext(FunctionCompilationContext functionCompilationContext) {
    _functionCompilationContext = functionCompilationContext;
  }
  
  private FunctionCompilationContext generateFunctionCompilationContext() {
    FunctionCompilationContext functionCompilationContext = new FunctionCompilationContext();
    functionCompilationContext.setSecuritySource(getSecuritySource());
    functionCompilationContext.setRawComputationTargetResolver(new DefaultComputationTargetResolver(getSecuritySource(), getPositionSource()));
    setFunctionCompilationContext(functionCompilationContext);
    return functionCompilationContext;
  }

  // Environment accessors
  // -------------------------------------------------------------------------
  public ViewProcessorImpl getViewProcessor() {
    return _viewProcessor;
  }
  
  public FunctionResolver getFunctionResolver() {
    return _functionResolver;
  }
  
  private FunctionResolver generateFunctionResolver(final CompiledFunctionService compiledFunctions) {
    _functionResolver = new DefaultFunctionResolver(compiledFunctions);
    return _functionResolver;
  }
  
  public ViewProcessImpl getViewProcess(ViewProcessorImpl viewProcessor, UniqueId viewClientId) {
    return viewProcessor.getViewProcessForClient(viewClientId);
  }
  
  public ViewComputationJob getCurrentComputationJob(ViewProcessImpl viewProcess) {
    return viewProcess.getComputationJob();
  }

  public Thread getCurrentComputationThread(ViewProcessImpl viewProcess) {
    return viewProcess.getComputationThread();
  }

  public static ComputationTargetSpecification getPrimitiveTarget() {
    return s_primitiveTarget;
  }

  public static ValueRequirement getPrimitive1() {
    return s_primitive1;
  }

  public static ValueRequirement getPrimitive2() {
    return s_primitive2;
  }

  public ViewCalculationResultModel getCalculationResult(ViewResultModel result) {
    return result.getCalculationResult(TEST_CALC_CONFIG_NAME);
  }
  
}
